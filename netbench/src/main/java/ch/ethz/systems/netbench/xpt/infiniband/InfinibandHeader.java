package ch.ethz.systems.netbench.xpt.infiniband;

import ch.ethz.systems.netbench.core.network.PacketHeader;

public interface InfinibandHeader extends PacketHeader {

    /**
     * Get packet source node identifier.
     *
     * @return  Source node identifier
     */
    int getSourceId();

    /**
     * Get packet destination node identifier.
     *
     * @return  Destination node identifier
     */
    int getDestinationId();

    /**
     * Get the previous switch id that just sent this packet.
     *
     * @return  Switch Id of the previous hop's switch
     */
    int getPreviousSwitchId();

    /**
     * Sets the id of the switch the packet just traversed through. 
     *
     * @return No return
     */
    void setCurrentHopSwitchId(int switchId);

    /**
     * Get the payload size in number of bytes.
     *
     * @return Payload size in number of bytes.
     */
    long getPayloadSizeBytes();    
}
