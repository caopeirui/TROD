package ch.ethz.systems.netbench.xpt.blockvaliant;

import ch.ethz.systems.netbench.ext.basic.IpPacket;
import ch.ethz.systems.netbench.ext.basic.TcpPacket;

public class BlockValiantEncapsulation extends IpPacket {

    private final TcpPacket packet;
    private int valiantBlockID;
    private boolean passedValiant;
    private boolean alreadySetValiant;
    private final int destinationBlockID;
    // marks the exit switch in the valiant block to the destination group
    private int valiantExitSwitch;
    // marks the entry switch in the source block that leads to the valiant group
    private int entrySwitchToValiantBlock; 

    public BlockValiantEncapsulation(TcpPacket packet, int valiantBlockID, int destBlockID) {
        super(packet.getFlowId(), packet.getSizeBit() - 480L, packet.getSourceId(), packet.getDestinationId(), packet.getTTL());
        this.packet = packet;
        this.valiantBlockID = valiantBlockID;
        this.passedValiant = false;
        this.alreadySetValiant = false;
        this.destinationBlockID = destBlockID;
        this.valiantExitSwitch = -1;
        this.entrySwitchToValiantBlock = -1;
    }

    public TcpPacket getPacket() {
        return packet;
    }

    public int getValiantBlock() {
        return valiantBlockID;
    }

    public int getDestinationBlock() {
        return destinationBlockID;
    }

    public int getValiantExitSwitchID() {
        return valiantExitSwitch;
    }

    public int getEntrySwitchToValiantBlock() {
        return entrySwitchToValiantBlock;
    }

    public void setValiantBlock(int valiantBlock) {
        if (this.alreadySetValiant) {
            throw new IllegalStateException("Cannot set valiant more than once");
        } else {
            this.valiantBlockID = valiantBlock;
            this.alreadySetValiant = true;
        }
    }

    // sets the switch id in the valiant block to use to traverse to final destination group
    public void setValiantExitSwitch(int switchID) {
        if (this.valiantExitSwitch >= 0) {
            throw new IllegalStateException("Should not be setting Valiant Exit switches that has been set");
        } else {
            this.valiantExitSwitch = switchID;
        }
    }

    // sets the switch id to the valiant block from the source block
    public void setEntrySwitchToValiantBlock(int switchID) {
        if (this.entrySwitchToValiantBlock >= 0) {
            throw new IllegalStateException("Should not be setting Valiant Entry switches that has been set");
        } else {
            this.entrySwitchToValiantBlock = switchID;
        }
    }

    public void markPassedValiant() {
        passedValiant = true;
    }

    public boolean passedValiant() {
        return passedValiant;
    }

    @Override
    public void markCongestionEncountered() {
        this.packet.markCongestionEncountered();
    }

}
