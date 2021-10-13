package ch.ethz.systems.netbench.xpt.bandwidth_steering;

import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.Simulator;
import edu.asu.emit.algorithm.graph.Graph;
import edu.asu.emit.algorithm.graph.Vertex;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.io.*; 


public class RoutingUtility {

    private static final int INFINITY = 999999999;

    // need to figure out the path ratio
    public RoutingUtility() {
        // Cannot be instantiated
    }

    /**
     * Calculate all the shortest paths and store them internally.
     * Uses the modified Floyd-Warshall algorithm.
     */
    private static HashMap<Integer, HashMap<Integer, Integer>> calculateShortestPathsWithinPod(Graph graph, Set<Integer> nodesInCurrentPod) {

        int numNodes = graph.getVertexList().size();
        HashMap<Integer, HashMap<Integer, Integer>> shortestPathLen = new HashMap<Integer, HashMap<Integer, Integer>>();

        // Initialize the distance "matrix" and find easy shortest paths
        for (int nodeId : nodesInCurrentPod) {
            HashMap<Integer, Integer> row = new HashMap<Integer, Integer>();
            for (int nodeId2 : nodesInCurrentPod) {
                if (nodeId == nodeId2) {
                    row.put(nodeId2, 0);
                } else if (graph.getAdjacentVertices(graph.getVertex(nodeId)).contains(graph.getVertex(nodeId2))) {
                    row.put(nodeId2, 1);
                } else {
                    row.put(nodeId2, INFINITY);
                }
                
            }
            shortestPathLen.put(nodeId, row);
        }

        // Floyd-Warshall algorithm
        for (int k : nodesInCurrentPod) {
            for (int i : nodesInCurrentPod) {
                for (int j : nodesInCurrentPod) {
                    int dist_ij = shortestPathLen.get(i).get(j);
                    int dist_ik = shortestPathLen.get(i).get(k);
                    int dist_kj = shortestPathLen.get(k).get(j);
                    if (dist_ij > dist_ik + dist_kj) {
                        shortestPathLen.get(i).put(j, dist_ik + dist_kj);
                    }
                }
            }
        }
        return shortestPathLen;
    }

    private static void populateIntraPodRoutingTables(Graph graph, 
            Map<Integer, NetworkDevice> idToNetworkDevice, 
            int podId, 
            Set<Integer> nodesInCurrentPod) {
        // For each pod, route each server to all other servers via ECMP.
        System.out.print(" Intra-pod routing table setup for pod " + podId + "...");
        HashMap<Integer, HashMap<Integer, Integer>> distanceMatrix = calculateShortestPathsWithinPod(graph, nodesInCurrentPod);
        // Go over every network device pair and set the forwarder switch routing table
        for (int i : nodesInCurrentPod) {
            for (int j : nodesInCurrentPod) {
                if (i != j) {
                    // For every outgoing edge (i, j) check if it is on a shortest path to j
                    List<Vertex> adjacent = graph.getAdjacentVertices(graph.getVertex(i));
                    int dist_ij = distanceMatrix.get(i).get(j);
                    for (Vertex v : adjacent) {
                        if (!nodesInCurrentPod.contains(v.getId())) {
                            // skip over vertext that is not in the same pod.
                            continue;
                        }
                        // ECMP stores all the possible hops
                        if (dist_ij == distanceMatrix.get(v.getId()).get(j) + 1) {
                            ((ReconfigurableNetworkSwitch) idToNetworkDevice.get(i)).addDestinationToNextSwitch(j, v.getId());
                        }
                    }
                }
            }
        }
    }

    /**
     * Initializes the multi-forwarding ECMP routing tables in the network devices.
     * The network devices must be ECMP switches, and should have been generated
     * corresponding to the topology graph defined in the run configuration.
     *
     * @param idToNetworkDevice     Mapping of network device identifier to network device
     */
    public static void populatePathRoutingTables(Map<Integer, NetworkDevice> idToNetworkDevice, 
            Map<Integer, Set<Integer>> podIdToDeviceIds) {
        // step 1 : figure out all of the path that exists between each source and destination
        // Create graph and prepare shortest path algorithm
        System.out.print("Reading path weights lengths...");
        Graph graph = Simulator.getConfiguration().getGraph();
        int numNodes = Simulator.getConfiguration().getGraphDetails().getNumNodes();
        // First step is to figure out which server ID is connected to which ToR ID
        // computeServerToRRelationships(idToNetworkDevice);
        for (int podId : podIdToDeviceIds.keySet()) {
            // First, populate the intra-pod routing table using ECMP/Shortest path routing
            populateIntraPodRoutingTables(graph, idToNetworkDevice, podId, podIdToDeviceIds.get(podId));
            // also identify the boundary switch
        }
    }

    
}
