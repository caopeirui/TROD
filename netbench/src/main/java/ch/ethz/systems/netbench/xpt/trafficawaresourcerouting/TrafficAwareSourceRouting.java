package ch.ethz.systems.netbench.xpt.trafficawaresourcerouting;

import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.run.routing.RoutingPopulator;
import ch.ethz.systems.netbench.ext.ecmp.EcmpSwitchRouting;
import edu.asu.emit.algorithm.graph.Graph;
import edu.asu.emit.algorithm.graph.Vertex;
import edu.asu.emit.algorithm.graph.algorithms.YenTopKShortestPathsAlg;

import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import static ch.ethz.systems.netbench.core.Simulator.getConfiguration;

public class TrafficAwareSourceRouting extends RoutingPopulator {

    protected final Map<Integer, NetworkDevice> idToNetworkDevice;
    
    // maps each switch id to its corresponding block ID
    protected HashMap<Integer, Integer> switchIDToBlockID; 
    
    protected HashMap<Integer, HashSet<Integer>> blockIDToSwitchIDs;

    // this is deliberately kept private, as it is only needed by traffic aware source routing
    private HashMap<Integer, HashMap<Integer, ArrayList<Pair <Integer, Double>>>> routingWeights; 
    
    // similarly also deliverately kept private, because it is something that only traffic aware source routing class will need.
    private String routingWeightsFilename;

    public TrafficAwareSourceRouting(Map<Integer, NetworkDevice> idToNetworkDevice, 
                                    String routingWeightsFilename) {
        this.idToNetworkDevice = idToNetworkDevice;
        this.routingWeightsFilename = routingWeightsFilename;

        this.routingWeights = new HashMap<Integer, HashMap<Integer, ArrayList<Pair <Integer, Double>>>>();
        this.blockIDToSwitchIDs = new HashMap<Integer, HashSet<Integer>>();
        this.switchIDToBlockID = new HashMap<Integer, Integer>();
        SimulationLogger.logInfo("Routing", "TRAFFIC_AWARE_SRC_ROUTING");
    }

    // reading weight of routing to each block via each switch
    private void readBlockWeight(Graph graph, GraphDetails details) {
        Set<Integer> setOfToRs = details.getTorNodeIds();
        for (int switchID : setOfToRs) {
            int srcBlockID = this.switchIDToBlockID.get(switchID);
            routingWeights.put(switchID, new HashMap<Integer, ArrayList<Pair <Integer, Double>>>());
            for (int dstBlockID : blockIDToSwitchIDs.keySet()) {
                if (srcBlockID != dstBlockID) {
                    routingWeights.get(switchID).put(dstBlockID, new ArrayList<Pair <Integer, Double>>());
                }
            }
        }

        // initialize the data structure that stores these weights
        try {
            File f = new File(this.routingWeightsFilename);
            if (f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(this.routingWeightsFilename));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    // Split up by comma
                    String[] split = line.split(",");
                    // format : switchID, targetBlock, entrySwitch, weight
                    // ignore any line with split length smaller than 2
                    if (split.length < 4 || line.charAt(0) == '#') {
                        continue;
                    }
                    // Retrieve source and destination graph device identifier
                    int switchID = Integer.parseInt(split[0]);
                    int blockID = Integer.parseInt(split[1]);
                    int entryID = Integer.parseInt(split[2]);
                    double weight = Double.parseDouble(split[3]);

                    if (weight < 0 || weight > 1) {
                        throw new IllegalArgumentException("Should not have routing weights outside of range [0, 1]");
                    }
                    routingWeights.get(switchID).get(blockID).add(new ImmutablePair(entryID, weight));
                }
                // Close stream
                br.close();
                System.out.println(" done.");
            } 
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // todo( ) : complete this
    protected void populateRoutingTablesIntraBlock(Graph graph, GraphDetails details) {
        //IntraBlockRoutingUtility routingUtility = new IntraBlockRoutingUtility();
        IntraBlockRoutingUtility.populateShortestPathRoutingTables(graph,
                                                                    details,
                                                                    idToNetworkDevice, 
                                                                    blockIDToSwitchIDs, 
                                                                    switchIDToBlockID);
        // go and set the routing for each server to their respective ToR switches
        Set<Integer> setOfServerIDs = details.getServerNodeIds();
        for (int serverID : setOfServerIDs) {
            int torID = details.getTorIdOfServer(serverID);
            BlockSwitch server = (BlockSwitch) idToNetworkDevice.get(serverID);
            BlockSwitch tor = (BlockSwitch) idToNetworkDevice.get(torID);
            tor.addConnectedServer(serverID);
        }
    }

