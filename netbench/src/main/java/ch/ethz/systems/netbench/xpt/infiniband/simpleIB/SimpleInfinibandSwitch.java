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

// Used for priority queue when sorting which packets should go first based on how long they have been born in the network.
class PacketArrivalComparator implements Comparator<AbstractMap.SimpleEntry<Integer, Packet>>{ 
    // Overriding compare()method of Comparator  
    // for descending order of cgpa 
    public int compare(AbstractMap.SimpleEntry<Integer, Packet> p1, AbstractMap.SimpleEntry<Integer, Packet> p2) { 
        Packet packet1 = p1.getValue();
        Packet packet2 = p2.getValue();
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
public class SimpleInfinibandSwitch extends NetworkDevice implements InfinibandSwitchInterface {

    // Routing table
    protected final HashMap<Integer, ArrayList<Integer>> destinationToNextSwitchId;

    // Cached routing table for flows
    private HashMap<Long, Integer> cachedFlowRoutingTable;

    // Records the output port belonging to each of the switches in the downstream direction
    // i.e. switches that have output ports sending to this current switch.
    protected final HashMap<Integer, SimpleInfinibandOutputPort> downstreamOutputPorts;  

    // Input queue
    protected final HashMap<Integer, Queue<Packet>> inputQueues; 
    protected final HashMap<Integer, Long> inputQueuesCurrentRemainingSizeBits; 

    protected final long inputBufferMaxSizeBits;

    private Random rng; 

    /**
     * Constructor of an infiniband network device.
     *
     * @param identifier        Network device identifier
     * @param transportLayer    Transport layer instance (null, if only router and not a server)
     * @param intermediary      Flowlet intermediary instance (takes care of flowlet support)
     */
    public SimpleInfinibandSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, long inputBufferMaxSizeBits) {
        super(identifier, transportLayer, intermediary);
        this.inputQueues = new HashMap<>();
        this.downstreamOutputPorts = new HashMap<>();
        this.destinationToNextSwitchId = new HashMap<>();
        this.cachedFlowRoutingTable = new HashMap<>();
        this.rng = new Random();
        this.inputBufferMaxSizeBits = inputBufferMaxSizeBits;
        this.inputQueuesCurrentRemainingSizeBits = new HashMap<>();
        if (transportLayer != null) {
            ((InfinibandTransportLayer) transportLayer).setUnderlyingDevice(this);
        }
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
        SimpleInfinibandOutputPort injPort = (SimpleInfinibandOutputPort) targetIdToOutputPort.get(connectedTo.get(0));
        long remainingBufferOccupiedBits = injPort.getMaxBufferSizeBits() - injPort.getBufferOccupiedBits();
        assert(remainingBufferOccupiedBits >= 0);
        return remainingBufferOccupiedBits;
    }

    public void addDestinationToNextSwitch(int destinationId, int nextHopId) {
        // Check for not possible identifier
        if (!connectedTo.contains(nextHopId)) {
            throw new IllegalArgumentException("Cannot add hop to a network device to which it is not connected (" + nextHopId + ")");
        }

        // Check for duplicate
        ArrayList<Integer> current = this.destinationToNextSwitchId.get(destinationId);
        if (current == null) {
            current = new ArrayList<Integer>();
            this.destinationToNextSwitchId.put(destinationId, current);
        }
        if (current.contains(nextHopId)) {
            throw new IllegalArgumentException("Cannot add a duplicate next hop network device identifier (" + nextHopId + ")");
        }

        // Add to current ones
        current.add(nextHopId);
    }

    public void setDestinationToNextSwitch(int destinationId, int nextHopId) {
        // Check for not possible identifier
        if (!connectedTo.contains(nextHopId)) {
            throw new IllegalArgumentException("Cannot add hop to a network device to which it is not connected (" + nextHopId + ")");
        }

        // Check for duplicate
        ArrayList<Integer> current = this.destinationToNextSwitchId.get(destinationId);
        if (current == null) {
            current = new ArrayList<Integer>();
            this.destinationToNextSwitchId.put(destinationId, current);
        }
        if (!current.isEmpty()) {
            throw new IllegalArgumentException("Cannot add a duplicate next hop network device identifier (" + nextHopId + ")");
        }

        // Add to current ones
        current.add(nextHopId);
    }

    /**
     * Called by the downstream switch of the current switch (i.e. downstream switch is one which 
     * has an output port leading to this switch).
     *
     * @param outputPort    The output port of the downstream switch
     *
     */
    private void addDownstreamOutputPort(SimpleInfinibandOutputPort outputPort) {
        int downstreamSwitchId = outputPort.getOwnId();
        assert(identifier == outputPort.getTargetId());
        assert(!this.downstreamOutputPorts.containsKey(downstreamSwitchId));
        assert(!this.inputQueues.containsKey(downstreamSwitchId));
        this.downstreamOutputPorts.put(downstreamSwitchId, outputPort);
        this.inputQueues.put(downstreamSwitchId, new LinkedList<Packet>());
        this.inputQueuesCurrentRemainingSizeBits.put(downstreamSwitchId, this.inputBufferMaxSizeBits);
        outputPort.incrementCredit(this.inputBufferMaxSizeBits);
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
        SimpleInfinibandSwitch targetSwitch = (SimpleInfinibandSwitch) outputPort.getTargetDevice();
        targetSwitch.addDownstreamOutputPort((SimpleInfinibandOutputPort) outputPort);
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
         * Part 1
         * Make the routing decisions first.
         */
        // Check first whether if the current id corresponds to the destination id.
        SimpleInfinibandOutputPort outputPort = null;
        if (dstId == this.identifier) {
            // send it to the connected server if we've reached the ToR connected to dstID
            int prevSwitchId = infinibandPacket.getPreviousSwitchId();
            infinibandPacket.setCurrentHopSwitchId(this.identifier);
            this.passToIntermediary(genericPacket); 
            // System.out.println("Server " + identifier + " has " + this.downstreamOutputPorts.size() + " downstream ports. Packet's previous hop id is: " + prevSwitchId);
            this.downstreamOutputPorts.get(prevSwitchId).incrementCredit(infinibandPacket.getSizeBit());
            return;
        } else {
            // Need to make the routing decision.
            int nextHopId = this.routingDecisionForPacket(infinibandPacket.getFlowId(), dstId);
            outputPort = (SimpleInfinibandOutputPort) this.targetIdToOutputPort.get(nextHopId);
        }

        /*
         * Part 2
         * Check whether if the output port's queue is full. If so, then queue at the input switches, else
         * guaranteeEnqueue at the output port.
         */
        int prevSwitchId = infinibandPacket.getPreviousSwitchId();
        assert(prevSwitchId >= 0);
        // Check whether if there is sufficient buffer space in the output port to handle this packet.
        if (outputPort.getBufferOccupiedBits() + infinibandPacket.getSizeBit() 
                <= outputPort.getMaxBufferSizeBits()) {
            // If yes, just enqueue said packet, and then 
            outputPort.enqueue(genericPacket);
            // Since we are not taking packets off the queue, need to incrementCredit for the downstream output port
            // as well.
            long deltaCredit = infinibandPacket.getSizeBit();
            this.downstreamOutputPorts.get(prevSwitchId).incrementCredit(deltaCredit);
        } else {
            // Put in the input queue.
            // Update the input port queue by decrementing the input queue's size
            long currentInputQueueSize = this.inputQueuesCurrentRemainingSizeBits.get(prevSwitchId);
            long newInputQueueSize = currentInputQueueSize - infinibandPacket.getSizeBit();
            if (newInputQueueSize < 0) {
                System.out.println("Current switch: " + identifier + " received packet from: " + prevSwitchId);
                throw new IllegalStateException("Received a packet despite insufficient input queue buffer space.");
            }
            this.inputQueuesCurrentRemainingSizeBits.put(prevSwitchId, newInputQueueSize);
            inputQueues.get(prevSwitchId).add(infinibandPacket);
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
        SimpleInfinibandOutputPort ibOutputPort = (SimpleInfinibandOutputPort) targetIdToOutputPort.get(nextSwitchId);
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
    public void popInputQueue(int upstreamSwitchId) {
        // If this is a server, rather than looking at the input queue, which is always empty, 
        // we just trigger the transport layer to send more packets.
        if (this.isServer()) {
            InfinibandTransportLayer transportLayerIB = (InfinibandTransportLayer) getTransportLayer();
            // Trigger the transport layer will arbitrate which flow gets to get packet during this iteration.
            // transportLayerIB.receive(null);
            TransportLayer selfTransportLayer = getTransportLayer();
            // Compute the remaining number of bits in the output port's buffer.
            assert(connectedTo.size() == 1);
            int nextSwitchId = connectedTo.get(0);
            SimpleInfinibandOutputPort ibOutputPort = (SimpleInfinibandOutputPort) targetIdToOutputPort.get(nextSwitchId);
            long remainingBufferSizeBits = ibOutputPort.getMaxBufferSizeBits() - ibOutputPort.getBufferOccupiedBits();
            // Signal transport layer to do something with the empty queues
            transportLayerIB.arbitrateFlows(remainingBufferSizeBits);
            return;
        }


        PriorityQueue<AbstractMap.SimpleEntry<Integer, Packet>> pq = new PriorityQueue<>(new PacketArrivalComparator()); 
        for (Map.Entry<Integer, Queue<Packet>> entry : this.inputQueues.entrySet()) {
            Queue<Packet> currentInputQueue = entry.getValue();
            if (!currentInputQueue.isEmpty()) {
                InfinibandPacket headPacket = (InfinibandPacket) currentInputQueue.peek();
                // check and see whether if this packet is meant for the current output port
                int nextHopId = routingDecisionForPacket(headPacket.getFlowId(), headPacket.getDestinationId());
                assert(nextHopId >= 0);
                // The the next hop id is the same as the upstream switch id, then push it into the priority queue
                if (nextHopId == upstreamSwitchId) {
                    pq.add(new AbstractMap.SimpleEntry<Integer, Packet>(entry.getKey(), headPacket));
                }
            }
        }
        SimpleInfinibandOutputPort outport = (SimpleInfinibandOutputPort) this.targetIdToOutputPort.get(upstreamSwitchId);
        long maxBufferSizeBits = outport.getMaxBufferSizeBits();
        while (!pq.isEmpty()) {
            long currentBufferSizeBit = outport.getBufferOccupiedBits();
            Packet peekPacket = pq.peek().getValue();
            int downstreamSwitchId = pq.peek().getKey();
            if (currentBufferSizeBit + peekPacket.getSizeBit() > maxBufferSizeBits) {
                break;
            } else {
                // guarantee enqueue
                outport.enqueue(peekPacket);
                // pop the packet from the head of the input queue
                this.inputQueues.get(downstreamSwitchId).poll();
                // pop the packet from the priority queue
                pq.poll();
                long currentInputQueueSize = this.inputQueuesCurrentRemainingSizeBits.get(downstreamSwitchId);
                long newInputQueueSize = currentInputQueueSize + peekPacket.getSizeBit();
                this.inputQueuesCurrentRemainingSizeBits.put(downstreamSwitchId, newInputQueueSize);
                // go back to each of the output ports in the downstream switch and actually update the credits 
                SimpleInfinibandOutputPort downstreamOutport = downstreamOutputPorts.get(downstreamSwitchId);
                downstreamOutport.incrementCredit(peekPacket.getSizeBit()); // Only increment the credit once a packet has been taken off the input queue.
            }
        }
    }
}
