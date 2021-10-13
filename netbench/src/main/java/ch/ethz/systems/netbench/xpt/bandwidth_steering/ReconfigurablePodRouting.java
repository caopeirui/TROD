package ch.ethz.systems.netbench.xpt.bandwidth_steering;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.run.routing.RoutingPopulator;
import edu.asu.emit.algorithm.graph.Graph;
import edu.asu.emit.algorithm.graph.Vertex;
import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ReconfigurablePodRouting extends RoutingPopulator {

    private final Map<Integer, NetworkDevice> idToNetworkDevice;
    private Map<Integer, Integer> deviceToPodIdMap;
    private Map<Integer, Set<Integer>> podIdToDeviceIdsMap;
    private final String wcmpInterpodPathFilename;
    // private CentralNetworkController centralNetworkController; // Responsible for generating the central network controller

    public ReconfigurablePodRouting(Map<Integer, NetworkDevice> idToNetworkDevice, String wcmpInterpodPathFilenameArg) {
        System.out.println("debug: ReconfigurablePodRouting start!!!");
        this.idToNetworkDevice = idToNetworkDevice;
        this.deviceToPodIdMap = new HashMap<Integer, Integer>();
        this.podIdToDeviceIdsMap = new HashMap<Integer, Set<Integer>>();

        // Initialize podIdToDeviceIdsMap and deviceToPodIdMap
        for (Integer deviceId : idToNetworkDevice.keySet()) {
            ReconfigurableNetworkSwitch device = (ReconfigurableNetworkSwitch) idToNetworkDevice.get(deviceId); 
            int podId = device.getPodId();
            if (!this.podIdToDeviceIdsMap.containsKey(podId)) {
                this.podIdToDeviceIdsMap.put(podId, new HashSet<Integer>());
            }
            this.podIdToDeviceIdsMap.get(podId).add(deviceId);
            this.deviceToPodIdMap.put(deviceId, podId);
        }

        this.wcmpInterpodPathFilename = wcmpInterpodPathFilenameArg;
        SimulationLogger.logInfo("Routing", "ReconfigurablePodRouting");
    }

    /*
    private void initializeCentralNetworkController(Map<Integer, NetworkDevice> idToNetworkDevice) {
        for (Map.Entry<Integer, NetworkDevice> entry : idToNetworkDevice.entrySet()) {
            int deviceId = entry.getKey();
            ReconfigurableNetworkSwitch device = (ReconfigurableNetworkSwitch) entry.getValue();
            int podId = device.getPodId();
            deviceToPodIdMap.put(deviceId, podId);
            if (!this.podIdToDeviceIdsMap.containsKey(podId)) {
                this.podIdToDeviceIdsMap.put(podId, new HashSet<Integer>());
            }
            this.podIdToDeviceIdsMap.get(podId).add(deviceId);
        }
        HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> initialRoutingSoln = readInterpodWCMPRoutingWeightsFile();
        //this.centralNetworkController = new CentralNetworkController(idToNetworkDevice, deviceToPodIdMap, this.podIdToDeviceIdsMap.size(), initialRoutingSoln);
    }
    */

    /**
     * Reads in a wcmp interpod routing path file and returns the routing weights of different paths.
     */
    private HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> readInterpodWCMPRoutingWeightsFile() {
        // step 1 : figure out all of the path that exists between each source and destination
        // Create graph and prepare shortest path algorithm
        HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> initialRoutingWeights = 
            new HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>>();
        Graph graph = Simulator.getConfiguration().getGraph();
        // First step is to figure out which server ID is connected to which ToR ID
        // computeServerToRRelationships(idToNetworkDevice);
        File file = new File(this.wcmpInterpodPathFilename); 
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
                if (!initialRoutingWeights.containsKey(src)) {
                    initialRoutingWeights.put(src, new HashMap<Integer, HashMap<Integer, Double>>());
                }
                if (!initialRoutingWeights.get(src).containsKey(dst)) {
                    initialRoutingWeights.get(src).put(dst, new HashMap<Integer, Double>());   
                }
                initialRoutingWeights.get(src).get(dst).put(next_hop, weight);
            }
            br.close();
        } catch (FileNotFoundException fe) {
            System.out.println("File not found");
        } catch (IOException ie) {
            System.out.println("IO exception not found");
        }
        return initialRoutingWeights;
    }

    /**
     * Initialize the multi-forwarding routing tables in the network devices.
     */
    @Override
    public void populateRoutingTables() {
        // Next, complete the intra-pod routing table for each pod
        RoutingUtility.populatePathRoutingTables(idToNetworkDevice, this.podIdToDeviceIdsMap);
        HashMap<Integer, Integer> podIdToAggregationSwitchId = new HashMap<>();
        Graph graph = Simulator.getConfiguration().getGraph();
        // Then, figure out the boundary switch id of each pod, and at the same time link all devices to central network controller
        for (int podId : this.podIdToDeviceIdsMap.keySet()) {
            int boundarySwitchId = -1;
            for (int deviceId : this.podIdToDeviceIdsMap.get(podId)) {
                ReconfigurableNetworkSwitch device = (ReconfigurableNetworkSwitch) this.idToNetworkDevice.get(deviceId);
                // device.setCentralNetworkController(this.centralNetworkController);
                if (device.isAggregation()) {
                    podIdToAggregationSwitchId.put(podId, deviceId);
                    if (boundarySwitchId >= 0) {
                        throw new IllegalStateException("One pod cannot have more than 1 aggregation/boundary switch to other pods");
                    } else {
                        boundarySwitchId = deviceId;
                    }
                } else if (device.isServer()) {
                    // if it's a server, then just set its ToR id to its only neighbor
                    int connectedToRId = -1;
                    List<Vertex> neighborsList = graph.getAdjacentVertices(graph.getVertex(deviceId));
                    assert(neighborsList.size() == 1); // a server must be connected to only one ToR/Lead switch
                    device.setConnectedToRId(neighborsList.get(0).getId());
                }
            }
            //this.centralNetworkController.setBoundarySwitchOfPod(boundarySwitchId, podId);
        }

        // Next, go into every single device and set the device to pod id map
        for (Integer deviceId : this.idToNetworkDevice.keySet()) {
            ReconfigurableNetworkSwitch device = (ReconfigurableNetworkSwitch) idToNetworkDevice.get(deviceId); 
            int podId = device.getPodId();
            int aggregationSwitchId = podIdToAggregationSwitchId.get(podId);
            device.setDeviceIdTopodId(this.deviceToPodIdMap);
            device.setAggregationSwitchIdOfCurrentPod(aggregationSwitchId);
        }
    }

}
