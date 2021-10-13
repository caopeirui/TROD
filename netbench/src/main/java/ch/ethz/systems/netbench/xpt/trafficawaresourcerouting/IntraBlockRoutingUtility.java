package ch.ethz.systems.netbench.xpt.trafficawaresourcerouting;

import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.Simulator;
import edu.asu.emit.algorithm.graph.Graph;
import ch.ethz.systems.netbench.core.config.GraphDetails;
import edu.asu.emit.algorithm.graph.Vertex;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class IntraBlockRoutingUtility {

    private static final int INFINITY = 999999999;

    // Note that the populate routing table will simply route the tables
    private IntraBlockRoutingUtility() {
        // Cannot be instantiated
    }

    /**
     * Calculate all the shortest paths and store them internally.
     * Uses the modified Floyd-Warshall algorithm.
     *
     * @param graph     The actual graph of the topology, could include servers 
     * @param setOfToRs     The set of tors, which are switches and vertices we are interested in actually routing
     * @param setOfBlockIDs     The set of blocks in the entire topology
     */
    private static HashMap<Integer, HashMap<Integer, Integer>> calculateShortestPathsInBlock(Graph graph, Set<Integer> switchIDsInBlock) {

        System.out.print("Calculating shortest path lengths...");

        // Iterate over each block ID and route each one
        HashMap<Integer, HashMap<Integer, Integer>> distanceMatrix = new HashMap<Integer, HashMap<Integer, Integer>>();

        // Initial scan to find easy shortest paths
        for (int switchID1 : switchIDsInBlock) {
            HashMap<Integer, Integer> distanceList = new HashMap<Integer, Integer>();
            for (int switchID2 : switchIDsInBlock) {
                if (switchID1 == switchID2) {
                    distanceList.put(switchID2, 0);
                } else if (graph.getAdjacentVertices(graph.getVertex(switchID1)).contains(graph.getVertex(switchID2))) {
                    distanceList.put(switchID2, 1);
                } else {
                    distanceList.put(switchID2, INFINITY);
                }
            }
            distanceMatrix.put(switchID1, distanceList);
        }

        // Floyd-Warshall algorithm
        for (int k : switchIDsInBlock) {
            for (int i : switchIDsInBlock) {
                for (int j : switchIDsInBlock) {
                    int distij = distanceMatrix.get(i).get(j);
                    int distik = distanceMatrix.get(i).get(k);
                    int distkj = distanceMatrix.get(k).get(j);
                    if (distij > distik + distkj) {
                        distanceMatrix.get(i).put(j, distik + distkj);
                    }
                }
            }
        }
        System.out.println(" done.");
        return distanceMatrix;
    }

    /**
     * Initializes the multi-forwarding ECMP routing tables in the network devices.
     * The network devices must be ECMP switches, and should have been generated
     * corresponding to the topology graph defined in the run configuration.
     *
     * @param idToNetworkDevice     Mapping of network device identifier to network device, note that the servers are not here, only the switches
     */
    public static void populateShortestPathRoutingTables(Graph graph,
                                                        GraphDetails details,
                                                        Map<Integer, NetworkDevice> idToNetworkSwitch, 
                                                        HashMap<Integer, HashSet<Integer>> blockIDToSwitchIDs, 
                                                        HashMap<Integer, Integer> switchIDToBlockID) {

        // Create graph and prepare shortest path algorithm

        System.out.print("Populating Traffic Aware Source forward routing tables for blocks...");
        int numberblocks = 0;
        System.out.println("" + blockIDToSwitchIDs.keySet().toString());
        for (int blockID : blockIDToSwitchIDs.keySet()) {

            HashMap<Integer, HashMap<Integer, Integer>> distanceMatrix = IntraBlockRoutingUtility.calculateShortestPathsInBlock(graph, blockIDToSwitchIDs.get(blockID));

            for (int i : blockIDToSwitchIDs.get(blockID)) {
                BlockSwitch blockSwitch = (BlockSwitch) idToNetworkSwitch.get( i );
                for (int j : blockIDToSwitchIDs.get(blockID)) {
                    if (i != j) {
                        // For every outgoing edge (i, j) check if it is on a shortest path to j
                        List<Vertex> adjacent = graph.getAdjacentVertices(graph.getVertex(i));
                        for (Vertex v : adjacent) {
                            if (idToNetworkSwitch.get(v.getId()).isServer()) {
                                continue;
                            }
                            // check to see if v is a switch
                            
                            if (blockIDToSwitchIDs.get(blockID).contains(v.getId())) {
                                if (distanceMatrix.get(i).get(j) == distanceMatrix.get(v.getId()).get(j) + 1) {
                                    blockSwitch.addDestinationToNextHopID(j, v.getId());
                                    Set<Integer> serversAttachedToDstSwitch = details.getServersOfTor(j);
                                    for (int server : serversAttachedToDstSwitch) {
                                        blockSwitch.addDestinationToNextHopID(server, v.getId());
                                    }
                                }    
                            } else {
                                // this means that the neighbor is in another block
                                int targetBlockID = switchIDToBlockID.get(v.getId());
                                // entrySwitchesBetweenBlocks.get(blockID).get(targetBlockID).add(i);
                                blockSwitch.addDestinationBlock(targetBlockID, v.getId());
                            }
                            
                            // ignore if it's just a server
                        }
                    }
                }
            }
            // Log progress...
            System.out.print(" " + (++numberblocks) + " blocks out of " + blockIDToSwitchIDs.size() + " blocks completed...");
        }
        // Go over every network device pair and set the forwarder switch routing table
        System.out.println(" Done.");
    }
}
