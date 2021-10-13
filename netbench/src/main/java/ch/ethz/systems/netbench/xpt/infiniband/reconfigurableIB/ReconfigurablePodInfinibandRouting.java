package ch.ethz.systems.netbench.xpt.infiniband.reconfigurableIB;

import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.run.routing.RoutingPopulator;
import ch.ethz.systems.netbench.core.Simulator;
import edu.asu.emit.algorithm.graph.Graph;
import edu.asu.emit.algorithm.graph.Vertex;
import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;   // Import path and path split weights



import java.util.*;
import java.io.*; // for file reading

public class ReconfigurablePodInfinibandRouting extends RoutingPopulator {

    private final Map<Integer, NetworkDevice> idToNetworkDevice;
    
    private Map<Integer, Integer> deviceToPodIdMap;
    
    private Map<Integer, Set<Integer>> podIdToDeviceIdsMap;

    private final String wcmpInterpodPathFilename;
    // private CentralNetworkController centralNetworkController; // Responsible for generating the central network controller

    public ReconfigurablePodInfinibandRouting(Map<Integer, NetworkDevice> idToNetworkDevice, String wcmpInterpodPathFilenameArg) {
        this.idToNetworkDevice = idToNetworkDevice;
        this.deviceToPodIdMap = new HashMap<Integer, Integer>();
        this.podIdToDeviceIdsMap = new HashMap<Integer, Set<Integer>>();

        // Initialize podIdToDeviceIdsMap and deviceToPodIdMap
        for (Integer deviceId : idToNetworkDevice.keySet()) {
            ReconfigurableInfinibandSwitch device = (ReconfigurableInfinibandSwitch) idToNetworkDevice.get(deviceId); 
            int podId = device.getPodId();
            if (!this.podIdToDeviceIdsMap.containsKey(podId)) {
                this.podIdToDeviceIdsMap.put(podId, new HashSet<Integer>());
            }
            this.podIdToDeviceIdsMap.get(podId).add(deviceId);
            this.deviceToPodIdMap.put(deviceId, podId);
        }

        this.wcmpInterpodPathFilename = wcmpInterpodPathFilenameArg;
        SimulationLogger.logInfo("Routing", "ReconfigurablePodInfinibandRouting");
    }

    /**
     * Reads in a wcmp interpod routing path file and returns the routing weights of different paths.
     * Note that this routing weight is meant to be the initialized routing weights in the network devices
     * before any reconfiguration event actually hits the network.
     */
    private HashMap<Integer, HashMap<Integer, PathSplitWeights>> readInterpodWCMPRoutingWeightsFile() {
        // step 1 : figure out all of the path that exists between each source and destination
        // Create graph and prepare shortest path algorithm
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> initialRoutingWeights = 
            new HashMap<>();
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
                    initialRoutingWeights.put(src, new HashMap<>());
                }
                if (!initialRoutingWeights.get(src).containsKey(dst)) {
                    initialRoutingWeights.get(src).put(dst, new PathSplitWeights(src, dst));
                }
                PathSplitWeights pathSplitWeights = initialRoutingWeights.get(src).get(dst);
                // Initialize a pod-to-pod path
                ArrayList<Integer> pathListRepresentation;
                if (pathLen == 2) {
                    pathListRepresentation = new ArrayList<Integer>(Arrays.asList(src, dst));
                } else {
                    pathListRepresentation = new ArrayList<Integer>(Arrays.asList(src, next_hop, dst));
                }
                pathSplitWeights.updatePathWeight(new Path(pathListRepresentation), weight);
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
        // Step 2: Initialize the ECMP routing within each pod.
        ReconfigurablePodInfinibandRoutingUtility.populateIntraPodRoutingTables(idToNetworkDevice, this.podIdToDeviceIdsMap);
        // Step 3: Initialize the initial WCMP interpod routing weights.
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> initialGlobalRoutingWeights = readInterpodWCMPRoutingWeightsFile();

        // Step 4: For each of the aggregation switches, initialize the load balancers. But first need to figure out which ones are the aggregation switches
        HashMap<Integer, Integer> podIdToAggregationSwitchId = new HashMap<>();
        // Then, figure out the boundary switch id of each pod, and at the same time link all devices to central network controller
        for (int podId : this.podIdToDeviceIdsMap.keySet()) {
            int boundarySwitchId = -1;
            for (int deviceId : this.podIdToDeviceIdsMap.get(podId)) {
                ReconfigurableInfinibandSwitch device = (ReconfigurableInfinibandSwitch) this.idToNetworkDevice.get(deviceId);
                if (device.isAggregation()) {
                    podIdToAggregationSwitchId.put(podId, deviceId);
                    // Initialize the load balancers
                    if (boundarySwitchId >= 0) {
                        throw new IllegalStateException("One pod cannot have more than 1 aggregation/boundary switch to other pods");
                    } else {
                        boundarySwitchId = deviceId;
                    }

                    // Set the initial pod-to-pod routing weights, and then initialize the load balancers.
                    for (int targetPodId : this.podIdToDeviceIdsMap.keySet()) {
                        if (targetPodId != podId) {
                            device.setPathSplitWeights(targetPodId, initialGlobalRoutingWeights.get(podId).get(targetPodId));
                            device.addLoadBalancer(targetPodId);
                        }
                    }
                }
            }
        }
    }

}
