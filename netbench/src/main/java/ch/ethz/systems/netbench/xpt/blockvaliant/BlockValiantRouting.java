package ch.ethz.systems.netbench.xpt.blockvaliant;

import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.xpt.trafficawaresourcerouting.TrafficAwareSourceRouting;
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

public class BlockValiantRouting extends TrafficAwareSourceRouting {

    //private HashMap<Integer, HashMap<Integer, List<Pair<Integer, Double>>>> routingWeights;

    public BlockValiantRouting(Map<Integer, NetworkDevice> idToNetworkDevice) {
        // initialize the parent class
        super(idToNetworkDevice, "");
        //this.blockIDToSwitchIDs = new HashMap<Integer, HashSet<Integer>>();
        //this.switchIDToBlockID = new HashMap<Integer, Integer>();
        
        SimulationLogger.logInfo("Routing", "BLOCK_VALIANT_ROUTING");
    }

    @Override
    public void populateRoutingTables() {

        // Populate ECMP routing state
        //new EcmpSwitchRouting(idToNetworkDevice).populateRoutingTables();
        for (int identifier : idToNetworkDevice.keySet()) {
            BlockValiantEcmpSwitch device = (BlockValiantEcmpSwitch) idToNetworkDevice.get(identifier);
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
        HashMap<Integer, Integer> serverIDToBlockIDMap = new HashMap<Integer, Integer>();
        for (int serverID : details.getServerNodeIds()) {
            BlockValiantEcmpSwitch device = (BlockValiantEcmpSwitch) this.idToNetworkDevice.get(serverID);
            int torID = device.getToRID();
            serverIDToBlockIDMap.put(serverID, this.switchIDToBlockID.get(torID));
        }
        for (int identifier : this.idToNetworkDevice.keySet()) {
            BlockValiantEcmpSwitch device = (BlockValiantEcmpSwitch) this.idToNetworkDevice.get(identifier);
            device.setServerIDToBlockIDMap(serverIDToBlockIDMap);
        }
        // Step 1 : read in the routing weights for each switch
        // Create graph and prepare shortest path algorithm


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
                BlockValiantEcmpSwitch currentSwitchEntity = (BlockValiantEcmpSwitch) idToNetworkDevice.get(switchID);
                for (int targetBlockID : entrySwitchesToTargetBlocks.keySet()) {
                    for (int entrySwitch : entrySwitchesToTargetBlocks.get(targetBlockID)) {
                        currentSwitchEntity.addEntrySwitchToDestinationBlockID(targetBlockID, entrySwitch);
                    }
                }
            }
        }
    }
}
