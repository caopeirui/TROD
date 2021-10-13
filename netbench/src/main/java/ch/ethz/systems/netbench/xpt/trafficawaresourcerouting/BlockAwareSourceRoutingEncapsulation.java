package ch.ethz.systems.netbench.xpt.trafficawaresourcerouting;

import ch.ethz.systems.netbench.ext.basic.IpPacket;
import ch.ethz.systems.netbench.ext.basic.TcpPacket;

public class BlockAwareSourceRoutingEncapsulation extends IpPacket {

    private final TcpPacket packet;
    
    private int entrySwitchID;

    private final int destinationBlock;

    public BlockAwareSourceRoutingEncapsulation(TcpPacket packet,  int entrySwitchID, int destinationBlock) {
        super(packet.getFlowId(), packet.getSizeBit() - 480L, packet.getSourceId(), packet.getDestinationId(), packet.getTTL());
        this.packet = packet;
        this.destinationBlock = destinationBlock;
        this.entrySwitchID = entrySwitchID;
    }

    public int getDestinationBlock() {
        return this.destinationBlock;
    }

    public TcpPacket getPacket() {
        return packet;
    }

    public void setEntrySwitchID(int entrySwitchID) {
        this.entrySwitchID = entrySwitchID;
    }

    public int getEntrySwitchID() {
        return this.entrySwitchID;
    }

    @Override
    public void markCongestionEncountered() {
        this.packet.markCongestionEncountered();
    }

}
