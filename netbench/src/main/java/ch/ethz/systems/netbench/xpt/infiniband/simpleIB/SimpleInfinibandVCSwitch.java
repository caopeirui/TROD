package ch.ethz.systems.netbench.xpt.infiniband.simpleIB;

import ch.ethz.systems.netbench.xpt.infiniband.InfinibandTransportLayer;
import ch.ethz.systems.netbench.xpt.infiniband.InfinibandPacket;
import ch.ethz.systems.netbench.xpt.infiniband.InfinibandHeader;
import ch.ethz.systems.netbench.xpt.infiniband.InfinibandSwitchInterface;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.core.network.Packet;
import ch.ethz.systems.netbench.core.network.Intermediary;

// Tools
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
 * Abstraction for a network device.
 *
 * All nodes in the network are instances of this abstraction.
 * It takes care of the definition of network connections and
 * forces its subclasses to be able to handle packets it receives.
 * A network device is a server iff it has a
 * {@link TransportLayer transport layer}.
 *
 * It enables additional modification of packets by placement of a
 * {@link Intermediary intermediary}
 * in between the network device and the transport layer.
 */
public class SimpleInfinibandVCSwitch extends SimpleInfinibandSwitch {

    // Cached routing table for flows
    private HashMap<Long, Integer> cachedFlowRoutingTable;

    // Records the output port belonging to each of the switches in the downstream direction
    // i.e. switches that have output ports sending to this current switch.
    protected final HashMap<Integer, SimpleInfinibandVCOutputPort> downstreamOutputPorts;  

    // Input queue
    protected final HashMap<Integer, Queue<Packet>[]> inputQueues; 
    protected final HashMap<Integer, Long[]> inputQueuesCurrentRemainingSizeBits; 

    protected final long inputBufferMaxSizeBits;        // The total buffer queue size in bits for the input ports
    protected final long inputBufferMaxSizeBitsPerVC;   // The total buffer queue size in bits for a virtual channel, such that it is = inputBufferMaxSizeBits/numVCs

    private final int numVCs;                           // The number of virtual channels

    private Random rng; 

