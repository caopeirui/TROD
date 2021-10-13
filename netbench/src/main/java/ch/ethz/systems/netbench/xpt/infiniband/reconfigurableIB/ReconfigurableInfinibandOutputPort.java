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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.List;

// author:  

/**
 * An output port on a network device that is reconfigurable (i.e. multiplicity with target devices can change with time). 
 * This means that the network device on the opposite end of the outport port is subject to change
 * at different times. Note that this output port implements infiniband backpressure based flow control.
 *
 */
public class ReconfigurableInfinibandOutputPort extends OutputPort implements ReconfigurableOutputPortInterface {

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
    private long currentPortMultiplicity;                       // The current port is a combination of how many ports exactly.
    private long effectiveMaxQueueSizeBits;                     // The effective max queue size, which is currentPortMultiplicity * unitMaxQueueSizeBits

    // For reconfiguration
    private long duringReconfigurationMultiplicity;             // during reconfiguration, what is the link multiplicity
    private long afterReconfigurationMultiplicity;              // after reconfiguration, what is the link multiplicity

    // Forward credits in bits
    private long credits;

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
    protected ReconfigurableInfinibandOutputPort(
            NetworkDevice ownNetworkDevice, 
            NetworkDevice towardsNetworkDevice, 
            Link link, 
            long maxQueueSizeBytes, 
            long reconfigLatencyNs, 
            long multiplicity) {
        super(ownNetworkDevice, towardsNetworkDevice, link, new LinkedBlockingQueue<Packet>());
        
        // Internal State
        this.portState = PortState.NORMAL;

        // Initialize the creidts
        this.credits = 0L;

        // References
        this.reconfigLatencyNs = reconfigLatencyNs;
        this.link = (ReconfigurableLink) link;

        // For managing the queue of this port
        this.unitMaxQueueSizeBits = maxQueueSizeBytes * 8L;
        this.effectiveMaxQueueSizeBits = this.unitMaxQueueSizeBits * multiplicity;
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
        assert(bufferOccupiedBits + packet.getSizeBit() <= effectiveMaxQueueSizeBits);
        // If it is not sending, then the queue is empty at the moment,
        // so if there is enough credit and that the current port multiplicity is not zero, then just send.
        if (!isSending && currentPortMultiplicity > 0) {
            long nextPacketNeededCredits = packet.getSizeBit();
            if (!queue.isEmpty()) {
                nextPacketNeededCredits = queue.peek().getSizeBit();
                if (this.credits >= nextPacketNeededCredits) {
                    Packet headPacket = queue.poll();
                    decreaseBufferOccupiedBits(nextPacketNeededCredits);
                    sendPacket((InfinibandPacket) headPacket);
                }
                // put the current packet into the queue
                queue.add(packet);
                // increment the buffer 
                bufferOccupiedBits += packet.getSizeBit();
                assert(bufferOccupiedBits <= effectiveMaxQueueSizeBits);
                logger.logQueueState(queue.size(), bufferOccupiedBits);
            } else {
                // Queue is empty, so can straight away send this packet without queueing
                if (this.credits >= nextPacketNeededCredits) {
                    sendPacket((InfinibandPacket) packet);
                } else {
                    // Put it in the queue
                    queue.add(packet);
                    bufferOccupiedBits += packet.getSizeBit();
                    logger.logQueueState(queue.size(), bufferOccupiedBits);
                }
            }
        } else { 
            // If it is still sending, the packet is added to the queue, making it non-empty.
            bufferOccupiedBits += packet.getSizeBit();
            queue.add(packet);
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
        assert(!isSending); // assert
        packet.setCurrentHopSwitchId(getOwnId());       // Stamp the packet with the current id.
        this.credits -= packet.getSizeBit();
        assert(this.credits >= 0L);
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
            // Immediately resend packet
            // Register when the packet is actually dispatched
            System.out.println("Packet was not sent successfully.");
            Simulator.registerEvent(new PacketDispatchedEvent(
                    packet.getSizeBit() / this.link.getBandwidthBitPerNs(),
                    packet,
                    this
                )
            );
            return;
        }

        // Set the output port's isSending signal to false.
        isSending = false;
        // Check whether if we are waiting on a reconfiguration, and whether if we can trigger reconfiguration
        if (this.portState == PortState.WAITING &&
                bufferOccupiedBits <= duringReconfigurationMultiplicity * this.unitMaxQueueSizeBits) {
            // Update port multiplicity 
            updatePortMultiplicity(duringReconfigurationMultiplicity);
            this.portState = PortState.RECONFIGURING;
            // Can trigger reconfiguration now.
            Simulator.registerEvent(new SignalPortReconfigurationCompletedEvent(reconfigLatencyNs, this));
        }


        // See whether if there are more packets in the output queue. If so pops the head packet from the queue
        // as long as there is enough credits to send.
        if (!this.queue.isEmpty() && 
                (this.queue.peek().getSizeBit() <= this.credits) && 
                this.currentPortMultiplicity > 0) {
            // Pop from queue and decrement the buffer occupancy.
            InfinibandPacket headPacket = (InfinibandPacket) this.queue.poll();
            decreaseBufferOccupiedBits(headPacket.getSizeBit());
            logger.logQueueState(this.getQueueSize(), bufferOccupiedBits);
            // Send the packet into the output port.
            sendPacket(headPacket);
            // Trigger the switch to send a packet from the input queues
            ((ReconfigurableInfinibandSwitch) getOwnDevice()).popInputQueue(getTargetId(), getRemainingBufferSizeBits());
            /*
            if (this.portState != PortState.WAITING) {
                long leftoverBufferSizeBits = effectiveMaxQueueSizeBits - bufferOccupiedBits;
                assert(leftoverBufferSizeBits >= 0);
                ((ReconfigurableInfinibandSwitch) this.getOwnDevice()).popInputQueue(this.getTargetId(), leftoverBufferSizeBits);
            }
            */
        } else {
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
        // Not only that, also need to ensure that the queue has been sufficiently drained
        if (!isSending && 
                this.duringReconfigurationMultiplicity * this.unitMaxQueueSizeBits >= this.bufferOccupiedBits) {
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
        ReconfigurableInfinibandSwitch ownReconfigurableNetworkSwitch = (ReconfigurableInfinibandSwitch) this.getOwnDevice();
        // Signal the owning switch to make its relevant updates as this port has been successfully reconfigured.
        ownReconfigurableNetworkSwitch.signalPortReconfigurationEnded(this.getTargetDevice());
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
        // Ensure that every time we update port multiplicity, the maximum space cannot exceed current buffer occupancy.
        assert(bufferOccupiedBits <= effectiveMaxQueueSizeBits);  
        // Set the multiplicity for the connected link.
        this.link.setMultiplicity(this.currentPortMultiplicity);
    }

    /*
     * Return the current link multiplicity
     
    public long getCurrentLinkMultiplicity() {
        return this.currentPortMultiplicity;
    }
    */

    /*
     * Returns the current buffer occupancy of this output port
     
    public long getMaxBufferSizeBits() {
        long maxBits = this.effectiveMaxQueueSizeBits;
        if (portState == PortState.WAITING) {
            return this.duringReconfigurationMultiplicity * this.unitMaxQueueSizeBits;
        }
        return maxBits;
    }
    */
    
    /**
     * Returns the available buffer space in the output port's queue in terms of bits.
     *
     * @return The remaining buffer size available to store packets in the output port queue.
     */    
    public long getRemainingBufferSizeBits() {
        // If the port is waiting to be reconfigured, then we need to return space based on what the multiplicity is DURING reconfiguration
        long leftOverBufferSizeBits = -bufferOccupiedBits;
        if (portState == PortState.WAITING) {
            leftOverBufferSizeBits += (unitMaxQueueSizeBits * duringReconfigurationMultiplicity);
        } else {
            leftOverBufferSizeBits += effectiveMaxQueueSizeBits;
        }
        return Math.max(leftOverBufferSizeBits, 0L);
    }

    /**
     * Called by the upstream switch to increment the credit of this output port by additionalCredits
     * amount. Note the the credits are in bits, not bytes.
     */
    public void incrementCredit(long additionalCredits) {
        this.credits += additionalCredits;
        // See whether if we are stuck from sending. If there are packets in the output port, then pop
        // and send.
        if (!isSending && !this.queue.isEmpty() && 
                this.queue.peek().getSizeBit() <= this.credits && 
                this.currentPortMultiplicity > 0) {
            // Pop the output queue
            InfinibandPacket headPacket = (InfinibandPacket) this.queue.poll();
            // Since we had just popped packet from output queue, then just decrement the occupied buffer space.
            decreaseBufferOccupiedBits(headPacket.getSizeBit());
            logger.logQueueState(this.getQueueSize(), bufferOccupiedBits);
            sendPacket(headPacket);
        }
    } 

    @Override
    public String toString() {
        return  "ReconfigurableInfinibandOutputPort<" +
                    getOwnId() + " -> " +
                    " link: " + link +
                    ", occupied: " + bufferOccupiedBits +
                    ", queue size: " + getQueueSize() +
                ">";
    }
}
