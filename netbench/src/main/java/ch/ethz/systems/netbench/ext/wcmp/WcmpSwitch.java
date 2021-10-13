package ch.ethz.systems.netbench.ext.wcmp;

import ch.ethz.systems.netbench.core.network.*;
import ch.ethz.systems.netbench.ext.basic.TcpHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.*; 
import java.util.Random; // random number generator used to determining how to route traffic
/*
public class Pair<K, V> {

    private final K element0;
    private final V element1;

    public static <K, V> Pair<K, V> createPair(K element0, V element1) {
        return new Pair<K, V>(element0, element1);
    }

    public Pair(K element0, V element1) {
        this.element0 = element0;
        this.element1 = element1;
    }

    public K getElement0() {
        return element0;
    }

    public V getElement1() {
        return element1;
    }

}
*/

public class WcmpSwitch extends NetworkDevice implements WcmpSwitchRoutingInterface {

    // Routing table
    protected final List<List<Map.Entry<Integer, Double>>> destinationToNextSwitch;
    protected HashMap<Integer, Integer> serverToToRID; // this is used by receiveFromIntermediary() to encapsulate tcp packet, and search for dest block
    protected Random rng; 
    protected int serverID; // Used by ToRs that support a single server, and records its ID
    protected int torID; // Used by servers connected to a ToR, and records its ID


    protected HashMap<Long, Integer> flowIDToNextHop;
    /**
     * Constructor for ECMP switch.
     *
     * @param identifier        Network device identifier
     * @param transportLayer    Underlying server transport layer instance (set null, if none)
     * @param n                 Number of network devices in the entire network (for routing table size)
     * @param intermediary      Flowlet intermediary instance (takes care of hash adaptation for flowlet support)
     */
    public WcmpSwitch(int identifier, TransportLayer transportLayer, int n, Intermediary intermediary) {
        super(identifier, transportLayer, intermediary);
        this.destinationToNextSwitch = new ArrayList<>();
        this.serverToToRID = new HashMap<Integer, Integer>();
        this.flowIDToNextHop = new HashMap<Long, Integer>();
        for (int i = 0; i < n; i++) {
            this.destinationToNextSwitch.add(new ArrayList<>());
        }
        this.rng = new Random();
        this.serverID = -1;
        this.torID = -1;

    }


    /*
    @Override
    public void receive(Packet genericPacket) {
        // Convert to TCP packet
        TcpHeader tcpHeader = (TcpHeader) genericPacket;
        int dstId = tcpHeader.getDestinationId();
        int srcId = tcpHeader.getSourceId();

        if (this.isServer()) {
            // Check if it has arrived
            if (dstId == this.identifier) {
                // Hand to the underlying server
                this.passToIntermediary(genericPacket); 
            } else {
                this.targetIdToOutputPort.get(this.torID).enqueue(genericPacket);
            }
            return;
        } else {
            // If we get here, it means that this device is a switch, not a server
            if (dstId == this.serverID) {
                // send it to the connected server if we've reached the ToR connected to dstID
                this.targetIdToOutputPort.get(dstId).enqueue(genericPacket);
            // else, check if the current switch needs to make the right decision
            } else if (srcId == this.serverID) {
                // Forward to the next switch
                List<Map.Entry<Integer, Double>> possibilities = destinationToNextSwitch.get(dstId);
                // this.targetIdToOutputPort.get(possibilities.get(tcpHeader.getHash(this.identifier) % possibilities.size())).enqueue(genericPacket);
                // now need to generate a random number in range [0, 1)
                final double random_number = this.rng.nextDouble();
                double cumulative_weights = 0;
                int dest = -1;
                for (Map.Entry<Integer, Double> potential_dest : possibilities) {
                    double weight = potential_dest.getValue();
                    if (random_number >= cumulative_weights && random_number < cumulative_weights + weight) {
                        dest = potential_dest.getKey();
                        break;
                    }
                    cumulative_weights += weight;
                }
                if (dest < 0) {
                    if (possibilities.size() == 0) {
                        dest = dstId;
                    } else {
                        dest = possibilities.get(possibilities.size() - 1).getKey();
                    }
                }
                this.targetIdToOutputPort.get(dest).enqueue(genericPacket);
            } else {
                // directly send to the destination port via the shortest path
                this.targetIdToOutputPort.get(this.serverToToRID.get(dstId)).enqueue(genericPacket);
            }
        }
    }
    */

