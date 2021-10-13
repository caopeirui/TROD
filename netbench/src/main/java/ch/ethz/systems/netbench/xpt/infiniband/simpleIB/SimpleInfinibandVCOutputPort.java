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
 *
 * NOTE: This class differs from SimpleInfinibandOutputPort in that this class supports more than 1 VC
 */
public class SimpleInfinibandVCOutputPort extends OutputPort {

    // Constants
    private final Link link;                            // Link type, defines latency and bandwidth of the medium
                                                        // that the output port uses

    // Relevant to output port queue 
    private final long bufferMaxSizeBits;               // The output port buffer size, any additional packets will be dropped
    private final long singleChannelBufferMaxSizeBits;  // The output port buffer size for each virtual channel, which = bufferMaxSizeBits/numVCs.
    private final long[] credits;                       // The credits allocated from the upstream switch to the outport, in bits.
    private final long[] channelBufferOccupiedBits;     // The buffer occupied bits in each virtual channel.

    private final int numVCs;                           // The number of virtual channels

    private final Queue<Packet>[] channelQueue;
    // Multi queues need to be carried by this class.
    /**
     * Constructor.
     *
     * @param ownNetworkDevice      Source network device to which this output port is attached
     * @param targetNetworkDevice   Target network device that is on the other side of the link
     * @param link                  Link that this output ports solely governs
     * @param queue                 Queue that governs how packet are stored queued in the buffer
     */
    public SimpleInfinibandVCOutputPort(
            NetworkDevice ownNetworkDevice, 
            NetworkDevice towardsNetworkDevice, 
            Link link, 
            long maxQueueSizeBytes,
            int numVCs) {
        super(ownNetworkDevice, towardsNetworkDevice, link, new LinkedBlockingQueue<Packet>());

        // References to the link.
        this.link = link;

        // Set the VCs number
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
        // For managing the queue of this port
        this.bufferMaxSizeBits = maxQueueSizeBytes * 8L;
        this.singleChannelBufferMaxSizeBits = this.bufferMaxSizeBits / numVCs;
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
        InfinibandPacket ibPacket = (InfinibandPacket) packet;
        int channel = ibPacket.getVC();
        assert(channelBufferOccupiedBits[channel] + packet.getSizeBit() <= singleChannelBufferMaxSizeBits);
        // If it is not sending, then the queue is empty at the moment,
        // so if there is enough credit, then just send.
        if (!isSending && this.credits[channel] >= packet.getSizeBit()) {
            // Decrement the credit before sending.
            this.credits[channel] -= packet.getSizeBit();
            // It is now sending again
            isSending = true;

            // Add event when sending is finished
            ibPacket.setCurrentHopSwitchId(getOwnId());
            Simulator.registerEvent(new PacketDispatchedEvent(
                    packet.getSizeBit() / link.getBandwidthBitPerNs(),
                    packet,
                    this
                )
            );
            // Link is now being utilized
            logger.logLinkUtilized(true);
        } else { 
            // If it is still sending, the packet is added to the queue, making it non-empty
            this.channelBufferOccupiedBits[channel] += packet.getSizeBit();
            this.channelQueue[channel].add(packet);
            logger.logQueueState(queue.size(), bufferOccupiedBits);
        }
    }

    /**
     * Called by the upstream switch (i.e. target switch) to increment the credit in this port.
     * This reduces the backpressure. 
     */
    public void incrementCredit(long additionalCredits, int channel) {
        assert(channel >= 0 && channel < numVCs);
        this.credits[channel] += additionalCredits;
        // Next, check whether if the port is currently sending, and if so see whether we have a packet to send
        // or if there is sufficient credits to send.
        if (!isSending && !this.channelQueue[channel].isEmpty()) {
            // Check whether if there are any packets in the output queue to send.
            // Check whether if there is enough credit.
            InfinibandPacket headPacket = (InfinibandPacket) this.channelQueue[channel].peek();
            if (this.credits[channel] >= headPacket.getSizeBit()) {
                // Can send, decrement the credit first.
                this.credits[channel] -= headPacket.getSizeBit();
                // Since we had just popped packet from output queue, then just decrement the occupied buffer space.
                decreaseBufferOccupiedBits(headPacket.getSizeBit(), channel);
                // Pop the output queue
                this.channelQueue[channel].poll();
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
        InfinibandPacket ibPacket = (InfinibandPacket) packet;
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

        // If we get here then packet has been sent successfully, free up channel and search for next channel to send.
        // Again free to send other packets
        isSending = false;

        SimpleInfinibandVCSwitch owningDevice = (SimpleInfinibandVCSwitch) this.getOwnDevice();
        owningDevice.popInputQueue(this.getTargetId(), ibPacket.getVC());

        // Check if any of the channels have idle packets and sufficient credits to send.
        int nextChannelToSend = -1;
        for (int i = 1; i <= numVCs; i++) {
            int currentChannel = (ibPacket.getVC() + i) % numVCs;
            // if there are enough credits and actually packets to send, then set nextChannelToSend
            if (!this.channelQueue[currentChannel].isEmpty() && 
                this.channelQueue[currentChannel].peek().getSizeBit() <= this.credits[currentChannel]) {
                nextChannelToSend = currentChannel;
                break;
            }
        }
        if (nextChannelToSend < 0) {
            // Now figure out and see
        }
        // Check if there are more in the queue to send.
        if (nextChannelToSend >= 0) {
            // Check whether there are sufficient credits used to send the next packet.
            InfinibandPacket headPacket = (InfinibandPacket) this.channelQueue[nextChannelToSend].poll();
            headPacket.setCurrentHopSwitchId(getOwnId());
            decreaseBufferOccupiedBits(headPacket.getSizeBit(), nextChannelToSend);
            // Can send, decrement the credit first.
            this.credits[nextChannelToSend] -= headPacket.getSizeBit();
            assert(this.credits[nextChannelToSend] >= 0);
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
    protected void decreaseBufferOccupiedBits(long deltaAmount, int channel) {
        assert(channel >= 0 && channel < numVCs);
        this.channelBufferOccupiedBits[channel] -= deltaAmount;
        assert(this.channelBufferOccupiedBits[channel] >= 0);
    }


    public long getRemainingVCBufferBits(int currvc) {
        long remainingBufferSizeBits = singleChannelBufferMaxSizeBits - channelBufferOccupiedBits[currvc];
        assert(remainingBufferSizeBits >= 0);
        return remainingBufferSizeBits;
    }

    @Override
    public String toString() {
        return  "SimpleInfinibandVCOutputPort<" +
                    this.getOwnId() + " -> " +
                    "num VCs:" + numVCs +
                    " link: " + link +
                    ", occupied: " + bufferOccupiedBits +
                    ", queue size: " + this.getQueueSize() +
                ">";
    }
}
