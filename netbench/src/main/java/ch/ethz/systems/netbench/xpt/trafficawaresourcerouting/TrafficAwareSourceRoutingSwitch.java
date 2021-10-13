package ch.ethz.systems.netbench.xpt.trafficawaresourcerouting;


import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.network.*;
import ch.ethz.systems.netbench.ext.basic.TcpPacket;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.ImmutablePair;


import java.util.Set;
import java.util.HashSet;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;
import java.util.Random;

public class TrafficAwareSourceRoutingSwitch extends BlockSwitch {
    // keeps tabs of which server ID belongs to which block ID
    //protected HashMap<Integer, Integer> serverToBlockID; // this is used by receiveFromIntermediary() to encapsulate tcp packet, and search for dest block

    // Routing table
    //protected HashMap<Integer, ArrayList<Integer>> shortestPathsWithinBlock; // it will just store the shortest paths

    //protected HashMap<Integer, ArrayList<Integer>> destinationBlockToSwitchIDs; // stores the list of next hop IDs when hopping from this switch to another block

    private HashMap<Integer, ArrayList<Pair<Integer, Double>>> weightsToEachBlock;

    //protected HashSet<Integer> listOfAttachedServers;

    // Block ID that this switch belongs to
    //protected int blockID; 

    //protected int torID = -1;