    @Override
    public void receive(Packet genericPacket) {
        // Convert to TCP packet
        TcpHeader tcpHeader = (TcpHeader) genericPacket;
        int dstId = tcpHeader.getDestinationId();
        int srcId = tcpHeader.getSourceId();

        // If we get here, it means that this device is a switch, not a server
        if (dstId == this.identifier) {
            // send it to the connected server if we've reached the ToR connected to dstID
            this.passToIntermediary(genericPacket); 
        // else, check if the current switch needs to make the right decision
        } else if (srcId == this.identifier) {
            // find the hash table entry for next step in this table
            int nextHopID = this.flowIDToNextHop.get(tcpHeader.getFlowId());
            this.targetIdToOutputPort.get(nextHopID).enqueue(genericPacket);
        } else {
            // directly send to the destination port via the shortest path
            this.targetIdToOutputPort.get(dstId).enqueue(genericPacket);
        }

    }

    @Override
    public void receiveFromIntermediary(Packet genericPacket) {
        // decide here where the next hop should be if the flow id hasn't been seen before.
        // We want to make sure that the flow ID packets must traverse the same path to avoid 
        // out of order delivery.
        long flowId = genericPacket.getFlowId();
        if (!this.flowIDToNextHop.containsKey(flowId)) {
            TcpHeader tcpHeader = (TcpHeader) genericPacket;
            int dstId = tcpHeader.getDestinationId();
            int srcId = tcpHeader.getSourceId();
            // try finding the next hop here for the hashed flow
            List<Map.Entry<Integer, Double>> possibilities = destinationToNextSwitch.get(dstId);
            final double random_number = this.rng.nextDouble();
            double cumulative_weights = 0;
            int dest = -1;
            for (Map.Entry<Integer, Double> potential_dest : possibilities) {
                double weight = potential_dest.getValue();
                if (random_number >= cumulative_weights && random_number < cumulative_weights + weight) {
                    dest = potential_dest.getKey();
                    break;
                }
                cumulative_weights += weight;
            }
            if (dest < 0) {
                if (possibilities.size() == 0) {
                    dest = dstId;
                } else {
                    dest = possibilities.get(possibilities.size() - 1).getKey();
                }
            }
            this.flowIDToNextHop.put(flowId, dest);
        }
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
    @Override
    public void addDestinationToNextSwitch(int destinationId, int nextHopId, double weight) {

        // Check for not possible identifier
        if (!connectedTo.contains(nextHopId)) {
            System.out.println("weight: " + weight);
            throw new IllegalArgumentException("Cannot add hop to a network device to which it is not connected (" + nextHopId + ")");
        }

        // Check for duplicate
        
        List<Map.Entry<Integer, Double>> current = this.destinationToNextSwitch.get(destinationId);
        boolean containsEntry = false;
        for (Map.Entry<Integer,Double> intermediateHopPair : current) {
            int intermediateHop = intermediateHopPair.getKey();
            if (intermediateHop == nextHopId) {
                containsEntry = true;
                break;
            }
        }
        if (!containsEntry) {
            current.add(new AbstractMap.SimpleEntry<Integer, Double>(nextHopId, weight));    
        }
        // Add to current ones
    }

    public void addServerID(int serverIDarg) {
        if (this.serverID < 0) {
            this.serverID = serverIDarg;
        } else {
            throw new IllegalArgumentException("Cannot reset Server ID");
        }
    }

    public int getServerID() {
        return this.serverID;
    }

    public void addToRID(int torIDarg) {
        if (this.torID < 0) {
            this.torID = torIDarg;
        } else {
            throw new IllegalArgumentException("Cannot reset ToR ID");   
        }
    }

    public void addServerIDToToRID(int serverID, int torID) {
        this.serverToToRID.put(serverID, torID);
    }

    public int getToRID() {
        return this.torID;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("WCMP Switch<id=");
        builder.append(getIdentifier());
        builder.append(", connected=");
        builder.append(connectedTo);
        builder.append(", routing: ");
        for (int i = 0; i < destinationToNextSwitch.size(); i++) {
            if (i != 0) {
                builder.append(", ");
            }
            builder.append(i);
            builder.append("->");
            builder.append(destinationToNextSwitch.get(i));
        }
        builder.append(">");
        return builder.toString();
    }

}
