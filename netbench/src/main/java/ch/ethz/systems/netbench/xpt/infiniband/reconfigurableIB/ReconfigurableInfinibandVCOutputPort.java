package ch.ethz.systems.netbench.xpt.infiniband.reconfigurableIB;

import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.log.PortLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.Link;
import ch.ethz.systems.netbench.core.network.Packet;
import ch.ethz.systems.netbench.core.network.PacketArrivalEvent;
import ch.ethz.systems.netbench.core.network.PacketDispatchedEvent;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitch;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_outport.ReconfigurableOutputPortInterface;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_link.ReconfigurableLink;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfiguration_planner.SignalPortReconfigurationCompletedEvent;
import ch.ethz.systems.netbench.xpt.infiniband.InfinibandPacket;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.List;

// author:  

/**
 * An output port on a network device that is reconfigurable (i.e. multiplicity with target devices can change with time). 
 * This means that the network device on the opposite end of the outport port is subject to change
 * at different times. Note that this output port implements infiniband backpressure based flow control.
 *
 */
public class ReconfigurableInfinibandVCOutputPort extends OutputPort implements ReconfigurableOutputPortInterface {

    // The state of the port.
    private enum PortState {
        NORMAL,         // Under normal execution mode, not under or waiting for reconfiguration.
        RECONFIGURING,  // In the middle of reconfiguration.
        WAITING         // Waiting for dispatched packet until the reconfiguration can be triggered.
    }

    // Internal state
    private PortState portState;        // The port state

    // Constants
    private final long reconfigLatencyNs;                       // Reconfiguration latency of a link in Ns
    private final ReconfigurableLink link;                      // Link type, defines latency and bandwidth of the medium
                                                                // that the output port uses

    // Relevant to output port queue 
    private final long unitMaxQueueSizeBits;                    // The output port buffer size, any additional packets will be dropped
    private final long unitPerChannelMaxQueueSizeBits;          // The max buffer size of each virtual channel, such that it is = unitMaxQueueSizeBits / numVCs
    private long currentPortMultiplicity;                       // The current port is a combination of how many ports exactly.
    private long effectiveMaxQueueSizeBits;                     // The effective max queue size, which is currentPortMultiplicity * unitMaxQueueSizeBits
    private long effectivePerChannelMaxQueueSizeBits;           // The effective max queue size, which is currentPortMultiplicity * unitMaxQueueSizeBits / numVCs

    // For reconfiguration
    private long duringReconfigurationMultiplicity;             // during reconfiguration, what is the link multiplicity
    private long afterReconfigurationMultiplicity;              // after reconfiguration, what is the link multiplicity

    // Forward credits in bits
    private final int numVCs;
    private final long[] credits;
    private final long[] channelBufferOccupiedBits;
    private final Queue<Packet>[] channelQueue;

    // Logging utility
    private final PortLogger logger;

    /**
     * Constructor.
     *
     * @param ownNetworkDevice      Source network device to which this output port is attached
     * @param targetNetworkDevice   Target network device that is on the other side of the link
     * @param link                  Link that this output ports solely governs
     * @param queue                 Queue that governs how packet are stored queued in the buffer
     */
    protected ReconfigurableInfinibandVCOutputPort(
            NetworkDevice ownNetworkDevice, 
            NetworkDevice towardsNetworkDevice, 
            Link link, 
            long maxQueueSizeBytes, 
            long reconfigLatencyNs, 
            long multiplicity,
            int numVCs) {
        super(ownNetworkDevice, towardsNetworkDevice, link, new LinkedBlockingQueue<Packet>());
        assert(numVCs > 1);
        this.numVCs = numVCs;
        this.credits = new long[numVCs];
        this.channelBufferOccupiedBits = new long[numVCs];
        this.channelQueue = new LinkedBlockingQueue[numVCs];
        for (int channel = 0; channel < numVCs; channel++) {
            this.credits[channel] = 0L;
            this.channelBufferOccupiedBits[channel] = 0L;
            this.channelQueue[channel] = new LinkedBlockingQueue<>();
        }

        // Internal State
        this.portState = PortState.NORMAL;

        // References
        this.reconfigLatencyNs = reconfigLatencyNs;
        this.link = (ReconfigurableLink) link;

        // For managing the queue of this port
        this.unitMaxQueueSizeBits = maxQueueSizeBytes * 8L;
        this.unitPerChannelMaxQueueSizeBits = this.unitMaxQueueSizeBits / numVCs;
        this.effectiveMaxQueueSizeBits = this.unitMaxQueueSizeBits * multiplicity;
        this.effectivePerChannelMaxQueueSizeBits = this.unitPerChannelMaxQueueSizeBits * multiplicity;
        this.currentPortMultiplicity = multiplicity;
        if (multiplicity <= 0) {
            throw new IllegalStateException("Initialized reconfigurable output port with 0 multiplicity.");
        }

        // Logging
        this.logger = new PortLogger(this);
    }