    /**
     * Constructor for Source Routing switch WITH a transport layer attached to it.
     *
     * @param identifier            Network device identifier
     * @param transportLayer        Underlying server transport layer instance (set null, if none)
     * @param intermediary          Flowlet intermediary instance (takes care of hash adaptation for flowlet support)
     */
    public TrafficAwareSourceRoutingSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, int blockIDArg) {
        super(identifier, transportLayer, intermediary, blockIDArg);
        this.weightsToEachBlock = new HashMap<Integer, ArrayList<Pair<Integer, Double>>>();
        
    }

    // Does a self-check and checks whether 
    public void checkRoutingWeights(int totalBlocks) {
        for (int block = 0; block < totalBlocks; ++block) {
            if (block == this.blockID) {
                continue;
            }
            double sumOfRoutingWeights = 0;
            ArrayList<Pair<Integer, Double>> routingWeightPairs = 
                this.weightsToEachBlock.get(block);
            for (Pair<Integer, Double> routingPair : routingWeightPairs) {
                int entrySwitchID = routingPair.getKey();
                double routingWeight = routingPair.getValue();
                sumOfRoutingWeights += routingWeight;
            }
            if (sumOfRoutingWeights > 1.001 || sumOfRoutingWeights < 0.999) {
                throw new IllegalStateException("Sum of routing weights from switch ID : " + 
                    this.identifier + 
                    " to block : " + 
                    block + 
                    " is " + 
                    sumOfRoutingWeights);
            }
        }
    }

    /**
     * given a destination block ID, and a random number between 0 and 1,
     * returns the entry switch to the destination block
     */
    private int locateEntrySwitch(int destBlockID, double randomNumber) {
        List<Pair<Integer, Double>> possibleOptions = weightsToEachBlock.get(destBlockID);
        //System.out.println("Switch " + this.identifier + "making decision");
        double aggregate = 0.;
        int entryID = possibleOptions.get(0).getKey();
        for (int i = 0; i < possibleOptions.size(); ++i) {
            Pair<Integer, Double> entryWeightPair = possibleOptions.get(i);
            if (randomNumber >= aggregate && randomNumber < aggregate + entryWeightPair.getValue()) {
                entryID = entryWeightPair.getKey();
                break;
            }
            aggregate += entryWeightPair.getValue();
        }
       
        //return entryID;
        return possibleOptions.get(this.rng.nextInt(possibleOptions.size())).getKey();
    }

        /**
     * given a destination block ID, and a random number between 0 and 1,
     * returns the entry switch to the destination block
     */
    private int randomlyLocateEntrySwitch(int destBlockID, double randomNumber) {
        List<Integer> possibleOptions = this.blockIDtoEntrySwitches.get(destBlockID);
        //return entryID;
        return possibleOptions.get(this.rng.nextInt(possibleOptions.size()));
    }

    @Override
    public void receive(Packet genericPacket) {
        // Convert to encapsulation first
        BlockAwareSourceRoutingEncapsulation encapsulation = (BlockAwareSourceRoutingEncapsulation) genericPacket;
        int dstServerID = encapsulation.getDestinationId();
        int dstBlockID = encapsulation.getDestinationBlock();
        int srcServerID = encapsulation.getSourceId();
        int srcBlockID = this.serverToBlockID.get(srcServerID);

        // Check if currently at a server, rather than at a switch
        if (this.identifier == dstServerID) {
            receiveAsServer(encapsulation.getPacket());
            return;
        } else if (this.listOfAttachedServers.contains(dstServerID)) {
            // Forward to the next switch (automatically advances path progress)
            this.targetIdToOutputPort.get(dstServerID).enqueue(encapsulation);
            return;
        }
        /*
        System.out.println( "This switch is " + this.identifier +
                            "\npacket source server : " + 
                            encapsulation.getSourceId() + " dest server : " + encapsulation.getDestinationId() +
                            " source block : " + this.serverToBlockID.get(encapsulation.getSourceId()) + " destination block : " + 
                            this.serverToBlockID.get(encapsulation.getDestinationId()));
        */
        
        // Check if we are in the source switch, if so make the routing decisions
        if (this.listOfAttachedServers.contains(srcServerID) && 
                this.blockID != encapsulation.getDestinationBlock()) {
            double rv = this.rng.nextDouble();
            //int entryID = locateEntrySwitch(encapsulation.getDestinationBlock(), rv);
            int entryID = randomlyLocateEntrySwitch(encapsulation.getDestinationBlock(), rv);
            encapsulation.setEntrySwitchID(entryID);
        }
        
        // Option 1 : Check if we are in the destination block already
        int nextHopID = -1;
        List<Integer> possibleNextHopIDs = null;
        if (dstBlockID == this.blockID) {
            // Simply route minimally to the destination
            possibleNextHopIDs = this.shortestPathsWithinBlock.get(dstServerID);
        } else {
        // Option 2 : We are not in the same block, so we gotta hop to the entry switch
            int entryID = encapsulation.getEntrySwitchID();
            // First, check if we are at entry switch, if yes, hop directly to destination block
            if (this.identifier == entryID) {
                // send to global
                possibleNextHopIDs = this.destinationBlockToSwitchIDs.get(dstBlockID);
            } else {
                // If not then we are in transit to entry switch, so just randomly route
                // to the destination via a randomly-chosen shortest path
                possibleNextHopIDs = this.shortestPathsWithinBlock.get(entryID);
            }    
        }
        int pathIndex = this.rng.nextInt(possibleNextHopIDs.size()); 
        nextHopID = possibleNextHopIDs.get(pathIndex);
        // Forward to the next switch (automatically advances path progress)
        this.targetIdToOutputPort.get(nextHopID).enqueue(encapsulation);
    }
    
    /*
     * Receives a TCP packet from the transport layer, which
     * is oblivious to the source routing happening underneath.
     * The TCP packet is then encapsulated to carry information of the
     * route it must take. The sequential hash of the packet is used
     * to determine the path it should be sent on.
     *
     * @param genericPacket     TCP packet instance
     */
    @Override
    public void receiveFromIntermediary(Packet genericPacket) {
        TcpPacket packet = (TcpPacket) genericPacket;
        
        // check if packet has errors
        if (packet.getSourceId() == packet.getDestinationId()) {
            throw new IllegalStateException("Should not be receiving a packet that has its destination ID be the same as source ID");
        }

        int destinationBlock = this.serverToBlockID.get(packet.getDestinationId());
        
        // Create encapsulation to propagate through the network
        BlockAwareSourceRoutingEncapsulation encapsulation = new BlockAwareSourceRoutingEncapsulation(
                packet,  -1, destinationBlock
        );
        
        // Send to network if it is server
        this.targetIdToOutputPort.get(this.torID).enqueue(encapsulation);
    }

    /**
     * Adds the weights of routing traffic from this switch to a given destination block via 
     * a specific entry switch that belongs to the same block
     */
    protected void addEntrySwitchWeightToTargetDestBlock(int destBlock, int entrySwitch, double weight) {
        if (destBlock == this.blockID) {
            throw new IllegalArgumentException("Cannot add destblock same as current dest block");
        }
        if (!this.weightsToEachBlock.containsKey(destBlock)) {
            this.weightsToEachBlock.put(destBlock , new ArrayList<Pair<Integer, Double>>());
        }
        this.weightsToEachBlock.get(destBlock).add(new ImmutablePair(entrySwitch, weight));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("TrafficAwareSourceRoutingSwitch<id=");
        builder.append(this.getIdentifier());
        builder.append(", connected=");
        builder.append(connectedTo);
        builder.append(", routing: ");
        /*
        for (int i = 0; i < destinationToPaths.size(); i++) {
            builder.append("for ");
            builder.append(i);
            builder.append(" possible paths are ");
            builder.append(destinationToPaths.get(i));
            builder.append("; ");
        }
        */
        //builder.append(">");
        return builder.toString();
    }

}
