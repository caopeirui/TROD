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
import java.util.concurrent.LinkedBlockingQueue;
import org.javatuples.Triplet; 

// Used for priority queue when sorting which packets should go first based on how long they have been born in the network.
class PacketArrivalVCComparator implements Comparator<Triplet<Integer, Integer, Packet>>{ 
    // Overriding compare()method of Comparator  
    // for descending order of cgpa 
    public int compare(Triplet<Integer, Integer, Packet> p1, Triplet<Integer, Integer, Packet> p2) { 
        Packet packet1 = p1.getValue2();
        Packet packet2 = p2.getValue2();
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
 * NOTE: Unlike ReconfigurableInfinibandSwitch, this class can support more than 1 virtual channels in order to prevent
 *          deadlocking.
 */
public class ReconfigurableInfinibandVCSwitch extends ReconfigurableInfinibandSwitch {

    // Input queues for backpressure and VC
    private final int numVCs;
    private final long inputQueueBufferMaxSizeBits;
    private final long inputQueueBufferMaxSizeBitsPerVC;
    private final Map<Integer, Queue<Packet>[]> inputQueues;                              // Contains the input packet buffer
    private final HashMap<Integer, Long[]> inputQueuesCurrentRemainingSizeBits; 
    private final HashMap<Integer, ReconfigurableInfinibandVCOutputPort> downstreamOutputPorts; // Contains references to the downstream output ports, indexed by the owning switch id of the downstream switch


    private final Map<Long, Set<Long>> packetsWithModifiedVC;                // Remembers the packets based on flowId and Sequence id, so that won't update VC information twice when calling routePacket repeatedly

    /**
     * Constructor for ECMP switch.
     *
     * @param identifier        Network device identifier
     * @param transportLayer    Underlying server transport layer instance (set null, if none)
     * @param intermediary      Flowlet intermediary instance (takes care of hash adaptation for flowlet support)
     * @param podIdArg          The pod that this device belongs to
     */
    public ReconfigurableInfinibandVCSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, int podIdArg, long inputQueueBufferMaxSizeBits, boolean statefulLoadBalancer, boolean usePacketSpraying, int numVCs) {
        super(identifier, transportLayer, intermediary, podIdArg, inputQueueBufferMaxSizeBits, statefulLoadBalancer, usePacketSpraying);
        assert(numVCs > 0);

        // Infiniband backpressure related.
        this.inputQueueBufferMaxSizeBits = inputQueueBufferMaxSizeBits;
        this.inputQueueBufferMaxSizeBitsPerVC = inputQueueBufferMaxSizeBits / numVCs;
        this.inputQueuesCurrentRemainingSizeBits = new HashMap<>();
        this.downstreamOutputPorts = new HashMap<>();
        this.inputQueues = new HashMap<>();

        // VCs
        this.numVCs = numVCs;
        this.packetsWithModifiedVC = new HashMap<>();
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
        ReconfigurableInfinibandVCSwitch targetInfinibandSwitch = (ReconfigurableInfinibandVCSwitch) outputPort.getTargetDevice();
        targetInfinibandSwitch.addDownstreamOutputPort((ReconfigurableInfinibandVCOutputPort) outputPort);

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
    private void addDownstreamOutputPort(ReconfigurableInfinibandVCOutputPort outputPort) {
        int downstreamSwitchId = outputPort.getOwnId();
        assert(identifier == outputPort.getTargetId());
        assert(!this.downstreamOutputPorts.containsKey(downstreamSwitchId));
        assert(!this.inputQueues.containsKey(downstreamSwitchId));
        this.downstreamOutputPorts.put(downstreamSwitchId, outputPort);
        this.inputQueues.put(downstreamSwitchId, new LinkedBlockingQueue[numVCs]);
        this.inputQueuesCurrentRemainingSizeBits.put(downstreamSwitchId, new Long[numVCs]);
        // Increment credit to all of the vcs and initialize
        for (int channel = 0; channel < numVCs; channel++) {
            this.inputQueues.get(downstreamSwitchId)[channel] = new LinkedBlockingQueue<Packet>();
            this.inputQueuesCurrentRemainingSizeBits.get(downstreamSwitchId)[channel] = this.inputQueueBufferMaxSizeBitsPerVC;
            outputPort.incrementCredit(this.inputQueueBufferMaxSizeBitsPerVC, channel);
        }
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
     * NOTE: This method could change the internal state of ibPacket, which specifically means that the
     *          channel of the packet might be changed. So the caller method needs to keep track of the original VC
     *          ibPacket belongs to before calling this method.
     * 
     * @param flowId                    The flow id this packet belongs to.
     * @param srcPodId                  The source pod id this packet started from.
     * @param dstId                     The destination id of this packet.
     * @param dstPodId                  The pod id of the destination switch.
     *
     * @return The next hop switch id.
     **/
    private int routePacket(InfinibandPacket ibPacket, long flowId, int srcPodId, int dstId, int dstPodId) {
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
                this.loadBalancers.get(dstPodId).logPathTaken(new Path(interpodPathList), ibPacket.getSizeBit() / 8L);
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

            // Increment the vc to prevent deadlocking, only if the method is allowed to change the state
            if (!packetsWithModifiedVC.containsKey(flowId)) {
                packetsWithModifiedVC.put(flowId, new HashSet<Long>());
                packetsWithModifiedVC.get(flowId).add(ibPacket.getSequenceNumber());
                ibPacket.setVC(ibPacket.getVC() + 1);
            } else if (!packetsWithModifiedVC.get(flowId).contains(ibPacket.getSequenceNumber())) {
                packetsWithModifiedVC.get(flowId).add(ibPacket.getSequenceNumber());
                ibPacket.setVC(ibPacket.getVC() + 1);
            }
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
        long packetSizeBits = ibPacket.getSizeBit();
        int prevSwitchId = ibPacket.getPreviousSwitchId();
        int srcPodId = deviceIdToPodId.get(srcId);
        int dstPodId = deviceIdToPodId.get(dstId);
        

        // First, save the virtual channel of the packet the moment it arrives at the current switch, as routePacket()
        // may change the virtual channel.
        int previousVC = ibPacket.getVC();

        // Pass to transport layer if we've reached the destination
        if (identifier == dstId) {
            // Need to increment the downstream output port's credit, otherwise it will eventually run out of credits.
            // This is because when a packet has reached its destination, it is immediately absorbed by server and does not take
            // any space on the input port.
            downstreamOutputPorts.get(prevSwitchId).incrementCredit(packetSizeBits, previousVC);
            passToIntermediary(ibPacket);
            return;
        }

        // Come up with a routing decision for this packet, and then two cases:
        //  1) If the output port can hold more packets, then queue this packet in the output port's queue, and increment credit for downstream port
        //  2) else, place it in the input queue.
        int nextHopSwitchId = routePacket(ibPacket, flowId, srcPodId, dstId, dstPodId);
        assert(nextHopSwitchId >= 0 && targetIdToOutputPort.containsKey(nextHopSwitchId));

        // TODO( ): Do we need to check multiplicity?
        ReconfigurableInfinibandVCOutputPort outport = (ReconfigurableInfinibandVCOutputPort) targetIdToOutputPort.get(nextHopSwitchId);
        
        assert(this.downstreamOutputPorts.containsKey(prevSwitchId));
        if (outport != null && outport.getRemainingVCBufferBits(ibPacket.getVC()) >= packetSizeBits) {
            // When there is enough room in output port's queue, place it there
            outport.enqueue(ibPacket);
            // increment the credits in the downstream switch, for the VC when the packet enters, not the current VC
            downstreamOutputPorts.get(prevSwitchId).incrementCredit(packetSizeBits, previousVC);
        } else {
            // Not enough buffer space at the output port's queue, place packet in the input queue instead.
            inputQueues.get(prevSwitchId)[previousVC].add(ibPacket);
            // Update the remaining buffer space bits of the input queue
            long previousRemainingBufferSize = inputQueuesCurrentRemainingSizeBits.get(prevSwitchId)[previousVC];
            inputQueuesCurrentRemainingSizeBits.get(prevSwitchId)[previousVC] -= ibPacket.getSizeBit();
            assert(inputQueuesCurrentRemainingSizeBits.get(prevSwitchId)[previousVC] == previousRemainingBufferSize - ibPacket.getSizeBit());
            assert(inputQueuesCurrentRemainingSizeBits.get(prevSwitchId)[previousVC] >= 0);
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
        InfinibandPacket ibPacket = (InfinibandPacket) genericPacket;
        int nextSwitchId = connectedTo.get(0);
        ReconfigurableInfinibandVCOutputPort ibOutputPort = (ReconfigurableInfinibandVCOutputPort) targetIdToOutputPort.get(nextSwitchId);
        // Check the output port and make sure that there is actually enough credits
        assert(genericPacket.getSizeBit() <= ibOutputPort.getRemainingVCBufferBits(ibPacket.getVC()));
        ibOutputPort.enqueue(genericPacket);
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
            ReconfigurableInfinibandVCOutputPort reconfigurableOutputPort = (ReconfigurableInfinibandVCOutputPort) this.targetIdToOutputPort.get(targetSwitchId);
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
     * Checks the head of each of the input queue, and determine whether if any of them are meant to be pushed onto
     * the output port's output queue if the output port is connected to upstreamSwitchId. Called by the output port
     * when a packet has been popped from the output port and is being sent, to call the network switch to check if
     * the head of any packets in all input ports are to be queued.
     * 
     * @param upstreamSwitchId      The id of the upstream network switch.
     */ 
    public void popInputQueue(int upstreamSwitchId, long remainingBufferSizeBits, int vc) {
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

        PriorityQueue<Triplet<Integer, Integer, Packet>> pq = new PriorityQueue<>(new PacketArrivalVCComparator()); 
        for (Map.Entry<Integer, Queue<Packet>[]> entry : this.inputQueues.entrySet()) {
            Queue<Packet>[] currentInputQueue = entry.getValue();
            for (int channel = 0; channel < numVCs; channel++) {
                if (!currentInputQueue[channel].isEmpty()) {
                    InfinibandPacket headPacket = (InfinibandPacket) currentInputQueue[channel].peek();
                    // check and see whether if this packet is meant for the current output port
                    long flowId = headPacket.getFlowId();
                    int srcId = headPacket.getSourceId(), dstId = headPacket.getDestinationId();
                    int srcPodId = deviceIdToPodId.get(srcId), dstPodId = deviceIdToPodId.get(dstId);
                    int nextHopId = routePacket(headPacket, flowId, srcPodId, dstId, dstPodId);
                    assert(nextHopId >= 0);
                    // The the next hop id is the same as the upstream switch id, then push it into the priority queue
                    if (nextHopId == upstreamSwitchId && headPacket.getVC() == vc) {
                        pq.add(new Triplet<>(entry.getKey(), channel, headPacket));
                    }
                }
            }
        }
        ReconfigurableInfinibandVCOutputPort outport = (ReconfigurableInfinibandVCOutputPort) this.targetIdToOutputPort.get(upstreamSwitchId);
        while (!pq.isEmpty()) {
            Triplet<Integer, Integer, Packet> entryTriplet = pq.peek();
            InfinibandPacket peekPacket = (InfinibandPacket) entryTriplet.getValue2();
            int originalVC = entryTriplet.getValue1();
            int downstreamSwitchId = peekPacket.getPreviousSwitchId();
            assert(downstreamSwitchId == entryTriplet.getValue0());
            if (peekPacket.getSizeBit() > remainingBufferSizeBits) {
                break;
            } else {
                remainingBufferSizeBits -= peekPacket.getSizeBit();
                assert(remainingBufferSizeBits >= 0);
                // Enqueue the packet in the output port.
                outport.enqueue(peekPacket);
                // pop the packet from the head of the input queue.
                this.inputQueues.get(downstreamSwitchId)[originalVC].poll();
                // pop the packet from the priority queue.
                pq.poll();
                this.inputQueuesCurrentRemainingSizeBits.get(downstreamSwitchId)[originalVC] += peekPacket.getSizeBit();
                assert(this.inputQueuesCurrentRemainingSizeBits.get(downstreamSwitchId)[originalVC] <= inputQueueBufferMaxSizeBitsPerVC);
                // go back to each of the output ports in the downstream switch and actually update the credits 
                ReconfigurableInfinibandVCOutputPort downstreamOutport = downstreamOutputPorts.get(downstreamSwitchId);
                downstreamOutport.incrementCredit(peekPacket.getSizeBit(), originalVC); // Only increment the credit once a packet has been taken off the input queue.
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
        ReconfigurableInfinibandVCOutputPort injPort = (ReconfigurableInfinibandVCOutputPort) targetIdToOutputPort.get(connectedTo.get(0));
        long remainingBufferOccupiedBits = injPort.getRemainingVCBufferBits(0);
        // System.out.println("weeeeee : " + identifier + " with remaining buffer occupied bits: " + remainingBufferOccupiedBits);
        assert(remainingBufferOccupiedBits >= 0);
        return remainingBufferOccupiedBits;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Reconfiguration Infiniband VC Switch<id=");
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
