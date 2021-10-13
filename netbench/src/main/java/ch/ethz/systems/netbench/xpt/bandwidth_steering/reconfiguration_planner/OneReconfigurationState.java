package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfiguration_planner;

import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;

import java.util.*;


/**
 *  
 */
public class OneReconfigurationState {
    private NetworkDevice reconfiguringDevice;
    private HashMap<Integer, Long> reconfigurationDetails;
    private HashMap<Integer, HashMap<Integer, PathSplitWeights>> duringRoutingWeights;
    private HashMap<Integer, HashMap<Integer, PathSplitWeights>> afterRoutingWeights;

    OneReconfigurationState(
        NetworkDevice reconfiguringDeviceArg,
        HashMap<Integer, Long> reconfigurationDetailsArg, 
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> duringRoutingWeightsArg, 
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> afterRoutingWeightsArg
    ) {
        this.reconfiguringDevice = reconfiguringDeviceArg;        
        this.reconfigurationDetails = reconfigurationDetailsArg;
        this.duringRoutingWeights = duringRoutingWeightsArg;
        this.afterRoutingWeights = afterRoutingWeightsArg;
    }

    public NetworkDevice getReconfiguringDevice() {
        return this.reconfiguringDevice;
    }

    public HashMap<Integer, Long> getReconfigurationDetails() {
        return this.reconfigurationDetails;
    }
    
    public HashMap<Integer, HashMap<Integer, PathSplitWeights>> getDuringRoutingWeights() {
        return this.duringRoutingWeights;
    }

    public HashMap<Integer, HashMap<Integer, PathSplitWeights>> getAfterRoutingWeights() {
        return this.afterRoutingWeights;
    }

}
