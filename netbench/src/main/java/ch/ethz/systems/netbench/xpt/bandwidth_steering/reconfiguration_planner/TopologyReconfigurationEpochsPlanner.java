package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfiguration_planner;

import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.run.reconfiguration.TopologyReconfigurationPlanner;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitch;
// import ch.ethz.systems.netbench.xpt.bandwidth_steering.CentralNetworkController;
import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.proto.TopologyReconfigurationEventsProtos;

// Import the path and path split weights
import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;

import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.io.*; 

public class TopologyReconfigurationEpochsPlanner extends TopologyReconfigurationPlanner {

    private ArrayList<OneReconfigurationState> reconfig_state_seq;

    /**
     * Constructor.
     *
     * @param idToTransportLayerMap     Maps a network device identifier to its corresponding transport layer
     */
    public TopologyReconfigurationEpochsPlanner(Map<Integer, NetworkDevice> idToNetworkDevices) {
        super(idToNetworkDevices);
        reconfig_state_seq = new ArrayList<OneReconfigurationState>();
    }

    
    @Override
    public void planReconfigurationEvents() {
        // First read in the topology reconfiguration event filename.
        String filename = Simulator.getConfiguration().getPropertyWithDefault("reconfiguration_events_filename", "");
        TopologyReconfigurationEventsProtos.TopologyReconfigurationEvents reconfigEventSequence = readInTopologyReconfigurationEventsFile(filename);
        int numEvents = reconfigEventSequence.getEpochNanosecondCount();

        for (long reconfigTime : reconfigEventSequence.getEpochNanosecondList()) {
            System.out.println("First cycle Reconfig time ns: " + reconfigTime);
        }
        
        //   add
        long run_time_ns = Simulator.getConfiguration().getLongPropertyOrFail("run_time_ns");
        long reconfiguration_period = reconfigEventSequence.getEpochNanosecond(1);
        int cycle_length = reconfigEventSequence.getReconfigurationEventCount();
        System.out.printf("cycle_legth: %d\n", cycle_length);
        //    ReconfigurationTimerEvent 
        for (int i = 0; i < cycle_length; i++) {
            TopologyReconfigurationEventsProtos.TopologyReconfigurationEvent reconfigEventDetails = reconfigEventSequence.getReconfigurationEvent(i);
            // need to actually break down the reconfiguring target device and the actual
            HashMap<Integer, HashMap<Integer, Long>> reconfigSwitchDetail = new HashMap<>();
            for (TopologyReconfigurationEventsProtos.SwitchingDetail switchingDetail : reconfigEventDetails.getDetailList()) {
                int srcAggrSwitch = (int) switchingDetail.getSrcSwitchId();
                if (!reconfigSwitchDetail.containsKey(srcAggrSwitch)) {
                    reconfigSwitchDetail.put(srcAggrSwitch, new HashMap<Integer, Long>());
                }
                HashMap<Integer, Long> switchReconfigLinks = reconfigSwitchDetail.get(srcAggrSwitch);
                switchReconfigLinks.put((int) switchingDetail.getDstSwitchId(), switchingDetail.getAfterLinkMultiplicity());
                // assert(switchingDetail.getAfterLinkMultiplicity()>0);   //   annotation
            }

            // Derive the global routing weights from protobuf input
            TopologyReconfigurationEventsProtos.InterpodRoutingWeights duringWeightsProto = reconfigEventDetails.getDuringRoutingWeights();
            TopologyReconfigurationEventsProtos.InterpodRoutingWeights afterWeightsProto = reconfigEventDetails.getAfterRoutingWeights();
            HashMap<Integer, HashMap<Integer, PathSplitWeights>> duringGlobalRoutingWeights = extractPathSplitWeightsFromProtobuf(duringWeightsProto);
            HashMap<Integer, HashMap<Integer, PathSplitWeights>> afterGlobalRoutingWeights = extractPathSplitWeightsFromProtobuf(afterWeightsProto);

            //  
            for (int switchId : reconfigSwitchDetail.keySet()) {
                NetworkDevice networkSwitch = this.idToNetworkDevice.get(switchId);       
                recordReconfigurationEventArgs( 
                    networkSwitch,
                    reconfigSwitchDetail.get(switchId), 
                    duringGlobalRoutingWeights, 
                    afterGlobalRoutingWeights
                );
            }

        }

        //  ReconfigurationTimerEvent 
        long start_time = 0;
        long time_section = 1000000;
        while (true) {
            if (start_time + time_section > run_time_ns) {
                break;
            }
            // ReconfirurationTimerEvent  1ms     
            ReconfigurationTimerEvent recon_timer_event = new ReconfigurationTimerEvent(start_time, reconfiguration_period, this.reconfig_state_seq, time_section);
            Simulator.registerEvent(recon_timer_event);
            start_time = start_time + time_section;
        }

    }


