package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfiguration_planner;

import ch.ethz.systems.netbench.core.network.*;
// import ch.ethz.systems.netbench.xpt.bandwidth_steering.CentralNetworkController;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitch;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitchInterface;

import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;


/**
 * Event for the complete arrival of a packet in its entirety.
 */
public class TriggerReconfigurationSwitchEvent extends Event {

    private NetworkDevice reconfiguringDevice;
    private HashMap<Integer, Long> reconfigurationDetails; // maps a source switch to a bunch of dest switches
    private HashMap<Integer, HashMap<Integer, PathSplitWeights>> duringRoutingWeights;      // Global routing weights during the reconfiguration
    private HashMap<Integer, HashMap<Integer, PathSplitWeights>> afterRoutingWeights;       // Global routing weights after reconfiguration is completed

    /**
     * Packet arrival event constructor.
     *
     * @param timeFromNowNs      Time in simulation nanoseconds from now
     * @param networkDevice      Network device at which the OCS reconfiguration is happening to
     * @param newReconfiguredTarget The new target network device of each reconfigurable output port after reconfiguration is done 
     */
    TriggerReconfigurationSwitchEvent(long timeFromNowNs, 
            NetworkDevice reconfiguringDeviceArg,
            HashMap<Integer, Long> reconfigurationDetailsArg, 
            HashMap<Integer, HashMap<Integer, PathSplitWeights>> duringRoutingWeightsArg, 
            HashMap<Integer, HashMap<Integer, PathSplitWeights>> afterRoutingWeightsArg) {
        super(timeFromNowNs);
        this.reconfiguringDevice = reconfiguringDeviceArg;        
        this.reconfigurationDetails = reconfigurationDetailsArg;
        this.duringRoutingWeights = duringRoutingWeightsArg;
        this.afterRoutingWeights = afterRoutingWeightsArg;

        //   debug
        // System.out.println("debug TriggerReconfigurationSwitchEvent()");
        // for (Integer it : duringRoutingWeightsArg.keySet()) {
        //     HashMap<Integer, PathSplitWeights> weights = duringRoutingWeightsArg.get(it);
        //     for (Integer j: weights.keySet()) {
        //         PathSplitWeights test = weights.get(j);
        //         System.out.println(test.getSrc());
        //         System.out.println(test.getDst());
        //         System.out.println(test.getPathSplitWeights());
        //         System.out.println("--------------------");
        //     }
        // }
    }

    // reconfiguration details

    @Override
    public void trigger() {
        ((ReconfigurableNetworkSwitchInterface) this.reconfiguringDevice).triggerReconfiguration(reconfigurationDetails, duringRoutingWeights, afterRoutingWeights);
    }

    @Override
    public String toString() {
        return "TriggerReconfigurationSwitchEvent<switch device id= " + this.reconfiguringDevice.getIdentifier() + ", time=" + this.getTime() + ">";
    }

}
