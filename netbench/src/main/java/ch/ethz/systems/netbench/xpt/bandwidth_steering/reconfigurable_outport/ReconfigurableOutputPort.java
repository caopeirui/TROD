package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_outport;

import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.log.PortLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.Link;
import ch.ethz.systems.netbench.core.network.Packet;
import ch.ethz.systems.netbench.core.network.PacketArrivalEvent;
import ch.ethz.systems.netbench.core.network.PacketDispatchedEvent;
import ch.ethz.systems.netbench.ext.basic.IpPacket;
import ch.ethz.systems.netbench.ext.basic.IpHeader;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitch;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_link.ReconfigurableLink;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfiguration_planner.SignalPortReconfigurationCompletedEvent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.List;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import ch.ethz.systems.netbench.ext.basic.TcpPacket;
import ch.ethz.systems.netbench.xpt.tcpbase.PriorityHeader;


// author:  

/**
 * Abstraction for an output port on a network device that is reconfigurable. This
 * means that the network device on the opposite end of the outport port is subject to change
 * at different times.
 *
 * There is no corresponding InputPort class, as the output
 * port already forces a rate limit. OutputPort's subclasses
 * are forced to handle the enqueuing of packets, and are allowed
 * to drop packets depending on their own drop strategy to handle
 * congestion at the port (e.g. tail-drop, RED, ...).
 */
public class ReconfigurableOutputPort extends OutputPort implements ReconfigurableOutputPortInterface {

    // the state of the port
    private enum PortState {
        NORMAL, // under normal execution mode
        RECONFIGURING, // in the middle of reconfiguration
        WAITING // waiting for dispatched packet until the reconfiguration can be triggered.
    }

    // Internal state
    private PortState portState;        // The port state

    // Constants
    private final int ownDeviceId;                              // Own network device identifier
    private final int targetDeviceId;                           // Id of the device that the output port is connected to
    private final long reconfigLatencyNs;                       // Reconfiguration latency of a link in Ns
    private final ReconfigurableLink link;                      // Link type, defines latency and bandwidth of the medium
                                                                // that the output port uses

    // Relevant to output port queue 
    private final long unitMaxQueueSizeBits;                    // The output port buffer size, any additional packets will be dropped
    private final long unitEcnThresholdKBits;                   // The unit mark for the buffer to tell if congestion has happened
    private long currentPortMultiplicity;                       // The current port is a combination of how many ports exactly.
    private long effectiveMaxQueueSizeBits;                     // The effective max queue size, which is currentPortMultiplicity * unitMaxQueueSizeBits
    private long effectiveEcnThresholdKBits;                    // The effective ecn threshold, which is currentPortMultiplicity * unitEcnThresholdKBits
    private final boolean isStatic;                             // Marks whether this port should be treated as a static port or not.

    // For reconfiguration
    private long duringReconfigurationMultiplicity;         // during reconfiguration, what is the link multiplicity
    private long afterReconfigurationMultiplicity;

    // Logging utility
    private final PortLogger logger;

    private static final int INITIAL_QUEUE_CAPACITY = 100;   //   add

    /**
     * Constructor.
     *
     * @param ownNetworkDevice      Source network device to which this output port is attached
     * @param targetNetworkDevice   Target network device that is on the other side of the link
     * @param link                  Link that this output ports solely governs
     * @param queue                 Queue that governs how packet are stored queued in the buffer
     */
    protected ReconfigurableOutputPort(
            NetworkDevice ownNetworkDevice, 
            NetworkDevice towardsNetworkDevice, 
            ReconfigurableLink link, 
            long maxQueueSizeBytes, 
            long ecnThresholdKBytes, 
            long reconfigLatencyNs, 
            long multiplicity, 
            boolean isStaticArg) {
        super(ownNetworkDevice, towardsNetworkDevice, link, new LinkedBlockingQueue<Packet>());
        
        // super(ownNetworkDevice, towardsNetworkDevice, link, new PriorityBlockingQueue<>(INITIAL_QUEUE_CAPACITY, new Comparator<Packet>() {
        //     @Override
        //     public int compare(Packet o1, Packet o2) {
        //         if(o1 instanceof TcpPacket && o2 instanceof TcpPacket){

        //             // Cast
        //             PriorityHeader tcp1 = (PriorityHeader) o1;
        //             PriorityHeader tcp2 = (PriorityHeader) o2;

        //             // First compare based on priority
        //             int res = Long.compare(tcp1.getPriority(), tcp2.getPriority());

        //             // If packets have same priorities, compare based on departure time
        //             if (res == 0 ){
        //                 res = Long.compare(tcp1.getDepartureTime(), tcp2.getDepartureTime());
        //             }

        //             return res;
        //         }
        //         return 0;
        //     }
        // }));

        // Internal State
        this.isSending = false;
        this.bufferOccupiedBits = 0;
        this.portState = PortState.NORMAL;

        // References
        this.ownDeviceId = this.getOwnDevice().getIdentifier();
        this.targetDeviceId = this.getTargetDevice().getIdentifier();
        this.reconfigLatencyNs = reconfigLatencyNs;
        this.link = link;

        // For managing the queue of this port
        this.unitMaxQueueSizeBits = maxQueueSizeBytes * 8L;
        this.unitEcnThresholdKBits = ecnThresholdKBytes * 8L;
        //   modify
        long pod_egress = Simulator.getConfiguration().getLongPropertyOrFail("pod_egress");
        this.effectiveMaxQueueSizeBits = unitMaxQueueSizeBits * pod_egress;
        this.effectiveEcnThresholdKBits = unitEcnThresholdKBits * pod_egress;
        // this.effectiveMaxQueueSizeBits = unitMaxQueueSizeBits * multiplicity;
        // this.effectiveEcnThresholdKBits = unitEcnThresholdKBits * multiplicity;
        this.isStatic = isStaticArg;
        this.currentPortMultiplicity = multiplicity;
        if (multiplicity <= 0) {
            throw new IllegalStateException("Initialized reconfigurable output port with 0 multiplicity.");
        }
        // Logging
        this.logger = new PortLogger(this);
    }