    /**
     * Constructor of an infiniband network device.
     *
     * @param identifier        Network device identifier
     * @param transportLayer    Transport layer instance (null, if only router and not a server)
     * @param intermediary      Flowlet intermediary instance (takes care of flowlet support)
     */
    public SimpleInfinibandVCSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, long inputBufferMaxSizeBits, int numVCs) {
        super(identifier, transportLayer, intermediary, inputBufferMaxSizeBits);
        assert(numVCs > 1);
        this.downstreamOutputPorts = new HashMap<>();
        this.cachedFlowRoutingTable = new HashMap<>();
        this.rng = new Random();
        this.numVCs = numVCs;
        this.inputBufferMaxSizeBits = inputBufferMaxSizeBits;
        this.inputBufferMaxSizeBitsPerVC = inputBufferMaxSizeBits / numVCs;
        this.inputQueues = new HashMap<>();
        this.inputQueuesCurrentRemainingSizeBits = new HashMap<>();
    }

    /**
     * Given that the current device is a server, queries the output port's queue buffer space that is
     * currently occupied by packets. This is called by the infiniband transport layer to determine
     * whether if there is enough space in the output port to hold additional packets.
     */
    @Override
    public long queryServerInjectionPortBufferSizeBits() {
        //System.out.println("Calling injection port buffer size at server id:  " + identifier);
        assert(isServer());
        assert(connectedTo.size() == 1);
        SimpleInfinibandVCOutputPort injPort = (SimpleInfinibandVCOutputPort) targetIdToOutputPort.get(connectedTo.get(0));
        long remainingBufferOccupiedBits = injPort.getRemainingVCBufferBits(0);
        return remainingBufferOccupiedBits;
    }

    /**
     * Called by the downstream switch of the current switch (i.e. downstream switch is one which 
     * has an output port leading to this switch).
     *
     * @param outputPort    The output port of the downstream switch
     *
     */
    private void addDownstreamOutputPort(SimpleInfinibandVCOutputPort outputPort) {
        int downstreamSwitchId = outputPort.getOwnId();
        assert(identifier == outputPort.getTargetId());
        assert(!this.downstreamOutputPorts.containsKey(downstreamSwitchId));
        assert(!this.inputQueues.containsKey(downstreamSwitchId));
        this.downstreamOutputPorts.put(downstreamSwitchId, outputPort);
        LinkedBlockingQueue<Packet>[] virtualInputQueues = new LinkedBlockingQueue[numVCs];
        
        this.inputQueuesCurrentRemainingSizeBits.put(downstreamSwitchId, new Long[numVCs]);
        for (int channel = 0; channel < numVCs; channel++) {
            outputPort.incrementCredit(this.inputBufferMaxSizeBitsPerVC, channel);
            this.inputQueuesCurrentRemainingSizeBits.get(downstreamSwitchId)[channel] = this.inputBufferMaxSizeBitsPerVC;
            virtualInputQueues[channel] = new LinkedBlockingQueue<>();
        }
        this.inputQueues.put(downstreamSwitchId, virtualInputQueues);
    }

    /**
     * Reception of a packet by the network device from the underlying transport layer.
     * Adapts it via the intermediary and then sends it on to the switch.
     * Do not override. // TODO: make it package-local?
     *
     * @param genericPacket    Packet instance
     */
    @Override
    public void receiveFromTransportLayer(Packet genericPacket) {
        this.receiveFromIntermediary(genericPacket);
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

        // Port going there already exists
        if (connectedTo.contains(outputPort.getTargetId())) {
            throw new IllegalArgumentException("Impossible to add a duplicate port from " + outputPort.getOwnId() + " to " + outputPort.getTargetId() + ".");
        }

        // Add to mappings
        connectedTo.add(outputPort.getTargetId());
        targetIdToOutputPort.put(outputPort.getTargetId(), outputPort);

        // In the target switch, add the input queues and also set its downstream output port
        SimpleInfinibandVCSwitch targetSwitch = (SimpleInfinibandVCSwitch) outputPort.getTargetDevice();
        targetSwitch.addDownstreamOutputPort((SimpleInfinibandVCOutputPort) outputPort);
    }

    /**
     * Given a destination id and the flow id a packet belongs to, figure out what is the
     * next hop switch's id.
     * 
     * @param flowId    Identifier of the flow
     * @param dstId     Destination identifier
     * 
     * @return Returns the next hop switch id for a given packet. 
     */
    private int routingDecisionForPacket(long flowId, int dstId) {
        int nextHopId;
        if (cachedFlowRoutingTable.containsKey(flowId)) {
            nextHopId = cachedFlowRoutingTable.get(flowId);
        } else {
            ArrayList<Integer> possibleNextSteps = this.destinationToNextSwitchId.get(dstId);
            if (possibleNextSteps.isEmpty()) {
                throw new IllegalStateException("No routing options possible.");
            } else if (possibleNextSteps.size() == 1) {
                nextHopId = possibleNextSteps.get(0);
                this.cachedFlowRoutingTable.put(flowId, nextHopId);    
            } else {
                int index = this.rng.nextInt(possibleNextSteps.size());
                nextHopId = possibleNextSteps.get(index);
                this.cachedFlowRoutingTable.put(flowId, nextHopId);    
            }
        }
        return nextHopId;
    }

    @Override
    public void receive(Packet genericPacket) {
        // Convert to Infiniband packet
        InfinibandPacket infinibandPacket = (InfinibandPacket) genericPacket;
        int dstId = infinibandPacket.getDestinationId();
        int srcId = infinibandPacket.getSourceId();

        /*
         * Part 1.
         * Make the routing decisions first.
         */
        // Check first whether if the current id corresponds to the destination id.
        SimpleInfinibandVCOutputPort outputPort = null;
        int previousVC = infinibandPacket.getVC();
        if (dstId == this.identifier) {
            // send it to the connected server if we've reached the ToR connected to dstID
            int prevSwitchId = infinibandPacket.getPreviousSwitchId();
            infinibandPacket.setCurrentHopSwitchId(this.identifier);
            this.passToIntermediary(genericPacket); 
            // System.out.println("Server " + identifier + " has " + this.downstreamOutputPorts.size() + " downstream ports. Packet's previous hop id is: " + prevSwitchId);
            this.downstreamOutputPorts.get(prevSwitchId).incrementCredit(infinibandPacket.getSizeBit(), previousVC);
            return;
        } else {
            // Need to make the routing decision.
            int nextHopId = this.routingDecisionForPacket(infinibandPacket.getFlowId(), dstId);
            outputPort = (SimpleInfinibandVCOutputPort) this.targetIdToOutputPort.get(nextHopId);
        }

        /*
         * Part 2
         * Check whether if the output port's queue is full. If so, then queue at the input switches, else
         * guaranteeEnqueue at the output port.
         */
        int prevSwitchId = infinibandPacket.getPreviousSwitchId();
        assert(prevSwitchId >= 0);
        // Check whether if there is sufficient buffer space in the output port to handle this packet.
        if (infinibandPacket.getSizeBit() <= outputPort.getRemainingVCBufferBits(infinibandPacket.getVC())) {
            // If yes, just enqueue said packet, and then 
            outputPort.enqueue(genericPacket);
            // Since we are not taking packets off the queue, need to incrementCredit for the downstream output port
            // as well.
            this.downstreamOutputPorts.get(prevSwitchId).incrementCredit(infinibandPacket.getSizeBit(), previousVC);
        } else {
            // Put in the input queue.
            // Update the input port queue by decrementing the input queue's size
            long previousBufferSize = this.inputQueuesCurrentRemainingSizeBits.get(prevSwitchId)[previousVC];
            this.inputQueuesCurrentRemainingSizeBits.get(prevSwitchId)[previousVC] -= infinibandPacket.getSizeBit();
            assert(previousBufferSize - infinibandPacket.getSizeBit() == this.inputQueuesCurrentRemainingSizeBits.get(prevSwitchId)[previousVC]);
            if (this.inputQueuesCurrentRemainingSizeBits.get(prevSwitchId)[previousVC] < 0) {
                System.out.println("Current switch: " + identifier + " received packet from: " + prevSwitchId);
                throw new IllegalStateException("Received a packet despite insufficient input queue buffer space.");
            }
            inputQueues.get(prevSwitchId)[previousVC].add(infinibandPacket);
        }
        
    }

    /**
     * Receives a packet from intermediary, which in turn received from the transport layer.
     * When this function is called, it is assumed that there must be sufficient output port
     * queue space to queue this packet.
     */
    @Override
    public void receiveFromIntermediary(Packet genericPacket) {
        if (connectedTo.size() != 1) {
            throw new IllegalStateException("There has to be exactly one output port for each server.");
        }
        int nextSwitchId = connectedTo.get(0);
        SimpleInfinibandVCOutputPort ibOutputPort = (SimpleInfinibandVCOutputPort) targetIdToOutputPort.get(nextSwitchId);
        // Check the output port and make sure that there is actually enough credits
        assert(genericPacket.getSizeBit() + ibOutputPort.getBufferOccupiedBits() <= ibOutputPort.getMaxBufferSizeBits());
        ibOutputPort.enqueue(genericPacket);
    }

    /**
     * Checks the head of each of the input queue, and determine whether if any of them are meant to be pushed onto
     * the output port's output queue if the output port is connected to upstreamSwitchId. Called by the output port
     * when a packet has been popped from the output port and is being sent, to call the network switch to check if
     * the head of any packets in all input ports are to be queued.
     * 
     * @param upstreamSwitchId      The id of the upstream network switch.
     */ 
    public void popInputQueue(int upstreamSwitchId, int vc) {
        // If this is a server, rather than looking at the input queue, which is always empty, 
        // we just trigger the transport layer to send more packets.
        if (this.isServer()) {
            InfinibandTransportLayer transportLayerIB = (InfinibandTransportLayer) getTransportLayer();
            // Trigger the transport layer will arbitrate which flow gets to get packet during this iteration.
            // Compute the remaining number of bits in the output port's buffer.
            assert(connectedTo.size() == 1);
            int nextSwitchId = connectedTo.get(0);
            SimpleInfinibandVCOutputPort ibOutputPort = (SimpleInfinibandVCOutputPort) targetIdToOutputPort.get(nextSwitchId);
            long remainingBufferSizeBits = ibOutputPort.getRemainingVCBufferBits(vc);
            // Signal transport layer to do something with the empty queues
            transportLayerIB.arbitrateFlows(remainingBufferSizeBits);
            return;
        }


        PriorityQueue<Triplet<Integer, Integer, Packet>> pq = new PriorityQueue<>(new PacketArrivalVCComparator()); 
        for (Map.Entry<Integer, Queue<Packet>[]> entry : this.inputQueues.entrySet()) {
            Queue<Packet>[] currentInputQueues = entry.getValue();
            for (int channel = 0; channel < numVCs; channel++) {
                if (!currentInputQueues[channel].isEmpty()) {
                    InfinibandPacket headPacket = (InfinibandPacket) currentInputQueues[channel].peek();
                    // check and see whether if this packet is meant for the current output port
                    int nextHopId = routingDecisionForPacket(headPacket.getFlowId(), headPacket.getDestinationId());
                    assert(nextHopId >= 0);
                    // The the next hop id is the same as the upstream switch id, then push it into the priority queue
                    if (nextHopId == upstreamSwitchId && headPacket.getVC() == vc) {
                        pq.add(new Triplet<Integer, Integer, Packet>(entry.getKey(), channel, headPacket));
                    }
                }
            }
        }
        SimpleInfinibandVCOutputPort outport = (SimpleInfinibandVCOutputPort) this.targetIdToOutputPort.get(upstreamSwitchId);
        long remainingBufferSizeBits = outport.getRemainingVCBufferBits(vc);
        while (!pq.isEmpty()) {
            Triplet<Integer, Integer, Packet> currTriplet = pq.peek();
            Packet peekPacket = currTriplet.getValue2();
            int downstreamSwitchId = currTriplet.getValue0();
            int inputVC = currTriplet.getValue1();
            if (peekPacket.getSizeBit() > remainingBufferSizeBits) {
                break;
            } else {
                // guarantee enqueue
                outport.enqueue(peekPacket);
                // pop the packet from the head of the input queue
                this.inputQueues.get(downstreamSwitchId)[inputVC].poll();
                // pop the packet from the priority queue
                pq.poll();
                this.inputQueuesCurrentRemainingSizeBits.get(downstreamSwitchId)[inputVC] += peekPacket.getSizeBit();

                // Decrement the remaining bits in the output queue buffer.
                remainingBufferSizeBits -= peekPacket.getSizeBit();

                // go back to each of the output ports in the downstream switch and actually update the credits 
                SimpleInfinibandVCOutputPort downstreamOutport = downstreamOutputPorts.get(downstreamSwitchId);
                downstreamOutport.incrementCredit(peekPacket.getSizeBit(), inputVC); // Only increment the credit once a packet has been taken off the input queue.
            }
        }
    }
}
