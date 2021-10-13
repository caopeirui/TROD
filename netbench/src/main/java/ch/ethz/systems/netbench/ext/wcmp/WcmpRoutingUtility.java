package ch.ethz.systems.netbench.ext.wcmp;

import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.Simulator;
import edu.asu.emit.algorithm.graph.Graph;
import edu.asu.emit.algorithm.graph.Vertex;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.*; 

/*
public class Path {
    Integer [] path;

    public Path(List)

    Integer getSrcID() {
        return this.
    }

    boolean isEqual(Path path) {

    }
}
*/

public class WcmpRoutingUtility {

    private static final int INFINITY = 999999999;

    // need to figure out the path ratio
    public WcmpRoutingUtility() {
        // Cannot be instantiated
    }

    /**
     * Initializes the multi-forwarding ECMP routing tables in the network devices.
     * The network devices must be ECMP switches, and should have been generated
     * corresponding to the topology graph defined in the run configuration.
     *
     * @param idToNetworkDevice     Mapping of network device identifier to network device
     */
    public static void populatePathRoutingTables(Map<Integer, NetworkDevice> idToNetworkDevice, String filename) {
        // step 1 : figure out all of the path that exists between each source and destination
        // Create graph and prepare shortest path algorithm
        System.out.print("Reading path weights lengths...");
        Graph graph = Simulator.getConfiguration().getGraph();
        int numNodes = Simulator.getConfiguration().getGraphDetails().getNumNodes();
        // First step is to figure out which server ID is connected to which ToR ID
        // computeServerToRRelationships(idToNetworkDevice);
        File file = new File(filename); 
        try (FileReader fr = new FileReader(file)) {
            BufferedReader br = new BufferedReader(fr);     
            String st; 
            while ((st = br.readLine()) != null) {
                if (st.length() == 0) {
                    continue;
                }
                // format must be : path len, weight, node1, node2, .... node p, when p = path len
                String[] strArray = st.split(",", 0);
                int pathLen = Integer.parseInt(strArray[0]);
                double weight = Double.parseDouble(strArray[1]);
                int[] path = new int[pathLen];
                // check if path length is satisfied
                for (int index = 0; index < pathLen; index++) {
                    path[index] = Integer.parseInt(strArray[index  + 2]);
                }
                int src = path[0];
                int dst = path[pathLen - 1];
                int next_hop = dst;
                if (pathLen > 2) {
                    next_hop = path[1];
                }
                //addDestinationToNextSwitch(int destinationId, int nextHopId)
                ((WcmpSwitchRoutingInterface) idToNetworkDevice.get(src)).addDestinationToNextSwitch(dst, next_hop, weight);
                /*
                // added for when there is server attached to each tor.
                int dstServerID = ((WcmpSwitch) idToNetworkDevice.get(dst)).getServerID();
                if (dstServerID < 0) {
                    throw new IllegalStateException("Server ID must have been set at this point");
                }
                ((WcmpSwitchRoutingInterface) idToNetworkDevice.get(src)).addDestinationToNextSwitch(dstServerID, next_hop, weight);
                */
            }
            br.close();
        } catch (FileNotFoundException fe) {
            System.out.println("File not found");
        } catch (IOException ie) {
            System.out.println("IO exception not found");
        }

    }

    public static void computeServerToRRelationships(Map<Integer, NetworkDevice> idToNetworkDevice) {
        Graph graph = Simulator.getConfiguration().getGraph();
        HashMap<Integer, Integer> serverIDtoToRIDMap = new HashMap<Integer, Integer>();
        for (int identifier : idToNetworkDevice.keySet()) {
            WcmpSwitch device = (WcmpSwitch) idToNetworkDevice.get(identifier);
            if (device.isServer()) {
                Vertex deviceVertex = graph.getVertex(identifier);
                List<Vertex> neighborVertices = graph.getAdjacentVertices( deviceVertex );
                for (Vertex neighbor : neighborVertices) {
                    int torID = neighbor.getId();
                    WcmpSwitch torDevice = (WcmpSwitch) idToNetworkDevice.get(torID);
                    device.addToRID(torID);
                    torDevice.addServerID(identifier);
                    //torDevice.addServerIDToToRID(identifier, torID);
                    serverIDtoToRIDMap.put(identifier, torID);
                    break;
                }  
            } 
        }

        for (int identifier : idToNetworkDevice.keySet()) {
            WcmpSwitch device = (WcmpSwitch) idToNetworkDevice.get(identifier);
            if (!device.isServer()) {
                WcmpSwitch torDevice = (WcmpSwitch) idToNetworkDevice.get(identifier);
                for (int serverIDtmp : serverIDtoToRIDMap.keySet()) {
                    int connectedToRID = serverIDtoToRIDMap.get(serverIDtmp);
                    torDevice.addServerIDToToRID(serverIDtmp, connectedToRID);
                }
            }
        }
    }
    /*
    public static void populatePathRoutingTables(Map<Integer, NetworkDevice> idToNetworkDevice, String filename) {

        // Create graph and prepare shortest path algorithm
        Graph graph = Simulator.getConfiguration().getGraph();
        int numNodes = Simulator.getConfiguration().getGraphDetails().getNumNodes();

        System.out.print("Populating WCMP forward routing tables...");


        // Go over every network device pair and set the forwarder switch routing table
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (i != j) {

                    // For every outgoing edge (i, j) check if it is on a shortest path to j
                    List<Vertex> adjacent = graph.getAdjacentVertices(graph.getVertex(i));
                    for (Vertex v : adjacent) {

                        // ECMP stores all the possible hops
                        if (isEcmp) {

                            if (shortestPathLen[i][j] == shortestPathLen[v.getId()][j] + 1) {
                                ((EcmpSwitchRoutingInterface) idToNetworkDevice.get(i)).addDestinationToNextSwitch(j, v.getId());
                            }

                        // ... whereas single-forward routing only stores a single hop entry
                        } else {
                            if (shortestPathLen[i][j] == shortestPathLen[v.getId()][j] + 1) {
                                ((ForwarderSwitch) idToNetworkDevice.get(i)).setDestinationToNextSwitch(j, v.getId());
                                break; // We only need a single possibility
                            }
                        }

                    }
                }

            }

            // Log progress...
            if (numNodes > 10 && (i + 1) % ((numNodes / 10)) == 0) {
                System.out.print(" " + (((double) i + 1) / (numNodes) * 100) + "%...");
            }
        }
        System.out.println(" done.");
    }
    */
}