    /**
     * Enqueue the given packet for sending.
     * There is no guarantee that the packet is actually sent,
     * as the queue buffer's limit might be reached.
     *
     * @param packet    Packet instance
     */
    @Override
    public void enqueue(Packet packet) {
        // Convert to IP packet
        IpHeader ipHeader = (IpHeader) packet;

        // Mark congestion flag if size of the queue is too big
        if (this.getBufferOccupiedBits() >= effectiveEcnThresholdKBits) {
            ipHeader.markCongestionEncountered();
        }

        // Next, if the port is waiting for reconfiguration (draining), then we wanna drop packet if it exceeds our port buffer
        // during reconfiguration.
        long packetDropThreshold = effectiveMaxQueueSizeBits;
        //   annotate
        // if (this.portState == PortState.WAITING) {
        //     packetDropThreshold = this.duringReconfigurationMultiplicity * unitMaxQueueSizeBits;
        // }

        // Tail-drop enqueue
        if (getBufferOccupiedBits() + ipHeader.getSizeBit() <= packetDropThreshold) {
            guaranteedEnqueue(packet);
        } else {
            //  : print
            System.out.printf("  drop packetDropThreshold: %d\n", packetDropThreshold);
            SimulationLogger.increaseStatisticCounter("PACKETS_DROPPED");
            if (ipHeader.getSourceId() == this.getOwnId()) {
                SimulationLogger.increaseStatisticCounter("PACKETS_DROPPED_AT_SOURCE");
            }
        }
    }

    /**
     * Called when a packet has actually been sent completely.
     * In response, register arrival event at the destination network device,
     * and starts sending another packet if it is available.
     *
     * @param packet    Packet instance that was being sent
     */
    @Override
    public void dispatch(Packet packet) {
        // Finished sending packet, the last bit of the packet should arrive the link-delay later
        if (!link.doesNextTransmissionFail(packet.getSizeBit())) {
            Simulator.registerEvent(
                    new PacketArrivalEvent(
                            link.getDelayNs(),
                            packet,
                            this.getTargetDevice()
                    )
            );
        }

        // Again free to send other packets
        isSending = false;

        // Check port state, if it's waiting for reconfiguration, then check whether if we can trigger reconfiguration
        // once the port has been sufficiently drained.

        //   modify
        // if (this.portState == PortState.WAITING && 
        //         this.duringReconfigurationMultiplicity * unitMaxQueueSizeBits >= bufferOccupiedBits) {
        if (this.portState == PortState.WAITING) {
            this.portState = PortState.RECONFIGURING;
            this.currentPortMultiplicity = this.duringReconfigurationMultiplicity;
            updatePortMultiplicity();
            Simulator.registerEvent(new SignalPortReconfigurationCompletedEvent(reconfigLatencyNs, this));
        }

        if (this.link.getBandwidthBitPerNs() > 0) {  //   add
            // Check if there are more in the queue to send
            if (!this.queue.isEmpty()) {
                // Pop from queue
                Packet packetFromQueue = this.queue.poll();
                decreaseBufferOccupiedBits(packetFromQueue.getSizeBit());
                logger.logQueueState(this.getQueueSize(), bufferOccupiedBits);

                // Register when the packet is actually dispatched
                Simulator.registerEvent(new PacketDispatchedEvent(
                        packetFromQueue.getSizeBit() / this.link.getBandwidthBitPerNs(),
                        packetFromQueue,
                        this
                ));

                // It is sending again
                isSending = true;

            } else {
                // Check if there are any reconfiguration events, if so, then trigger it to start.
                // If the queue is empty, nothing will be sent for now.
                logger.logLinkUtilized(false);
            }

        }

    }