    /**
     * Enqueue the given packet for sending. Unlike other output ports, an Infiniband
     * output port must ensure that the packet is enqueued, so if this method is called
     * even when there is clearly not enough buffer space, then throw an error.
     *
     * @param packet    Packet instance
     */
    @Override
    public void enqueue(Packet packet) {
        InfinibandPacket ibPacket = (InfinibandPacket) packet;
        int vc = ibPacket.getVC();
        assert(vc >= 0 && vc < numVCs);
        assert(getRemainingVCBufferBits(vc) >= packet.getSizeBit());
        // If it is not sending, then the queue is empty at the moment,
        // so if there is enough credit and that the current port multiplicity is not zero, then just send.
        this.channelQueue[vc].add(packet);
        this.channelBufferOccupiedBits[vc] += packet.getSizeBit();
        assert(channelBufferOccupiedBits[vc] <= effectivePerChannelMaxQueueSizeBits);
        if (!isSending && currentPortMultiplicity > 0) {
            // Phsyical Channel is idling, could try sending if there is enough credit to send the next packet.
            InfinibandPacket nextPacketToSend = (InfinibandPacket) this.channelQueue[vc].peek();
            if (this.credits[vc] >= nextPacketToSend.getSizeBit()) {
                // Pop from queue and decrement queue buffer occupancy.
                this.channelQueue[vc].poll();
                decreaseBufferOccupiedBits(nextPacketToSend.getSizeBit(), vc);
                // Send packet
                sendPacket(nextPacketToSend);
            }
        } else { 
            logger.logQueueState(queue.size(), bufferOccupiedBits);
        }
    }

    /**
     * Sets up the packet dispatched event and actually sends the packet into the link.
     * This method does not perform any error checking, so make sure that when is it called,
     * the port is not currently sending and that the multiplicity is actually greater than zero.
     * Also, this method will not pop packet from queue. It is assumed that packet is already popped
     * from queue by the caller method.
     * 
     * NOTE: This method WILL decrement the credit, so caller should not decrement credit. 
     *
     * @param packet                            The infiniband packet we want to send
     */
    private void sendPacket(InfinibandPacket packet) {
        assert(!isSending); // assert that the port cannot be sending when this method is called.
        int currentVC = packet.getVC();
        packet.setCurrentHopSwitchId(getOwnId());       // Stamp the packet with the current id.
        this.credits[currentVC] -= packet.getSizeBit();
        assert(this.credits[currentVC] >= 0L);
        // Register when the packet is actually dispatched
        Simulator.registerEvent(new PacketDispatchedEvent(
                packet.getSizeBit() / link.getBandwidthBitPerNs(),
                packet,
                this
            )
        );
        isSending = true;
        // If the queue is empty, or if there i
        logger.logLinkUtilized(true);
    }