    /**
     * Extracts the path split ratios for all pod pairs.
     *
     * @param protobufInterpodRoutingWeights                    The protobuf representative of interpod routing weights
     *
     * @return A hashmap of hashmaps, mapping a srcpod to dstpod of path split weights.
     **/
    private HashMap<Integer, HashMap<Integer, PathSplitWeights>> extractPathSplitWeightsFromProtobuf(TopologyReconfigurationEventsProtos.InterpodRoutingWeights protobufInterpodRoutingWeights) {
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> soln = new HashMap<>();
        for (TopologyReconfigurationEventsProtos.InterpodPathWeight pathWeight : protobufInterpodRoutingWeights.getInterpodWeightList()) {
            int srcPod = (int) pathWeight.getSrcPod();
            int dstPod = (int) pathWeight.getDstPod();
            int intermediatePod = (int) pathWeight.getIntermediatePod();
            double weight = pathWeight.getWeight();
            // Create the path first
            ArrayList<Integer> pathListRepresentation;
            if (dstPod == intermediatePod) {
                pathListRepresentation = new ArrayList<Integer>(Arrays.asList(srcPod, dstPod));
            } else {
                pathListRepresentation = new ArrayList<Integer>(Arrays.asList(srcPod, intermediatePod, dstPod));
            }
            Path path = new Path(pathListRepresentation);
            // Now insert this entry in the output
            if (!soln.containsKey(srcPod)) {
                soln.put(srcPod, new HashMap<>());
            }
            if (!soln.get(srcPod).containsKey(dstPod)) {
                soln.get(srcPod).put(dstPod, new PathSplitWeights(srcPod, dstPod));
            }
            soln.get(srcPod).get(dstPod).updatePathWeight(path, weight);
        }
        return soln;
    }

    private void printRoutingWeights(HashMap<Integer, HashMap<Integer, ArrayList<AbstractMap.SimpleImmutableEntry<Integer, Double>>>> routingWeights) {
        for (int srcPod : routingWeights.keySet()) {
            HashMap<Integer, ArrayList<AbstractMap.SimpleImmutableEntry<Integer, Double>>> srcPodWeights = routingWeights.get(srcPod);
            for (int dstPod : srcPodWeights.keySet()) {
                double pathSum = 0;
                for (AbstractMap.SimpleImmutableEntry<Integer, Double> simpleEntry : srcPodWeights.get(dstPod)) {
                    pathSum += simpleEntry.getValue();
                }
                System.out.println("src pod: " + srcPod + " dst pod: " + dstPod + " path sum " + pathSum);
            }
            System.out.println("Current pod " + srcPod + " has " + srcPodWeights.size());
        }
    }

    /**
     * Reads the sequence of reconfiguration events from a protobuf file.
     *
     * @param topologyReconfigurationEventFilename      The filename of the protobuf file which details the sequence of reconfiguration events.
     */
    private TopologyReconfigurationEventsProtos.TopologyReconfigurationEvents readInTopologyReconfigurationEventsFile(String topologyReconfigurationEventFilename) {
        // Read in the sequence of reconfiguration events, and the corresponding topology at each of these times
        TopologyReconfigurationEventsProtos.TopologyReconfigurationEvents reconfigurationEventSequence = null;
        try {    
            FileInputStream protoInputStream = new FileInputStream(topologyReconfigurationEventFilename);
            reconfigurationEventSequence = TopologyReconfigurationEventsProtos.TopologyReconfigurationEvents.parseFrom(protoInputStream);
            protoInputStream.close();
            int eventCount = reconfigurationEventSequence.getReconfigurationEventCount();
            int epochCount = reconfigurationEventSequence.getEpochNanosecondCount();
            // Check whether event count and epoch counts are both equal.
            assert(eventCount == epochCount);
        } catch (Exception e) {
            System.out.println(e);
        }
        
        return reconfigurationEventSequence;
    }

    /**
     * Register the starting reconfiguration event for a device ID.
     *
     * @param time          Time at which it starts in nanoseconds
     * @param deviceID      Network device identifier
     * @param dstId         Destination network device identifier
     * @param flowSizeByte  Flow size in bytes
     */
    private void recordReconfigurationEventArgs( 
        NetworkDevice reconfiguringNetworkDevice,
        HashMap<Integer, Long> reconfigurationDetailsArg,
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> duringGlobalRoutingWeightsArg,
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> afterGlobalRoutingWeightsArg
    ) {
        OneReconfigurationState a_reconfig_state = new OneReconfigurationState(
            reconfiguringNetworkDevice,
            reconfigurationDetailsArg,
            duringGlobalRoutingWeightsArg,
            afterGlobalRoutingWeightsArg
        );
        System.out.println("debug: recordReconfigurationEventArgs()");
        System.out.println(a_reconfig_state.getReconfiguringDevice());
        // System.out.println(a_reconfig_state.getReconfigurationDetails());
        this.reconfig_state_seq.add(a_reconfig_state);
    }
}
