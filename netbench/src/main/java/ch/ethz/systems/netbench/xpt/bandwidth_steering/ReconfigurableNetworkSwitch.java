package ch.ethz.systems.netbench.xpt.bandwidth_steering;

import ch.ethz.systems.netbench.core.network.*;
import ch.ethz.systems.netbench.ext.basic.TcpHeader;
import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_outport.ReconfigurableOutputPort;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import ch.ethz.systems.netbench.xpt.tcpbase.FullExtTcpPacket;




public class ReconfigurableNetworkSwitch extends NetworkDevice implements ReconfigurableNetworkSwitchInterface {

    // Connectivity tables and topology information
    private int connectedToRId; // if this is a server then the interconnected ToR id is saved here, else it is -1
    // maps the target pod id 
    private HashSet<Integer> targetPodIdBeingReconfigured;
    private HashMap<Integer, ReconfigurableOutputPort> targetPodIdToReconfigurableOutputPort;
    private HashMap<Integer, ReconfigurableOutputPort> targetDeviceIdToReconfigurableOutputPort;
    // Routing tables
    private HashMap<Integer, Integer> destinationIdToNextHopDeviceId;
    private boolean isAggregation;

    // Routing weights for interpod traffic
    private HashMap<Integer, PathSplitWeights> currentInterpodRoutingWeights;
    private HashMap<Integer, PathSplitWeights> afterReconfigurationInterpodRoutingWeights;

    // Topology information
    private Map<Integer, Integer> deviceIdToPodId;
    private int aggregationSwitchOfCurrentPod; 
    private HashMap<Long, Integer> cachedFlowRoutes;