    /**
     * Called when a packet has actually been pushed completely into the link.
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
        } else {
            assert(false);
            // Immediately resend packet
            Simulator.registerEvent(new PacketDispatchedEvent(
                    packet.getSizeBit() / this.link.getBandwidthBitPerNs(),
                    packet,
                    this
                )
            );
            return;
        }

        // Next, arbitration for the next virtual channel queue to pop from.
        int currentVC = ((InfinibandPacket) packet).getVC();

        // Trigger the switch to send a packet from the input queues
        long leftoverBufferSizeBits = getRemainingVCBufferBits(currentVC);
        assert(leftoverBufferSizeBits >= 0 && leftoverBufferSizeBits <= effectivePerChannelMaxQueueSizeBits);
        ((ReconfigurableInfinibandVCSwitch) this.getOwnDevice()).popInputQueue(this.getTargetId(), leftoverBufferSizeBits, currentVC);

        // Set the output port's isSending signal to false.
        isSending = false;
        // Check whether if we are waiting on a reconfiguration, and whether if we can trigger reconfiguration
        if (this.portState == PortState.WAITING &&
                bufferOccupiedBits <= duringReconfigurationMultiplicity * this.unitMaxQueueSizeBits) {
            boolean allVirtualQueuesBelowThreshold = true;
            // Go through the entire number of virtual channels and check if all queues 
            long thresholdBits = duringReconfigurationMultiplicity * unitPerChannelMaxQueueSizeBits;
            for (int channel = 0; channel < numVCs; channel++) {
                if (this.channelBufferOccupiedBits[channel] > thresholdBits) {
                    allVirtualQueuesBelowThreshold = false;
                    break;
                }
            }
            // If all virtual queues have fewer bits occupied than the threshold, then the port has been sufficiently drained, and reconfiguration can begin.
            if (allVirtualQueuesBelowThreshold) {
                this.portState = PortState.RECONFIGURING;
                // Update port multiplicity 
                updatePortMultiplicity(duringReconfigurationMultiplicity);
                // Can trigger reconfiguration now.
                Simulator.registerEvent(new SignalPortReconfigurationCompletedEvent(reconfigLatencyNs, this));
            }
        }

        

        int nextSendingVC = -1;
        for (int offset = 1; offset <= numVCs; offset++) {
            int c = (currentVC + offset) % numVCs;
            // If there are sufficient credits to send in this channel, then send it.
            if (!this.channelQueue[c].isEmpty() && this.channelQueue[c].peek().getSizeBit() <= this.credits[c]) {
                nextSendingVC = c;
                break;
            }
        }

        // See whether if there are more packets in the output queue. If so pops the head packet from the queue
        // as long as there is enough credits to send.
        if (nextSendingVC >= 0 && this.currentPortMultiplicity > 0) {
            // Pop from queue and decrement the buffer occupancy.
            InfinibandPacket headPacket = (InfinibandPacket) this.channelQueue[nextSendingVC].poll();
            decreaseBufferOccupiedBits(headPacket.getSizeBit(), headPacket.getVC());
            logger.logQueueState(this.getQueueSize(), bufferOccupiedBits);
            // Send the packet into the output port.
            sendPacket(headPacket);
        } else {
            //System.out.println(toString());
            // If the queue is empty, or if there i
            logger.logLinkUtilized(false);
        }
    }

    /**
     * Called by the network device owning this port when this particular output port is to be reconfigured
     * reconfiguration start. 
     *
     * @param afterReconfigurationMultiplicity               The new multiplicity of this output port
     */
    @Override
    public void triggerPortReconfiguration(long afterReconfigurationMultiplicityArg) {
        // This port cannot be triggered for reconfiguration if it should be treated as a static port
        assert((this.portState != PortState.RECONFIGURING) && (this.portState != PortState.WAITING)); 
        // Return if there is no change between the after reconfig multiplicity and current setMultiplicity
        if (currentPortMultiplicity == afterReconfigurationMultiplicityArg) return; 
        // First set the during and after reconfiguration multiplicities
        this.duringReconfigurationMultiplicity = Math.min(afterReconfigurationMultiplicityArg, currentPortMultiplicity);
        this.afterReconfigurationMultiplicity = afterReconfigurationMultiplicityArg;

        // Then put the port in waiting mode for reconfiguration.
        this.portState = PortState.WAITING;
        // Can commence the reconfiguration if port is idling, otherwise do not commence port reconfiguration.
        boolean allVirtualQueuesBelowThreshold = true;
        for (int channel = 0; channel < numVCs; channel++) {
            if (channelBufferOccupiedBits[channel] > duringReconfigurationMultiplicity * unitPerChannelMaxQueueSizeBits) {
                allVirtualQueuesBelowThreshold = false;
                break;
            }
        }

        // Can start reconfiguring right now 
        if (!isSending && allVirtualQueuesBelowThreshold) {
            // The link must either disconnected (port multiplicity = 0) or has empty queue if its idling
            // assert(queue.isEmpty() || currentPortMultiplicity == 0); 
            this.portState = PortState.RECONFIGURING;
            updatePortMultiplicity(duringReconfigurationMultiplicity);
            // Register the ending event.
            Simulator.registerEvent(new SignalPortReconfigurationCompletedEvent(reconfigLatencyNs, this));
        }
    }

