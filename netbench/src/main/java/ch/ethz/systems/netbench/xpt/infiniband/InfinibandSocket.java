package ch.ethz.systems.netbench.xpt.infiniband;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.network.Socket;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.Packet;
import ch.ethz.systems.netbench.xpt.infiniband.InfinibandPacket;
import ch.ethz.systems.netbench.core.log.FlowLogger;

public class InfinibandSocket extends Socket {

    private static final long MAX_PACKET_PAYLOAD_BYTE = 1000L;
    private long remainderToConfirmFlowSizeByte;
    private FlowLogger privateLogger;
    private final long packetHeaderSizeBits;
    private final InfinibandSwitchInterface underlyingServer;
    private final boolean checkPacketDeliveryOrder;

    private long nextPacketSeqNumber = 1;
    /**
     * Create a Simplified IB based back-pressure socket. By default, it is the receiver.
     * The behavior is as follows: 
     * 1) Sender will try to push a packet into the underlying switch if there are sufficient credits
     * 2) If there are sufficient credits to send packets forward via the relevant port, then the packet is pushed
     *      in to the underlying network device hardware, otherwise we keep polling.
     * 3) Once it is in the network, then we can simply move forward without regard of the reply, because the network is lossless.
     *
     * @param transportLayer   Transport layer
     * @param underlyingServer The underlying physical server that implements infiniband interfaces
     * @param flowId           Flow identifier
     * @param sourceId         Source network device identifier
     * @param destinationId    Target network device identifier
     * @param flowSizeByte     Size of the flow in bytes
     */
    InfinibandSocket(TransportLayer transportLayer, 
            InfinibandSwitchInterface underlyingServer, 
            long flowId, 
            int sourceId, 
            int destinationId, 
            long flowSizeByte, 
            boolean flowLogger,
            boolean checkDeliveryOrder) {
        super(transportLayer, flowId, sourceId, destinationId, -1);
        this.remainderToConfirmFlowSizeByte = flowSizeByte;
        this.underlyingServer = underlyingServer;
        // By convention, a flow size byte of -1 should be a receiver socket, and we only want to log from the receiver
        if (flowLogger) {
            this.privateLogger = new FlowLogger(flowId, sourceId, destinationId, flowSizeByte);
        }
        this.packetHeaderSizeBits = InfinibandPacket.getHeaderSizeBytes() * 8L;
        /*
        if (flowLogger) {
            System.out.println("Flow socket " + flowId + " receiver: " + remainderToConfirmFlowSizeByte);
        } else {
            System.out.println("Flow socket " + flowId + " sender: " + remainderToConfirmFlowSizeByte);
        }
        */
        this.checkPacketDeliveryOrder = checkDeliveryOrder;
    }

    /** 
     *
     * Starts the flow by pushing the first packet into the network if there is sufficient space in the output port's buffer.
     * Does not send if the packet is too large.
     *
     */
    @Override
    public void start() {
        // Try to send a packet if there is enough buffer space in the output buffer.
        long availableBufferSizeBits = underlyingServer.queryServerInjectionPortBufferSizeBits();
        long intendedPayloadByteSize = getNextPayloadSizeByte();
        // System.out.println("Starting flow id : " + flowId +  " src: " + sourceId + " dst: " + destinationId + " available buffer size bits = " + availableBufferSizeBits);
        if (intendedPayloadByteSize * 8L + packetHeaderSizeBits <= availableBufferSizeBits) {
            // Only send when there is enough credits to support a packet's load, including its packet header.
            ((InfinibandTransportLayer) transportLayer).send(new InfinibandPacket(flowId, intendedPayloadByteSize * 8L, sourceId, destinationId, nextPacketSeqNumber++));
            confirmFlow(intendedPayloadByteSize);
        }
    }

