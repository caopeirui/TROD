package ch.ethz.systems.netbench.xpt.infiniband.reconfigurableIB;

// Network related imports
import ch.ethz.systems.netbench.core.network.*;

// Import from path and routing path split weights
import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;

// Import from load balancer
import ch.ethz.systems.netbench.ext.wcmp.loadbalancer.*;

// Reconfigurability related imports
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitchInterface;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_outport.ReconfigurableOutputPort;

// Infiniband related imports
import ch.ethz.systems.netbench.xpt.infiniband.InfinibandSwitchInterface;
import ch.ethz.systems.netbench.xpt.infiniband.InfinibandPacket;
import ch.ethz.systems.netbench.xpt.infiniband.InfinibandTransportLayer;

// Jave util import
import java.util.*; 
import java.util.AbstractMap.SimpleImmutableEntry;

// Used for priority queue when sorting which packets should go first based on how long they have been born in the network.
class PacketArrivalComparator implements Comparator<Packet>{ 
    // Overriding compare()method of Comparator  
    // for descending order of cgpa 
    public int compare(Packet packet1, Packet packet2) { 
        long packet1DepartureTime = packet1.getDepartureTime();
        long packet2DepartureTime = packet2.getDepartureTime();
        if (packet1DepartureTime < packet2DepartureTime) {
            return 1; 
        }
        return -1; 
    } 
} 

/**
 * A pod-reconfigurable switch that implements infiniband's link level backpressure flow control.
 * Therefore, this switch will only send a packet upstream i.f.f. there is sufficient credit in bits
 * given by the upstream switch to the current switch.
 *
 * NOTE: This switch does not explicitly guarantee deadlock-free, since only 1 virtual channel is used. Moreover,
 * it doesn't guarantee in-order packet delivery, as this is immensely challenging for reconfigurable networks.
 */
public class ReconfigurableInfinibandSwitch extends NetworkDevice implements ReconfigurableNetworkSwitchInterface, InfinibandSwitchInterface {

    // Connectivity tables and topology information
    
    // maps the target pod id 
    protected HashSet<Integer> targetDeviceIdsBeingReconfigured;                          // Records the target device ids upstream which are undergoing reconfiguration.

    // Routing tables
    protected HashMap<Integer, Integer> destinationIdToNextHopDeviceId;
    protected HashMap<Long, Integer> cachedFlowRoutes = new HashMap<>();         // Cache for the inter-pod paths for inter-pod flows, shared globally 

    // Routing weights for interpod traffic
    protected HashMap<Integer, PathSplitWeights> currentInterpodRoutingWeights;
    protected HashMap<Integer, PathSplitWeights> afterReconfigurationInterpodRoutingWeights;

    // Load-balancer for inter-pod routing.  Maps a source pod and dest pod to a LoadBalancer class
    protected HashMap<Integer, LoadBalancer> loadBalancers = new HashMap<>();
    protected final boolean useStatefulLoadBalancer;      // A flag to control whether load balancer is stateful or stateless, and otherwise it is false.
    protected final boolean employPacketSpraying;         // Packets from the same flow can follow different paths, allowing for finer grained load balancing.


    // Topology information
    protected static Map<Integer, Integer> deviceIdToPodId = new HashMap<>();             // Maps all the devices to their respective pod ids
    protected static Map<Integer, Integer> podIdToAggregationSwitchId = new HashMap<>();  // Identifies the switch id of the aggregation switch for each pod

    protected final int podId; // Records the pod id of this switch

    protected boolean isAggregation;

    // Input queues for backpressure
    private final long inputQueueBufferMaxSizeBits;
    private final Map<Integer, Queue<Packet>> inputQueues;                              // Contains the input packet buffer
    protected final HashMap<Integer, Long> inputQueuesCurrentRemainingSizeBits; 
    protected final HashMap<Integer, ReconfigurableInfinibandOutputPort> downstreamOutputPorts; // Contains references to the downstream output ports, indexed by the owning switch id of the downstream switch

