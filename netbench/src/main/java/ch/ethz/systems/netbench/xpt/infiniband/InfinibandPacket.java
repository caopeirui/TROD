package ch.ethz.systems.netbench.xpt.infiniband;

import ch.ethz.systems.netbench.core.network.Packet;

import java.util.ArrayList;

public class InfinibandPacket extends Packet implements InfinibandHeader {

    // IP header is [20, 60] bytes, assume maximum: 60 * 8
    private static final long PACKET_HEADER_SIZE_BIT = 480L;

    // IP header fields
    private final int sourceId;
    private final int destinationId;

    // Records the path record a packet has taken
    protected ArrayList<Integer> packetPath;

    // Sequence Number of the packet in the flow
    private final long seq;

    // Virtual channel
    private int currentVC;

    /**
     * Infiniband packet constructor.
     *
     * @param flowId            Flow identifier
     * @param payloadSizeBit    Payload of the IP packet in bits
     * @param sourceId          Source node identifier
     * @param destinationId     Destination node identifier
     */
    public InfinibandPacket(long flowId, long payloadSizeBit, int sourceId, int destinationId, long sequenceNumber) {
        super(flowId, PACKET_HEADER_SIZE_BIT + payloadSizeBit);
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.packetPath = new ArrayList<Integer>();
        this.seq = sequenceNumber;
        this.currentVC = 0;
    }

    public int getVC() {
        return this.currentVC;
    }

    public void setVC(int newVC) {
        this.currentVC = newVC;
    }

    public long getSequenceNumber() {
        return this.seq;
    }

    /**
     * Get the payload size in number of bytes.
     *
     * @return Payload size in number of bytes.
     */
    @Override
    public long getPayloadSizeBytes() {
        long payloadSizeBit = this.getSizeBit() - PACKET_HEADER_SIZE_BIT;
        long numBytes = payloadSizeBit / 8;
        if (payloadSizeBit % 8 > 0) {
            numBytes++;
        }
        return numBytes;
    }

    /**
     * Get the header size in number of bytes.
     *
     * @return Header size in number of bytes.
     */
    public static long getHeaderSizeBytes() {
        return PACKET_HEADER_SIZE_BIT / 8L;
    }

    /**
     * Get packet source node identifier.
     *
     * @return  Source node identifier
     */
    @Override
    public int getSourceId() {
        return sourceId;
    }

    /**
     * Get packet destination node identifier.
     *
     * @return  Destination node identifier
     */
    @Override
    public int getDestinationId() {
        return destinationId;
    }

    /**
     * Get the previous switch id that just sent this packet.
     *
     * @return  Switch Id of the previous hop's switch
     */
    @Override
    public int getPreviousSwitchId() {
        if (packetPath.isEmpty()) {
            return -1;
        }
        return packetPath.get(packetPath.size() - 1);
    }

    /**
     * Sets the id of the switch the packet just traversed through. 
     *
     * @return No return
     */
    @Override
    public void setCurrentHopSwitchId(int switchId) {
        packetPath.add(switchId);
    }

}
