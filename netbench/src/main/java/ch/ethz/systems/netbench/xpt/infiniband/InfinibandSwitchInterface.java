package ch.ethz.systems.netbench.xpt.infiniband;

import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.OutputPort;

/**
 * Interface that must be implemented by network switches that wish to be run alongside the 
 * infiniband transport layer protocol. Since Infiniband uses backpressure
 */
public interface InfinibandSwitchInterface {

    /**
     * Given that the current device is a server, queries the output port's queue buffer space that is
     * currently occupied by packets. This is called by the infiniband transport layer to determine
     * whether if there is enough space in the output port to hold additional packets.
     * 
     * @return the number of bits currently available in the  
     */
    public long queryServerInjectionPortBufferSizeBits();

}