    /**
     * Called by the network device owning this port when this particular output port is to be reconfigured
     * reconfiguration start. 
     *
     * @param duringReconfigurationMultiplicity              The multiplicity of this output port during reconfiguration
     * @param afterReconfigurationMultiplicity               The new multiplicity of this output port
     */
    @Override
    public void triggerPortReconfiguration(long afterReconfigurationMultiplicityArg) {
        // This port cannot be triggered for reconfiguration if it should be treated as a static port
        assert(!isStatic && (this.portState != PortState.RECONFIGURING) && (this.portState != PortState.WAITING)); 
        this.duringReconfigurationMultiplicity = Math.min(afterReconfigurationMultiplicityArg, currentPortMultiplicity);
        this.afterReconfigurationMultiplicity = afterReconfigurationMultiplicityArg;
        this.portState = PortState.WAITING;
        // Can commence the reconfiguration if port queue is sufficiently drained, so that we don't drop packets just by reconfiguration.
        
        //   modify
        // if (!isSending && 
        //         duringReconfigurationMultiplicity * unitMaxQueueSizeBits >= bufferOccupiedBits) {
        if (!isSending) {
            this.currentPortMultiplicity = this.duringReconfigurationMultiplicity;
            this.portState = PortState.RECONFIGURING;    
            updatePortMultiplicity();
            // register the ending event.
            Simulator.registerEvent(new SignalPortReconfigurationCompletedEvent(reconfigLatencyNs, this));
        }
    }

    /**
     * Signals the network switch which owns this port that the reconfiguration has been completed.
     */
    @Override
    public void signalReconfigurationEnded() {
        // Tells the network device that owns this output port to undrain this port.
        boolean is_port_previously_down = (this.currentPortMultiplicity == 0);
        this.currentPortMultiplicity = this.afterReconfigurationMultiplicity;
        updatePortMultiplicity();
        this.afterReconfigurationMultiplicity = -1;
        this.duringReconfigurationMultiplicity = -1;
        // reset the port state to normal.
        this.portState = PortState.NORMAL;

        //   add    
        if (is_port_previously_down && this.currentPortMultiplicity > 0) {
            // Check if there are more in the queue to send
            if (!this.queue.isEmpty()) {
                // Pop from queue
                Packet packetFromQueue = this.queue.poll();
                decreaseBufferOccupiedBits(packetFromQueue.getSizeBit());
                logger.logQueueState(this.getQueueSize(), bufferOccupiedBits);

                // Register when the packet is actually dispatched
                Simulator.registerEvent(new PacketDispatchedEvent(
                        packetFromQueue.getSizeBit() / this.link.getBandwidthBitPerNs(),
                        packetFromQueue,
                        this
                ));

                // It is sending again
                isSending = true;

            } else {
                // Check if there are any reconfiguration events, if so, then trigger it to start.
                // If the queue is empty, nothing will be sent for now.
                logger.logLinkUtilized(false);
            }
        }

        ReconfigurableNetworkSwitch reconfigurableNetworkSwitch = (ReconfigurableNetworkSwitch) this.getOwnDevice();
        reconfigurableNetworkSwitch.signalPortReconfigurationEnded((ReconfigurableNetworkSwitch) this.getTargetDevice());
        // signal the owner switch that port reconfiguration has completed.
        // ownNetworkDevice.undrainPort(ownPortId);
    }

    // Updates the port multiplicity
    private void updatePortMultiplicity() {
        //   
        // this.effectiveMaxQueueSizeBits = this.currentPortMultiplicity * this.unitMaxQueueSizeBits;
        // this.effectiveEcnThresholdKBits = this.currentPortMultiplicity * this.unitEcnThresholdKBits;
        this.link.setMultiplicity(this.currentPortMultiplicity);
    }



    

    /**
     * Return the current link multiplicity
     */
    public long getCurrentLinkMultiplicity() {
        return this.currentPortMultiplicity;
    }

    @Override
    public String toString() {
        return  "ReconfigurableOutputPort<" +
                    ownDeviceId + " -> " +
                    " link: " + link +
                    ", occupied: " + bufferOccupiedBits +
                    ", queue size: " + getQueueSize() +
                ">";
    }
}