    /**
     * Constructor for ECMP switch.
     *
     * @param identifier        Network device identifier
     * @param transportLayer    Underlying server transport layer instance (set null, if none)
     * @param intermediary      Flowlet intermediary instance (takes care of hash adaptation for flowlet support)
     * @param podIdArg          The pod that this device belongs to
     */
    public ReconfigurableInfinibandSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, int podIdArg, long inputQueueBufferMaxSizeBits, boolean statefulLoadBalancer, boolean usePacketSpraying) {
        super(identifier, transportLayer, intermediary);
        this.targetDeviceIdsBeingReconfigured = new HashSet<>();
        this.destinationIdToNextHopDeviceId = new HashMap<Integer, Integer>();
        this.currentInterpodRoutingWeights = new HashMap<>();
        this.afterReconfigurationInterpodRoutingWeights = new HashMap<>();

        // Infiniband backpressure related.
        this.inputQueueBufferMaxSizeBits = inputQueueBufferMaxSizeBits;
        this.inputQueuesCurrentRemainingSizeBits = new HashMap<>();
        this.downstreamOutputPorts = new HashMap<>();
        this.inputQueues = new HashMap<>();
        // Intialize the pod id this switch belongs to.
        this.podId = podIdArg;
        this.isAggregation = false;

        // Used for stateful or stateless load balancer
        this.useStatefulLoadBalancer = statefulLoadBalancer;
        this.employPacketSpraying = usePacketSpraying;

        // Add the current identifier to the shared memory deviceIdToPodId so that other devices will able to recognize.
        this.deviceIdToPodId.put(identifier, podIdArg);

        // Tie this device to the transport layer if it is not null
        if (transportLayer != null) {
            ((InfinibandTransportLayer) transportLayer).setUnderlyingDevice(this);
        }
    }

    /**
     * Add a port which is a connection to another network device.
     *
     * @param outputPort    Output port instance
     */
    @Override
    public void addConnection(OutputPort outputPort) {

        // Port does not originate from here
        if (identifier != outputPort.getOwnId()) {
            throw new IllegalArgumentException("Impossible to add output port not originating from " + getIdentifier() + " (origin given: " + outputPort.getOwnId() + ")");
        }

        // Port going there already exists, need to throw an error
        if (connectedTo.contains(outputPort.getTargetId())) {
            throw new IllegalArgumentException("Impossible to add a duplicate port from " + outputPort.getOwnId() + " to " + outputPort.getTargetId() + ".");
        }

        // Add to mappings
        connectedTo.add(outputPort.getTargetId());
        targetIdToOutputPort.put(outputPort.getTargetId(), outputPort);

        // In the target switch, add the input queues and also set its downstream output port
        ReconfigurableInfinibandSwitch targetInfinibandSwitch = (ReconfigurableInfinibandSwitch) outputPort.getTargetDevice();
        targetInfinibandSwitch.addDownstreamOutputPort((ReconfigurableInfinibandOutputPort) outputPort);

        // We've identified that this switch is an aggregation switch
        if (targetInfinibandSwitch.getPodId() != this.podId) {
            this.isAggregation = true;
            // Check that a pod must only have ONE aggregation switch.
            assert(!this.podIdToAggregationSwitchId.containsKey(this.podId) || this.podIdToAggregationSwitchId.get(this.podId) == identifier);
            // NOTE that the podIdToAggregationSwitchId is a shared memory between all ReconfigurableInfinibandSwitch instances, 
            // so each aggreagtion switch needs to add itself, and every one else will see it.
            this.podIdToAggregationSwitchId.put(this.podId, this.identifier);
        }
    }

    /**
     * Called by the downstream switch of the current switch (i.e. downstream switch is one which 
     * has an output port leading to this switch).
     *
     * @param outputPort    The output port of the downstream switch
     *
     */
    private void addDownstreamOutputPort(ReconfigurableInfinibandOutputPort outputPort) {
        int downstreamSwitchId = outputPort.getOwnId();
        assert(identifier == outputPort.getTargetId());
        assert(!this.downstreamOutputPorts.containsKey(downstreamSwitchId));
        assert(!this.inputQueues.containsKey(downstreamSwitchId));
        this.downstreamOutputPorts.put(downstreamSwitchId, outputPort);
        this.inputQueues.put(downstreamSwitchId, new LinkedList<Packet>());
        this.inputQueuesCurrentRemainingSizeBits.put(downstreamSwitchId, this.inputQueueBufferMaxSizeBits);
        outputPort.incrementCredit(this.inputQueueBufferMaxSizeBits);
    }

    /** 
     * Called by the routing populator function, note that 
     **/
    public void addLoadBalancer(int dstPod) {
        assert(dstPod != podId && !loadBalancers.containsKey(dstPod));
        LoadBalancer interpodLB = null;
        if (useStatefulLoadBalancer) {
            interpodLB = new StatefulLoadBalancer(this.currentInterpodRoutingWeights.get(dstPod));
        } else {
            interpodLB = new StatelessLoadBalancer(this.currentInterpodRoutingWeights.get(dstPod));
        }
        loadBalancers.put(dstPod, interpodLB);
    }

    /** 
     * GIven a path split weight
     **/
    public void setPathSplitWeights(int dstPod, PathSplitWeights newPathSplitWeights) {
        assert(dstPod != podId && newPathSplitWeights.getSrc() == podId && newPathSplitWeights.getDst() == dstPod);
        this.currentInterpodRoutingWeights.put(dstPod, newPathSplitWeights);
    }

    /**
     * TODO( ) : Need to debug this, might enable different degrees of in-order delivery.
     * Could implement a version that tries to tally the flow taking different paths so far, and spread packets such that the total ratio is closest to the 
     * path split ratio.
     */
    private int findIntermediatePod(int dstPod, long flowId) {
        if (!employPacketSpraying && this.cachedFlowRoutes.containsKey(flowId)) {
            return this.cachedFlowRoutes.get(flowId);
        }
        LoadBalancer lb = this.loadBalancers.get(dstPod);
        List<Integer> nextPacketPath = lb.getPath().getListRepresentation();
        int nextPodId = nextPacketPath.get(1);
        if (!employPacketSpraying) {
            this.cachedFlowRoutes.put(flowId, nextPodId);
        }
        return nextPodId;
    }

    /**
     * Decides what the next step switch id must be for a packet witch the following
     * parameters. 
     *
     * @param flowId                    The flow id this packet belongs to.
     * @param srcPodId                  The source pod id this packet started from.
     * @param dstId                     The destination id of this packet.
     * @param dstPodId                  The pod id of the destination switch.
     *
     * @return The next hop switch id.
     **/
    private int routePacket(Packet generalPacket, long flowId, int srcPodId, int dstId, int dstPodId) {
        int nextHopId = -1;
        if (podId == dstPodId) {
            nextHopId = destinationIdToNextHopDeviceId.get(dstId);
        } else if (podId == srcPodId) {
            if (identifier == podIdToAggregationSwitchId.get(srcPodId)) {
                // figure out if this should traverse 
                int intermediatePodId = this.findIntermediatePod(dstPodId, flowId);
                assert(intermediatePodId >= 0);
                assert(intermediatePodId != this.podId);
                nextHopId = podIdToAggregationSwitchId.get(intermediatePodId);
                // Log the next hop id with the load balancer.
                // TODO log the packet
                List<Integer> interpodPathList;
                if (nextHopId == dstPodId) {
                    interpodPathList = new ArrayList<Integer>(Arrays.asList(podId, dstPodId));
                } else {
                    interpodPathList = new ArrayList<Integer>(Arrays.asList(podId, nextHopId, dstPodId));
                }
                this.loadBalancers.get(dstPodId).logPathTaken(new Path(interpodPathList), generalPacket.getSizeBit() / 8L);
            } else {
                // this is a ToR leaf switch, so just get to the aggregation switch
                nextHopId = podIdToAggregationSwitchId.get(podId);
            }
        } else {
            // Ensure that this switch must be the aggregation switch
            assert(identifier == podIdToAggregationSwitchId.get(podId));
            // In an intermediate pod, route minimally to the destination pod.
            // Returns the aggregation switch to hop to in the destination pod.
            nextHopId = podIdToAggregationSwitchId.get(dstPodId); 
        }
        return nextHopId;
    }

    /**
     * Handles the reception of a packet. We have two choices, either try to see the next output port
     * queue has enough buffer space remaining, or put it in the input queue.
     *
     * @param genericPacket             Received packet.
     **/
    @Override
    public void receive(Packet genericPacket) {
        // Convert to Infiniband packet
        InfinibandPacket ibPacket = (InfinibandPacket) genericPacket;

        long flowId = ibPacket.getFlowId();
        int srcId = ibPacket.getSourceId();
        int dstId = ibPacket.getDestinationId();
        long packetSizeBits = genericPacket.getSizeBit();
        int prevSwitchId = ibPacket.getPreviousSwitchId();
        int srcPodId = deviceIdToPodId.get(srcId);
        int dstPodId = deviceIdToPodId.get(dstId);

        // Pass to transport layer if we've reached the destination
        if (identifier == dstId) {
            // Need to increment the downstream output port's credit, otherwise it will eventually run out of credits.
            downstreamOutputPorts.get(prevSwitchId).incrementCredit(packetSizeBits);
            passToIntermediary(genericPacket);
            return;
        }

        // Come up with a routing decision for this packet, and then two cases:
        //  1) If the output port can hold more packets, then queue this packet in the output port's queue, and increment credit for downstream port
        //  2) else, place it in the input queue.
        int nextHopSwitchId = routePacket(genericPacket, flowId, srcPodId, dstId, dstPodId);
        assert(nextHopSwitchId >= 0 && targetIdToOutputPort.containsKey(nextHopSwitchId));

        // TODO( ): Do we need to check multiplicity?
        ReconfigurableInfinibandOutputPort outport = (ReconfigurableInfinibandOutputPort) targetIdToOutputPort.get(nextHopSwitchId);
        
        assert(prevSwitchId >= 0);
        if (outport != null && outport.getRemainingBufferSizeBits() >= packetSizeBits) {
            // When there is enough room in output port's queue, place it there
            outport.enqueue(genericPacket);
            // increment the credits in the downstream switch
            downstreamOutputPorts.get(prevSwitchId).incrementCredit(packetSizeBits);
        } else {
            // Not enough buffer space at the output port's queue, place packet in the input queue instead.
            inputQueues.get(prevSwitchId).add(genericPacket);
            // Update the input queue's occupied buffer space
            long inputQueueBufferRemainingBits = inputQueuesCurrentRemainingSizeBits.get(prevSwitchId);
            inputQueueBufferRemainingBits -= packetSizeBits;
            assert(inputQueueBufferRemainingBits >= 0);
            inputQueuesCurrentRemainingSizeBits.put(prevSwitchId, inputQueueBufferRemainingBits);
        }
    }

    /** 
     * TODO ( ): need to implement this.
     * Receives a packet from intermediary, which in turn received from the transport layer. 
     * In this case, the transport layer has to implement a lossless backpressure-like 
     * congestion control layer.
     *
     * @param genericPacket         A packet received from the intermediary and transport layer.
     *
    **/
    @Override
    public void receiveFromIntermediary(Packet genericPacket) {
        // Simply queue up the packet in the injection port's output queue.
        if (connectedTo.size() != 1) {
            throw new IllegalStateException("There has to be exactly one output port for each server.");
        }
        int nextSwitchId = connectedTo.get(0);
        ReconfigurableInfinibandOutputPort ibOutputPort = (ReconfigurableInfinibandOutputPort) targetIdToOutputPort.get(nextSwitchId);
        // Check the output port and make sure that there is actually enough credits
        assert(genericPacket.getSizeBit() <= ibOutputPort.getRemainingBufferSizeBits());
        ibOutputPort.enqueue(genericPacket);
    }

    /**
     * Reception of a packet by the network device from the underlying transport layer.
     * Adapts it via the intermediary and then sends it on to the switch.
     * Do not override. 
     *
     * @param genericPacket    Packet instance
     */
    @Override
    public void receiveFromTransportLayer(Packet genericPacket) {
        this.receiveFromIntermediary(genericPacket);
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

    /** 
     * Triggers the reconfiguration of this switch, taking in a new aggregation switch routing weight.
     **/
    @Override
    public void triggerReconfiguration(Map<Integer, Long> targetPodToNewLinkMultiplicity, 
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> duringRoutingWeights,
        HashMap<Integer, HashMap<Integer, PathSplitWeights>> afterRoutingWeights) {
        // Check whether previous reconfiguration events has not completed. If so, then trigger an error
        // because the previous reconfiguration event hasn't completed yet while a new reconfiguration event
        // has already begun.
        if (!this.targetDeviceIdsBeingReconfigured.isEmpty()) {
            throw new IllegalStateException("Cannot trigger two reconfigurations simultaneously when previous hasn't completed yet.");
        }
        // if we get here, it means that a new reconfiguration event can be triggered.
        for (Map.Entry<Integer, Long> entry : targetPodToNewLinkMultiplicity.entrySet()) {
            int targetSwitchId = entry.getKey();
            long newMultiplicity  = entry.getValue();
            // Check that the target device id must be connected to the current switch.
            int targetPodId = deviceIdToPodId.get(targetSwitchId);
            assert(targetIdToOutputPort.containsKey(targetSwitchId) && targetPodId != podId);
            ReconfigurableInfinibandOutputPort reconfigurableOutputPort = (ReconfigurableInfinibandOutputPort) this.targetIdToOutputPort.get(targetSwitchId);
            // Mark the port connecting to the target pod id to being drained
            this.targetDeviceIdsBeingReconfigured.add(targetSwitchId);
            reconfigurableOutputPort.triggerPortReconfiguration(newMultiplicity);
        }

        // If there is nothing to reconfigure, then straight up change the routing weights 
        if (targetDeviceIdsBeingReconfigured.isEmpty()) {
            this.currentInterpodRoutingWeights = afterRoutingWeights.get(this.podId);
            this.afterReconfigurationInterpodRoutingWeights = null;
        } else {
            this.currentInterpodRoutingWeights = duringRoutingWeights.get(this.podId);
            this.afterReconfigurationInterpodRoutingWeights = afterRoutingWeights.get(this.podId);
        }

        // Next, go and reset all of the load balancers
        for (int targetPodId : this.loadBalancers.keySet()) {
            LoadBalancer lb = this.loadBalancers.get(targetPodId);
            lb.reset(this.currentInterpodRoutingWeights.get(targetPodId));
        }
    }

    /**
     * Called by the reconfigurable port to signal to the owning device that the port's reconfiguration has completed.
     *
     * @param targetDevice                      The network device to which the output port is connected to.
     */ 
    @Override
    public void signalPortReconfigurationEnded(NetworkDevice targetDevice) {
        int targetDeviceId = targetDevice.getIdentifier(); 
        this.targetDeviceIdsBeingReconfigured.remove(targetDeviceId);
        // check if all ports have been reconfigured correctly, if so, can trigger to the new routing weights
        if (this.targetDeviceIdsBeingReconfigured.isEmpty()) {
            // trigger a shift from the intermediate routing weights during reconfiguration to the new set of routing weights
            this.currentInterpodRoutingWeights = this.afterReconfigurationInterpodRoutingWeights;
            assert(this.afterReconfigurationInterpodRoutingWeights != null);
            this.afterReconfigurationInterpodRoutingWeights = null;
            // Go in and reset the load balancers
            for (int targetPodId : this.loadBalancers.keySet()) {
                LoadBalancer lb = this.loadBalancers.get(targetPodId);
                lb.reset(this.currentInterpodRoutingWeights.get(targetPodId));
            }
        }
    }

    /**
     * Returns the pod ID that this device belongs to. 
     *
     * @return The pod id this switch/server belongs to.
     */
    public int getPodId() {
        return this.podId;
    }

    /**
     * Checks whether if this switch is an aggregation switch
     *
     * @return True if this switch is an aggregation switch (connects with switches outside of this pod), and false otherwise.
     */
    public boolean isAggregation() {
        return this.isAggregation;
    }

    /**
     * Checks the head of each of the input queue, and determine whether if any of them are meant to be pushed onto
     * the output port's output queue if the output port is connected to upstreamSwitchId. Called by the output port
     * when a packet has been popped from the output port and is being sent, to call the network switch to check if
     * the head of any packets in all input ports are to be queued.
     * 
     * @param upstreamSwitchId      The id of the upstream network switch.
     */ 
    public void popInputQueue(int upstreamSwitchId, long remainingBufferSizeBits) {
        if (remainingBufferSizeBits <= 0) {
            return;
        }
        // If this is a server, rather than looking at the input queue, which is always empty, 
        // we just trigger the transport layer to send more packets.
        if (this.isServer()) {
            InfinibandTransportLayer transportLayerIB = (InfinibandTransportLayer) getTransportLayer();
            // Trigger the transport layer will arbitrate which flow gets to get packet during this iteration.
            transportLayerIB.arbitrateFlows(remainingBufferSizeBits);
            return;
        }

        
        PriorityQueue<InfinibandPacket> pq = new PriorityQueue<>(new PacketArrivalComparator()); 
        for (Map.Entry<Integer, Queue<Packet>> entry : this.inputQueues.entrySet()) {
            Queue<Packet> currentInputQueue = entry.getValue();
            if (!currentInputQueue.isEmpty()) {
                InfinibandPacket headPacket = (InfinibandPacket) currentInputQueue.peek();
                // check and see whether if this packet is meant for the current output port
                long flowId = headPacket.getFlowId();
                int srcId = headPacket.getSourceId(), dstId = headPacket.getDestinationId();
                int srcPodId = deviceIdToPodId.get(srcId), dstPodId = deviceIdToPodId.get(dstId);
                int nextHopId = routePacket(headPacket, flowId, srcPodId, dstId, dstPodId);
                assert(nextHopId >= 0);
                // The the next hop id is the same as the upstream switch id, then push it into the priority queue
                if (nextHopId == upstreamSwitchId) {
                    pq.add(headPacket);
                }
            }
        }
        ReconfigurableInfinibandOutputPort outport = (ReconfigurableInfinibandOutputPort) this.targetIdToOutputPort.get(upstreamSwitchId);
        while (!pq.isEmpty()) {
            InfinibandPacket peekPacket = pq.peek();
            int downstreamSwitchId = peekPacket.getPreviousSwitchId();
            if (peekPacket.getSizeBit() > remainingBufferSizeBits) {
                break;
            } else {
                remainingBufferSizeBits -= peekPacket.getSizeBit();
                // Enqueue the packet in the output port.
                outport.enqueue(peekPacket);
                // pop the packet from the head of the input queue.
                this.inputQueues.get(downstreamSwitchId).poll();
                // pop the packet from the priority queue.
                pq.poll();
                long currentInputQueueSize = this.inputQueuesCurrentRemainingSizeBits.get(downstreamSwitchId);
                long newInputQueueSize = currentInputQueueSize + peekPacket.getSizeBit();
                assert(newInputQueueSize <= inputQueueBufferMaxSizeBits);
                this.inputQueuesCurrentRemainingSizeBits.put(downstreamSwitchId, newInputQueueSize);
                // go back to each of the output ports in the downstream switch and actually update the credits 
                ReconfigurableInfinibandOutputPort downstreamOutport = downstreamOutputPorts.get(downstreamSwitchId);
                downstreamOutport.incrementCredit(peekPacket.getSizeBit()); // Only increment the credit once a packet has been taken off the input queue.
            }
        }
    }


    /**
     * Given that the current device is a server, queries the output port's queue buffer space that is
     * currently occupied by packets. This is called by the infiniband transport layer to determine
     * whether if there is enough space in the output port to hold additional packets.
     */
    @Override
    public long queryServerInjectionPortBufferSizeBits() {
        assert(isServer());
        assert(connectedTo.size() == 1);
        ReconfigurableInfinibandOutputPort injPort = (ReconfigurableInfinibandOutputPort) targetIdToOutputPort.get(connectedTo.get(0));
        long remainingBufferOccupiedBits = injPort.getRemainingBufferSizeBits();
        assert(remainingBufferOccupiedBits >= 0);
        return remainingBufferOccupiedBits;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Reconfiguration Infiniband Switch<id=");
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
