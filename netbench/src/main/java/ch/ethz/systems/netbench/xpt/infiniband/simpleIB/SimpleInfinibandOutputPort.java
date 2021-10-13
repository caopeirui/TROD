package ch.ethz.systems.netbench.xpt.infiniband.simpleIB;

import ch.ethz.systems.netbench.xpt.infiniband.InfinibandPacket;
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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.List;
import java.util.Map; 
import java.util.HashMap; 
import java.util.Queue; 

// Author:  

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
public class SimpleInfinibandOutputPort extends OutputPort {

    // Constants
    private final Link link;                            // Link type, defines latency and bandwidth of the medium
                                                        // that the output port uses

    // Relevant to output port queue 
    private final long bufferMaxSizeBits;               // The output port buffer size, any additional packets will be dropped
    private long credits;                               // The credits allocated from the upstream switch to the outport, in bits.

    /**
     * Constructor.
     *
     * @param ownNetworkDevice      Source network device to which this output port is attached
     * @param targetNetworkDevice   Target network device that is on the other side of the link
     * @param link                  Link that this output ports solely governs
     * @param queue                 Queue that governs how packet are stored queued in the buffer
     */
    public SimpleInfinibandOutputPort(
            NetworkDevice ownNetworkDevice, 
            NetworkDevice towardsNetworkDevice, 
            Link link, 
            long maxQueueSizeBytes) {
        super(ownNetworkDevice, towardsNetworkDevice, link, new LinkedBlockingQueue<Packet>());

        // References to the link.
        this.link = link;

        // For managing the queue of this port
        this.bufferMaxSizeBits = maxQueueSizeBytes * 8L;
        this.credits = 0L;      // Initialize the credits to zero.
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
        assert(bufferOccupiedBits + packet.getSizeBit() <= bufferMaxSizeBits);
        // If it is not sending, then the queue is empty at the moment,
        // so if there is enough credit, then just send.
        if (!isSending && this.credits >= packet.getSizeBit()) {
            // Link is now being utilized
            logger.logLinkUtilized(true);
            // Decrement the credit before sending.
            this.credits -= packet.getSizeBit();
            // It is now sending again
            isSending = true;

            // Add event when sending is finished
            ((InfinibandPacket) packet).setCurrentHopSwitchId(getOwnId());
            Simulator.registerEvent(new PacketDispatchedEvent(
                    packet.getSizeBit() / link.getBandwidthBitPerNs(),
                    packet,
                    this
            ));
            

        } else { 
            // If it is still sending, the packet is added to the queue, making it non-empty
            bufferOccupiedBits += packet.getSizeBit();
            assert(bufferOccupiedBits <= bufferMaxSizeBits);
            queue.add(packet);
            logger.logQueueState(queue.size(), bufferOccupiedBits);
        }
    }

    /**
     * Called by the upstream switch (i.e. target switch) to increment the credit in this port.
     * This reduces the backpressure. 
     */
    public void incrementCredit(long additionalCredits) {
        this.credits += additionalCredits;
        // Next, check whether if the port is currently sending, and if so see whether we have a packet to send
        // or if there is sufficient credits to send.
        if (!isSending && !this.queue.isEmpty()) {
            // Check whether if there are any packets in the output queue to send.
            // Check whether if there is enough credit.
            InfinibandPacket headPacket = (InfinibandPacket) this.queue.peek();
            if (this.credits >= headPacket.getSizeBit()) {
                // Can send, decrement the credit first.
                this.credits -= headPacket.getSizeBit();
                // Since we had just popped packet from output queue, then just decrement the occupied buffer space.
                decreaseBufferOccupiedBits(headPacket.getSizeBit());
                // Pop the output queue
                this.queue.poll();
                headPacket.setCurrentHopSwitchId(getOwnId());
                // Create a packet dispatched event. Register when the packet is actually dispatched
                Simulator.registerEvent(new PacketDispatchedEvent(
                    headPacket.getSizeBit() / this.link.getBandwidthBitPerNs(),
                    headPacket,
                    this
                    )
                );

                // Mark the port as sending.
                isSending = true;
                logger.logQueueState(this.getQueueSize(), bufferOccupiedBits);
                // Link is now being utilized
                logger.logLinkUtilized(true);
                // Call to the owning switch to pop the head of input queues
                SimpleInfinibandSwitch owningDevice = (SimpleInfinibandSwitch) this.getOwnDevice();
                //owningDevice.popInputQueue(this.getTargetId());
            }
        }
    }
    

    // Returns the maximum buffer size in bits of the output port's queue.
    public long getMaxBufferSizeBits() {
        return this.bufferMaxSizeBits;
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
        // Finished sending packet, the last bit of the packet should arrive 
        // the link-delay later.
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

        SimpleInfinibandSwitch owningDevice = (SimpleInfinibandSwitch) this.getOwnDevice();
        owningDevice.popInputQueue(this.getTargetId());

        // Check if there are more in the queue to send.
        if (!this.queue.isEmpty()) {
            // Check whether there are sufficient credits used to send the next packet.
            InfinibandPacket headPacket = (InfinibandPacket) this.queue.peek();
            if (this.credits >= headPacket.getSizeBit()) {
                headPacket.setCurrentHopSwitchId(getOwnId());
                // Pop from queue.
                this.queue.poll();
                decreaseBufferOccupiedBits(headPacket.getSizeBit());
                // Can send, decrement the credit first.
                this.credits -= headPacket.getSizeBit();
                // The port is sending again
                isSending = true;
                // Register when the packet is actually dispatched
                Simulator.registerEvent(new PacketDispatchedEvent(
                        headPacket.getSizeBit() / this.link.getBandwidthBitPerNs(),
                        headPacket,
                        this
                    )
                );
                logger.logQueueState(this.getQueueSize(), bufferOccupiedBits);
            } else {
                logger.logLinkUtilized(false);
            }
        } else {
            // If the queue is empty, nothing will be sent for now.
            logger.logLinkUtilized(false);
        }
        // Call to the owning switch to pop the head of input queues
        // SimpleInfinibandSwitch owningDevice = (SimpleInfinibandSwitch) this.getOwnDevice();
        // owningDevice.popInputQueue(this.getTargetId());
    }



    /**
     * Change the amount of bits occupied in the buffer with a delta.
     *
     * NOTE: adapting the buffer occupancy counter from your own implementation
     *       will most likely result in strange values in the port queue state log.
     *
     * @param deltaAmount    Amount of bits to from the current occupied counter
     */
    protected void decreaseBufferOccupiedBits(long deltaAmount) {
        bufferOccupiedBits -= deltaAmount;
        assert(bufferOccupiedBits >= 0);
    }


    @Override
    public String toString() {
        return  "SimpleInfinibandOutputPort<" +
                    this.getOwnId() + " -> " +
                    " link: " + link +
                    ", occupied: " + bufferOccupiedBits +
                    ", queue size: " + this.getQueueSize() +
                ">";
    }
}