    /**
     * Signals the network switch which owns this port that the reconfiguration has been completed.
     */
    @Override
    public void signalReconfigurationEnded() {
        // Tells the network device that owns this output port to undrain this port.
        updatePortMultiplicity(this.afterReconfigurationMultiplicity);
        this.afterReconfigurationMultiplicity = -1;
        this.duringReconfigurationMultiplicity = -1;
        // Reset the port state to normal operation.
        this.portState = PortState.NORMAL;
        ReconfigurableInfinibandVCSwitch ownReconfigurableNetworkSwitch = (ReconfigurableInfinibandVCSwitch) this.getOwnDevice();
        // Signal the owning switch to make its relevant updates as this port has been successfully reconfigured.
        ownReconfigurableNetworkSwitch.signalPortReconfigurationEnded(this.getTargetDevice());
        if (!isSending) {
            System.out.println(toString());
        }
    }

    /**
     * Updates the port multiplicity.
     * 
     * @param newLinkMultiplicity               The new multiplicity for this port.
     **/ 
    private void updatePortMultiplicity(long newLinkMultiplicity) {
        // Make updates to the internal states by changing 1) port multiplicity, and 2) current maximum buffer occupancy in bits
        currentPortMultiplicity = newLinkMultiplicity;
        effectiveMaxQueueSizeBits = currentPortMultiplicity * unitMaxQueueSizeBits;
        effectivePerChannelMaxQueueSizeBits = currentPortMultiplicity * unitPerChannelMaxQueueSizeBits;
        // Ensure that every time we update port multiplicity, the maximum space cannot exceed current buffer occupancy.
        for (int channel = 0; channel < numVCs; channel++) {
            if (this.channelBufferOccupiedBits[channel] > effectivePerChannelMaxQueueSizeBits) {
                throw new IllegalStateException("Port multiplicity is updated even before proper draining");
            }
        }
        // Set the multiplicity for the connected link.
        this.link.setMultiplicity(this.currentPortMultiplicity);
    }

    /**
     * Returns the available buffer space in the output port's queue in terms of bits.
     *
     * @return The remaining buffer size available to store packets in the output port queue.
     */    
    public long getRemainingVCBufferBits(int channel) {
        // If the port is waiting to be reconfigured, then we need to return space based on what the multiplicity is DURING reconfiguration
        long leftOverBufferSizeBits = -channelBufferOccupiedBits[channel];
        if (portState == PortState.WAITING) {
            leftOverBufferSizeBits += (unitPerChannelMaxQueueSizeBits * duringReconfigurationMultiplicity);
        } else {
            leftOverBufferSizeBits += effectivePerChannelMaxQueueSizeBits;
        }
        return Math.max(leftOverBufferSizeBits, 0L);
    }

    /**
     * Called by the upstream switch to increment the credit of this output port by additionalCredits
     * amount. Note the the credits are in bits, not bytes.
     */
    public void incrementCredit(long additionalCredits, int channel) {
        assert(channel >= 0 && channel < numVCs);
        this.credits[channel] += additionalCredits;
        // See whether if we are stuck from sending. If there are packets in the output port, then pop
        // and send.
        if (!isSending && 
                this.currentPortMultiplicity > 0 &&
                !this.channelQueue[channel].isEmpty() && 
                this.channelQueue[channel].peek().getSizeBit() <= this.credits[channel]) {
            // Pop the output queue
            InfinibandPacket headPacket = (InfinibandPacket) this.channelQueue[channel].poll();
            // Since we had just popped packet from output queue, then just decrement the occupied buffer space.
            decreaseBufferOccupiedBits(headPacket.getSizeBit(), channel);
            
            sendPacket(headPacket);
            logger.logQueueState(this.getQueueSize(), bufferOccupiedBits);
        }
    } 

    /**
     * Change the amount of bits occupied in the buffer with a delta for a given virtual channel.
     *
     * NOTE: adapting the buffer occupancy counter from your own implementation
     *       will most likely result in strange values in the port queue state log.
     *
     * @param deltaAmount    Amount of bits to from the current occupied counter
     */
    protected void decreaseBufferOccupiedBits(long deltaAmount, int channel) {
        assert(channel >= 0 && channel < numVCs);
        this.channelBufferOccupiedBits[channel] -= deltaAmount;
        assert(this.channelBufferOccupiedBits[channel] >= 0);
    }

    @Override
    public String toString() {
        String str =  "ReconfigurableInfinibandVCOutputPort<" +
                    getOwnId() + " -> " + getTargetId() + ", "
                    + " multiplicity: " + currentPortMultiplicity + ".\n";
        str += "current time: " + Simulator.getCurrentTime() + " ";
        for (int vc = 0; vc < numVCs; vc++) {
            str += ("vc " + vc + " - occupied: " + channelBufferOccupiedBits[vc] + " queue size: " + channelQueue[vc].size() + ". ");
        }
        return str;
    }
}
