package ch.ethz.systems.netbench.xpt.ugal;

import ch.ethz.systems.netbench.ext.basic.IpPacket;
import ch.ethz.systems.netbench.ext.basic.TcpPacket;

public class BlockUgalEncapsulation extends IpPacket {

    private final TcpPacket packet;
    
    private boolean enteredValiantBlock;
    
    private int destinationBlockID;

    // marks the exit switch in the valiant block to the destination group
    private int valiantExitSwitch; 

    // valiant block ID
    private int valiantBlockID;

    // marks the entry switch in the source block that leads to the valiant group
    private int entrySwitchToValiantBlock; 

    public BlockUgalEncapsulation(TcpPacket packet, int destBlockID) {
        super(packet.getFlowId(), packet.getSizeBit() - 480L, packet.getSourceId(), packet.getDestinationId(), packet.getTTL());
        this.packet = packet;
        this.enteredValiantBlock = false;
        this.destinationBlockID = destBlockID;
        this.valiantBlockID = -1;
        this.valiantExitSwitch = -1;
        this.entrySwitchToValiantBlock = -1;
    }

    // Get methods
    public TcpPacket getPacket() {
        return packet;
    }

    public int getValiantBlockID() {
        return valiantBlockID;
    }

    public int getDestinationBlockID() {
        return destinationBlockID;
    }

    public int getValiantExitSwitchID() {
        return valiantExitSwitch;
    }

    public int getEntrySwitchToValiantBlock() {
        return entrySwitchToValiantBlock;
    }

    // Set methods
    public void setValiantBlock(int valiantBlock) {
        this.valiantBlockID = valiantBlock;
    }

    // sets the switch id in the valiant block to use to traverse to final destination group
    public void setValiantExitSwitch(int switchID) {
        this.valiantExitSwitch = switchID;
    }

    // sets the switch id to the valiant block from the source block
    public void setEntrySwitchToValiantBlock(int switchID) {
        this.entrySwitchToValiantBlock = switchID;
    }

    public void markEnteredValiant() {
        enteredValiantBlock = true;
    }

    public boolean enteredValiant() {
        return enteredValiantBlock;
    }

    @Override
    public void markCongestionEncountered() {
        this.packet.markCongestionEncountered();
    }

}