    @Override
    public void populateRoutingTables() {

        // Populate ECMP routing state
        //new EcmpSwitchRouting(idToNetworkDevice).populateRoutingTables();
        for (int identifier : idToNetworkDevice.keySet()) {
            TrafficAwareSourceRoutingSwitch device = (TrafficAwareSourceRoutingSwitch) idToNetworkDevice.get(identifier);
            if (!device.isServer()) {
                int blockID = device.getBlockID();
                this.switchIDToBlockID.put(identifier, blockID);
                if (!this.blockIDToSwitchIDs.containsKey(blockID)) {
                    this.blockIDToSwitchIDs.put(blockID, new HashSet<Integer>());
                }
                this.blockIDToSwitchIDs.get(blockID).add(identifier);
            }
        }
        // Select all the nodes which are ToR
        GraphDetails details = getConfiguration().getGraphDetails();
        Graph graph = getConfiguration().getGraph();
        // Step 0 : read in the switches and what block they belong to.
        // this.assignSwitchIDToCorrespondingBlockID();

        this.populateRoutingTablesIntraBlock(graph, details);

        // next step is to program in the switch's weights to other blocks
        readBlockWeight(graph, details);

        // do other stuffs
        for (int switchID : details.getTorNodeIds()) {
            TrafficAwareSourceRoutingSwitch currentSwitch = (TrafficAwareSourceRoutingSwitch) idToNetworkDevice.get(switchID);
            int currentBlock = switchIDToBlockID.get(switchID);
            for (int blockID : blockIDToSwitchIDs.keySet()) {
                if (blockID != currentBlock) {
                    //readBlockWeight()
                    // get the entry switches
                    for (Pair<Integer, Double> entryIDWeightPair : this.routingWeights.get(switchID).get(blockID)) {
                        int entryID = entryIDWeightPair.getKey();
                        double weight = entryIDWeightPair.getValue();
                        currentSwitch.addEntrySwitchWeightToTargetDestBlock(blockID, entryID, weight);
                    }
                }
            }
        }


        // finally make sure that within each block, it can route directly to the server addresses
        HashMap<Integer, Integer> serverIDToBlockIDMap = new HashMap<Integer, Integer>();
        for (int serverID : details.getServerNodeIds()) {
            TrafficAwareSourceRoutingSwitch device = (TrafficAwareSourceRoutingSwitch) this.idToNetworkDevice.get(serverID);
            int torID = device.getToRID();
            serverIDToBlockIDMap.put(serverID, this.switchIDToBlockID.get(torID));
        }
        for (int identifier : this.idToNetworkDevice.keySet()) {
            TrafficAwareSourceRoutingSwitch device = (TrafficAwareSourceRoutingSwitch) this.idToNetworkDevice.get(identifier);
            device.setServerIDToBlockIDMap(serverIDToBlockIDMap);
        }
        
        // Adding for each switch in each block, where are the entry switches to each target block ID.
        // only valid for BlockValiantEcmpSwitch.
        for (int blockID : blockIDToSwitchIDs.keySet()) {
            HashSet<Integer> switchIDsInBlock = blockIDToSwitchIDs.get(blockID);

            HashMap<Integer, HashSet<Integer>> entrySwitchesToTargetBlocks = new HashMap<Integer, HashSet<Integer>>();

            for (int switchID : switchIDsInBlock) {
                Vertex switchVertex = graph.getVertex(switchID);
                List<Vertex> neighborVertices = graph.getAdjacentVertices( switchVertex );
                for (Vertex neighbor : neighborVertices) {
                    // check if neighbor is a server or a switch
                    if (idToNetworkDevice.get(neighbor.getId()).isServer()) {
                        continue;
                    }
                    int neighborBlockID = this.switchIDToBlockID.get(neighbor.getId());
                    if (neighborBlockID != blockID) {
                        if (!entrySwitchesToTargetBlocks.containsKey(neighborBlockID)) {
                            entrySwitchesToTargetBlocks.put(neighborBlockID, new HashSet<Integer>());
                        }
                        entrySwitchesToTargetBlocks.get(neighborBlockID).add(switchID);
                    }
                }
            }

            for (int switchID : switchIDsInBlock) {
                TrafficAwareSourceRoutingSwitch currentSwitchEntity = (TrafficAwareSourceRoutingSwitch) idToNetworkDevice.get(switchID);
                for (int targetBlockID : entrySwitchesToTargetBlocks.keySet()) {
                    for (int entrySwitch : entrySwitchesToTargetBlocks.get(targetBlockID)) {
                        currentSwitchEntity.addEntrySwitchToDestinationBlockID(targetBlockID, entrySwitch);
                    }
                }
            }
        }
        
        // check for correctness for each switch
        for (int identifier : idToNetworkDevice.keySet()) {
            if (idToNetworkDevice.get(identifier).isServer()) {
                continue;
            }
            TrafficAwareSourceRoutingSwitch traffic_aware_switch = 
                (TrafficAwareSourceRoutingSwitch) this.idToNetworkDevice.get(identifier);
            traffic_aware_switch.checkRoutingWeights(blockIDToSwitchIDs.keySet().size());
        }


    }
}
