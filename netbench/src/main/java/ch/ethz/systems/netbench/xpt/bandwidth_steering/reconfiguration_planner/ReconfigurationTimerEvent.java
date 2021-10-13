package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfiguration_planner;

import ch.ethz.systems.netbench.core.network.*;
import ch.ethz.systems.netbench.core.Simulator;
// import ch.ethz.systems.netbench.xpt.bandwidth_steering.CentralNetworkController;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitch;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitchInterface;

import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;


/**
 * ReconfigurationTimerEvent 1ms   
 */
public class ReconfigurationTimerEvent extends Event {

    private long start_time;
    private long reconfiguration_period;
    private ArrayList<OneReconfigurationState> reconfig_state_seq;
    private long time_section;

    /**
     * Packet arrival event constructor.
     *
     * @param start_time      Time in simulation nanoseconds from start_time
     * @param reconfiguration_period   
     * @param reconfig_state_seq      cycle 
     * @param time_section     
     */
    ReconfigurationTimerEvent(
        long start_time,
        long reconfiguration_period,
        ArrayList<OneReconfigurationState> reconfig_state_seq,
        long time_section
    ) {
        super(start_time);
        this.start_time = start_time;
        this.reconfiguration_period = reconfiguration_period;
        this.reconfig_state_seq = reconfig_state_seq;
        this.time_section = time_section;
    }

    @Override
    public void trigger() {
        //  
        long now_time = this.start_time;
        while (now_time < this.start_time + this.time_section) {
            int cycle_length = (int)Math.sqrt(this.reconfig_state_seq.size());
            int cnt = 0;
            for (OneReconfigurationState a_reconfig_state : this.reconfig_state_seq) {
                if (now_time > this.start_time + this.time_section) {
                    return;
                }
                // System.out.println("  debug:");
                // System.out.println(now_time);
                // System.out.println(a_reconfig_state.getReconfiguringDevice());
                // System.out.println(a_reconfig_state.getReconfigurationDetails());
                // System.out.println(a_reconfig_state.getDuringRoutingWeights());
                // System.out.println(a_reconfig_state.getAfterRoutingWeights());
                TriggerReconfigurationSwitchEvent reconfig_event = new TriggerReconfigurationSwitchEvent(
                    now_time,
                    a_reconfig_state.getReconfiguringDevice(), 
                    a_reconfig_state.getReconfigurationDetails(), 
                    a_reconfig_state.getDuringRoutingWeights(), 
                    a_reconfig_state.getAfterRoutingWeights()
                );
                Simulator.registerEvent(reconfig_event);
                cnt += 1;
                if (cnt == cycle_length) {
                    now_time = now_time + this.reconfiguration_period;
                    cnt = 0;
                }
            }
        }
    }

    @Override
    public String toString() {
        // return "ReconfigurationTimerEvent<switch device id= ";
        return "ReconfigurationTimerEvent<start_time= " + this.start_time + ", reconfiguration_period=" + this.reconfiguration_period;
    }

}