    /**
     * Confirm the amount of flow given as argument.
     *
     * @param newlyConfirmedFlowByte     Amount of flow (> 0) newly confirmed
     */
    @Override
    protected void confirmFlow(long newlyConfirmedFlowByte) {
        if (remainderToConfirmFlowSizeByte < newlyConfirmedFlowByte) {
            throw new IllegalStateException("Flow id: " + flowId + 
                " has more bytes received than flow intended - newlyConfirmedFlowByte: " + newlyConfirmedFlowByte + " leftover flow byte: " + remainderToConfirmFlowSizeByte);
        }
        assert(this.remainderToConfirmFlowSizeByte >= newlyConfirmedFlowByte);
        this.remainderToConfirmFlowSizeByte -= newlyConfirmedFlowByte;
        // Only log the reception of the flow if the receiver has received a packet.
        if (isReceiver()) {
            this.privateLogger.logFlowAcknowledged(newlyConfirmedFlowByte);
        }
        // Remove references to the socket after finish
        if (isAllFlowConfirmed()) {
            ((InfinibandTransportLayer) transportLayer).cleanupSockets(flowId, isReceiver());
            // Register that a flow has completed i.f.f. this is a receiver.
            if (isReceiver()) {
                System.out.println("Receiver for flow: " + flowId + " has completed");
                Simulator.registerFlowFinished(flowId);
            }
        }
    }


    /**
     * Attempts to send a new packet if there is sufficient available buffer size in the output port
     * to carry the packet.
     *
     * @param availableBufferSizeBits       The number of bits in the output port's output queue buffer space.
     *
     * @return The total amount of bits the new sent packet consumes.
     */
    public long tryToSend(Long availableBufferSizeBits) {
        // Check whether if this is a sender socket or a receiver socket.
        if (isReceiver()) {
            throw new IllegalStateException("A receiver socket should not be allowed to send packets in backpressure network.");
        }
        if (remainderToConfirmFlowSizeByte <= 0) {
            System.out.println("What the fuck");
        }
        long intendedPayloadSize = getNextPayloadSizeByte();
        if (intendedPayloadSize * 8L + this.packetHeaderSizeBits <= availableBufferSizeBits) {
            InfinibandPacket newPacket = new InfinibandPacket(flowId, intendedPayloadSize * 8L, sourceId, destinationId, nextPacketSeqNumber++);
            transportLayer.send(newPacket);
            confirmFlow(newPacket.getPayloadSizeBytes());
            return newPacket.getSizeBit();
        }
        return 0L;
    }

    /**
     * Infiniband socket handles the reception of a packet.
     */
    @Override
    public void handle(Packet genericPacket) {
        assert(this.isReceiver()); // A socket can only handle packets if it is a receiver
        // TODO( ): Might want to record the packet latency here by recording the time at which the packet enters the network.
        long packetLatencyNs = Simulator.getCurrentTime() - genericPacket.getDepartureTime();
        InfinibandPacket ibPacket = (InfinibandPacket) genericPacket;
        // If check delivery order, then need to check whether if the arrival sequence number is the same 
        if (checkPacketDeliveryOrder) {
            long seqNumber = ibPacket.getSequenceNumber();
            if (seqNumber != nextPacketSeqNumber) {
                throw new IllegalStateException("Flow id: " + flowId + " packet arrival not in order. Expecting: " + nextPacketSeqNumber + ", Received: " + seqNumber);
            }
        }
        nextPacketSeqNumber++;
        // System.out.println("Receiver socket for flow: " + flowId + " received a packet.");
        // Confirms the flow on the receiver end. Checks whether if the flow has completed, and if so just clean up the socket.
        confirmFlow(ibPacket.getPayloadSizeBytes());  
    }

    /**
     * Check whether all flow has been confirmed via {@link #confirmFlow(long) confirmFlow}.
     *
     * @return  True iff all flow has been confirmed
     */
    @Override
    protected boolean isAllFlowConfirmed() {
        return remainderToConfirmFlowSizeByte == 0;
    }

    /**
     * Get the remaining amount of flow to be confirmed.
     *
     * @return  Remainder of flow not yet confirmed in bytes
     */
    @Override
    protected long getRemainderToConfirmFlowSizeByte() {
        return remainderToConfirmFlowSizeByte;
    }

    /**
     * Determine the payload size of the next packet.
     *
     * @return  Next payload size in bytes
     */
    private long getNextPayloadSizeByte() {
        // Typically we send packets that are of size MAX_PACKET_PAYLOAD_BYTE, but if we are at the final packet
        // then the size is simply the leftover number of bytes that the flow/socket has to send.
        return Math.min(MAX_PACKET_PAYLOAD_BYTE, getRemainderToConfirmFlowSizeByte());
    }


}
