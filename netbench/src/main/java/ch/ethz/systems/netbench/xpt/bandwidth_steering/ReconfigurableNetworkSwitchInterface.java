package ch.ethz.systems.netbench.xpt.bandwidth_steering;

// Import for the routing weights
import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;

import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.core.network.NetworkDevice;

import java.util.*;

/**
 * Interface that must be implemented by network switches that wish to be run alongside the 
 * infiniband transport layer protocol. Since Infiniband uses backpressure
 */
public interface ReconfigurableNetworkSwitchInterface {

    /**
     * Given that the current device is a server, queries the output port's queue buffer space that is
     * currently occupied by packets. This is called by the infiniband transport layer to determine
     * whether if there is enough space in the output port to hold additional packets.
     * 
     * @return the number of bits currently available in the  
     */
    public void triggerReconfiguration(Map<Integer, Long> targetPodToNewLinkMultiplicity, 
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> duringRoutingWeights,
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> afterRoutingWeights);

    // TODO ( ) add another method that allows the output port to call to the owning switch that reconfiguration has completed.
    public void signalPortReconfigurationEnded(NetworkDevice targetDevice);
}