    // Internal State
    protected Random rng;   // Random number generator, used for tie breaking when there is multiple possible paths involved.
    private final int podId; // Records the pod id of this switch
    /**
     * Constructor for ECMP switch.
     *
     * @param identifier        Network device identifier
     * @param transportLayer    Underlying server transport layer instance (set null, if none)
     * @param intermediary      Flowlet intermediary instance (takes care of hash adaptation for flowlet support)
     * @param podIdArg          The pod that this device belongs to
     */
    public ReconfigurableNetworkSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, int podIdArg) {        
        super(identifier, transportLayer, intermediary);
        this.rng = new Random();
        // this.fixedOutputPorts = new HashMap<Integer, OutputPort>();
        this.targetPodIdToReconfigurableOutputPort = new HashMap<Integer, ReconfigurableOutputPort>();
        this.targetDeviceIdToReconfigurableOutputPort = new HashMap<Integer, ReconfigurableOutputPort>();
        this.targetPodIdBeingReconfigured = new HashSet<Integer>();
        this.connectedToRId = -1;
        this.destinationIdToNextHopDeviceId = new HashMap<Integer, Integer>();
        this.isAggregation = false;
        this.currentInterpodRoutingWeights = new HashMap<>();
        this.afterReconfigurationInterpodRoutingWeights = new HashMap<>();
        this.podId = podIdArg;
        this.cachedFlowRoutes = new HashMap<Long, Integer>();
    }

    /**
     * Add a port which is a connection to another network device.
     *
     * @param outputPort    Output port instance
     */
    @Override
    public void addConnection(OutputPort outputPort) {
        // Port does not originate from here
        if (getIdentifier() != outputPort.getOwnId()) {
            throw new IllegalArgumentException("Impossible to add output port not originating from " + getIdentifier() + " (origin given: " + outputPort.getOwnId() + ")");
        }

        // Port going there already exists
        if (connectedTo.contains(outputPort.getTargetId())) {
            throw new IllegalArgumentException("Impossible to add a duplicate port from " + outputPort.getOwnId() + " to " + outputPort.getTargetId() + ".");
        }

        // decide whether if this new output port is connected to the same pod or not
        int targetDeviceId = outputPort.getTargetId();
        int targetPodId = ((ReconfigurableNetworkSwitch) outputPort.getTargetDevice()).getPodId();
        if (targetPodId == this.podId) {
            this.targetDeviceIdToReconfigurableOutputPort.put(targetDeviceId, (ReconfigurableOutputPort) outputPort);
        } else {
            this.isAggregation = true;
            this.targetPodIdToReconfigurableOutputPort.put(targetPodId, (ReconfigurableOutputPort) outputPort);
        }
        // Add to mappings
        connectedTo.add(targetDeviceId);
        targetIdToOutputPort.put(targetDeviceId, outputPort);
    }

    @Override
    public void receive(Packet genericPacket) {
        // Convert to TCP packet
        TcpHeader tcpHeader = (TcpHeader) genericPacket;

        long flowId = genericPacket.getFlowId();
        int srcId = tcpHeader.getSourceId();
        int dstId = tcpHeader.getDestinationId();
        int srcPodId = this.deviceIdToPodId.get(srcId);
        int dstPodId = this.deviceIdToPodId.get(dstId);
        int currentPodId = podId;
        
        // Handle the base cases, if this device is the destination, if this device is the source
        if (this.isServer()) {
            assert(this.identifier == srcId || this.identifier == dstId);
            if (dstId == this.identifier) {
                this.passToIntermediary(genericPacket);
            } else {

                ReconfigurableOutputPort outport = this.targetDeviceIdToReconfigurableOutputPort.get(this.connectedToRId);
                assert(outport.getTargetId() == this.connectedToRId);
                outport.enqueue(genericPacket);
            }
            return;
        } 
        // If we get here, it means that this device is a switch, not a server
        ReconfigurableOutputPort outport = null;
        // boolean increase_priority = false;
        if (this.podId == dstPodId) {
            int nextHopId = this.destinationIdToNextHopDeviceId.get(dstId);
            outport = this.targetDeviceIdToReconfigurableOutputPort.get(nextHopId);
            
        } else if (this.podId == srcPodId) {
            if (this.isAggregation) {
                // figure out if this should traverse 
                int intermediatePodId = this.findIntermediatePod(dstPodId, flowId);
                assert(intermediatePodId >= 0);
                assert(intermediatePodId != this.podId);

                // if (intermediatePodId == dstPodId) {
                //     increase_priority = true;
                // }

                outport = this.targetPodIdToReconfigurableOutputPort.get(intermediatePodId);
                assert(outport.getOwnId() == this.identifier);
                //if (outport.getCurrentLinkMultiplicity() == 0) {
                //    outport = null;
                //}
            } else {
                // this is a ToR leaf switch, so just get to the aggregation switch
                int nextHopId = this.destinationIdToNextHopDeviceId.get(this.aggregationSwitchOfCurrentPod);
                outport = this.targetDeviceIdToReconfigurableOutputPort.get(nextHopId);
            }
        } else {
            assert(this.isAggregation);
            // In an intermediate pod, route minimally to the destination pod.
            // increase_priority = true;
            outport = this.targetPodIdToReconfigurableOutputPort.get(dstPodId);
            //if (outport.getCurrentLinkMultiplicity() == 0) {
              //  outport = null;
            //}
        }

        if (outport != null) {
            outport.enqueue(genericPacket);
            // if (increase_priority) {
            //     FullExtTcpPacket packet_tmp = (FullExtTcpPacket) genericPacket;
            //     packet_tmp.increasePriority();
            //     outport.enqueue(packet_tmp);
            // } else {
            //     outport.enqueue(genericPacket);
            // }
        } 
        //else {
        //    throw new IllegalStateException("Could not find the appropriate output port to send this packet");
        //}
    }

    public void setDeviceIdTopodId(Map<Integer, Integer> deviceIdToPodIdMap) {
        this.deviceIdToPodId = deviceIdToPodIdMap;
    }

    public void setAggregationSwitchIdOfCurrentPod(int switchId) {
        this.aggregationSwitchOfCurrentPod = switchId;
    }

    @Override
    public void receiveFromIntermediary(Packet genericPacket) {
        // simple, just forward to the connected ToR.
        receive(genericPacket);
    }

    /**
     * Add another hop opportunity to the routing table for the given destination.
     *
     * @param destinationId     Destination identifier
     * @param nextHopId         A network device identifier where it could go to next (must have already been added
     *                          as connection via {@link #addConnection(OutputPort)}, else will throw an illegal
     *                          argument exception.
     */
    public void addDestinationToNextSwitch(int destinationId, int nextHopId) {

        // Check for not possible identifier
        if (!connectedTo.contains(nextHopId)) {
            throw new IllegalArgumentException("Cannot add hop to a network device to which it is not connected (" + nextHopId + ")");
        }
        if (this.destinationIdToNextHopDeviceId.containsKey(destinationId)) {
            throw new IllegalArgumentException("Cannot add more than one path to destination " + destinationId + ".");   
        }

        // Check for duplicate
        this.destinationIdToNextHopDeviceId.put(destinationId, nextHopId);
    }

    @Override
    public void triggerReconfiguration(Map<Integer, Long> targetPodToNewLinkMultiplicity, 
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> duringRoutingWeights,
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> afterRoutingWeights) {
        // Go and check whether if previous reconfiguration events has not completed. if so, then trigger an error
        // because the previous reconfiguration event hasn't completed yet
        
        if (!this.targetPodIdBeingReconfigured.isEmpty()) {
            throw new IllegalStateException("Cannot trigger two reconfigurations simultaneously when previous hasn't completed yet.");
        }
        // if we get here, it means that a new reconfiguration event can be triggered.
        // System.out.println("\n---------------------------------");
        for (Map.Entry<Integer, Long> entry : targetPodToNewLinkMultiplicity.entrySet()) {
            int targetPodId = entry.getKey();
            if (targetPodId == this.podId) {
                // ignore entries when target pod id is the same as the current pod id
                continue;
            }
            long newMultiplicity  = entry.getValue();
            // assert(newMultiplicity > 0);
            ReconfigurableOutputPort reconfigurableOutputPort = this.targetPodIdToReconfigurableOutputPort.get(targetPodId);
            // mark the port connecting to the target pod id to being drained
            this.targetPodIdBeingReconfigured.add(targetPodId);

            //   debug
            // System.out.printf("triggerReconfiguration PodId: %d, targetPodId: %d, newMultiplicity: %d\n", this.podId, targetPodId, newMultiplicity);
            reconfigurableOutputPort.triggerPortReconfiguration(newMultiplicity);
        }
        if (this.targetPodIdBeingReconfigured.isEmpty()) {
            this.currentInterpodRoutingWeights = afterRoutingWeights.get(this.podId);
            this.afterReconfigurationInterpodRoutingWeights = null;
        } else {
            this.currentInterpodRoutingWeights = duringRoutingWeights.get(this.podId);
            this.afterReconfigurationInterpodRoutingWeights = afterRoutingWeights.get(this.podId);
        }
        // proceed to remove the cached flow table.
        // this.cachedFlowRoutes.clear();
    }

    // Called by the reconfigurable port to signal this device that the reconfiguration for said port has been completed;
    @Override
    public void signalPortReconfigurationEnded(NetworkDevice targetDevice) {
        int targetDevicePodId = ((ReconfigurableNetworkSwitch) targetDevice).getPodId(); 
        assert(targetDevicePodId != this.podId);
        this.targetPodIdBeingReconfigured.remove(targetDevicePodId);
        // check if all ports have been reconfigured correctly, if so, can trigger to the new routing weights
        if (this.targetPodIdBeingReconfigured.isEmpty()) {
            // trigger a shift from the intermediate routing weights during reconfiguration to the new set of routing weights
            this.currentInterpodRoutingWeights = this.afterReconfigurationInterpodRoutingWeights;
            assert(this.afterReconfigurationInterpodRoutingWeights != null);
            this.afterReconfigurationInterpodRoutingWeights = null;
            // this.cachedFlowRoutes.clear(); // clear the cached flow routes again
            // System.out.println("switch " + this.identifier + " has finished reconfiguration");
        }
    }

    // Finds the pod-to-pod path, and decides at random whether 
    private int findIntermediatePod(int dstPod, long flowId) {
        // System.out.println("coapeirui debug findIntermediatePod()");
        // System.out.println(this.cachedFlowRoutes);
        // if (this.cachedFlowRoutes.containsKey(flowId)) {
        //     return this.cachedFlowRoutes.get(flowId);
        // }
        
        assert(this.currentInterpodRoutingWeights.containsKey(dstPod));

        PathSplitWeights possibleInterpodPaths = this.currentInterpodRoutingWeights.get(dstPod);
        double carrySumWeight = 0;
        boolean noUplink = true;
        double randomNumber = this.rng.nextDouble();
        int latestOption = -1;
        for (Map.Entry<Path, Double> pathWeightPair : possibleInterpodPaths.getPathSplitWeights().entrySet()) {
            Path currentPath = pathWeightPair.getKey();
            Double currentWeight = pathWeightPair.getValue();
            List<Integer> currentPathListRepresentation = currentPath.getListRepresentation();
            int intermediatePodId = -1;
            if (currentPathListRepresentation.size() == 2) {
                intermediatePodId = currentPath.getDst();
            } else if (currentPathListRepresentation.size() == 3) {
                intermediatePodId = currentPathListRepresentation.get(1);
            } else {
                throw new IllegalStateException("Illegal interpod path length of: " + currentPathListRepresentation.size());
            }
            
            long currentMultiplicity = this.targetPodIdToReconfigurableOutputPort.get(intermediatePodId).getCurrentLinkMultiplicity();
            if (currentMultiplicity > 0) {
                noUplink = false;
            }

            carrySumWeight += currentWeight;
            if (currentMultiplicity == 0) {
                // continue;       //   annotate
                noUplink = false;  //    topo 0
            }
            latestOption = intermediatePodId;
            if (carrySumWeight > randomNumber) {
                this.cachedFlowRoutes.put(flowId, intermediatePodId);
                return intermediatePodId;
            }
        }
        if (noUplink) {
            throw new IllegalStateException("This pod has no uplinks at all");
        }
        return latestOption;
    }

    /**
     * Returns the pod ID that this device belongs to. 
     */
    public int getPodId() {
        return this.podId;
    }

    /**
     * Returns true if this device is an aggregation switch, and false otherwise.
     * Aggregation switches are the ones that contain actual reconfigurable ports.
     *
     * @return Whether or not this switch/device contains any reconfigurable ports.
     */
    public boolean isAggregation() {
        return this.isAggregation;
    }

    public void setConnectedToRId(int deviceId) {
        if (this.connectedToRId >= 0) {
            throw new IllegalStateException("This device : " + this.identifier + "'s ToR id has been set already");
        }
        if (!this.isServer()) {
            throw new IllegalStateException("This function must only be called on servers. THis device : " + this.identifier + " is not a server.");   
        }
        System.out.println("Setting the connected ToR id: " + this.identifier + " to " + deviceId);
        this.connectedToRId = deviceId;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Reconfiguration Network Switch<id=");
        builder.append(getIdentifier());
        builder.append(", connected=");
        builder.append(connectedTo);
        builder.append(", routing: ");
        for (int i = 0; i < destinationIdToNextHopDeviceId.size(); i++) {
            if (i != 0) {
                builder.append(", ");
            }
            builder.append(i);
            builder.append("->");
            builder.append(destinationIdToNextHopDeviceId.get(i));
        }
        builder.append(">");
        return builder.toString();
    }

}
