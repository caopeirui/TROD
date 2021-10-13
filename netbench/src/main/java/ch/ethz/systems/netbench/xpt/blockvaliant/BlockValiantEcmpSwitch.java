package ch.ethz.systems.netbench.xpt.blockvaliant;


import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.network.*;
import ch.ethz.systems.netbench.ext.basic.TcpPacket;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.ImmutablePair;

import ch.ethz.systems.netbench.xpt.trafficawaresourcerouting.TrafficAwareSourceRoutingSwitch;

import java.util.Set;
import java.util.HashSet;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;
import java.util.Random;

public class BlockValiantEcmpSwitch extends TrafficAwareSourceRoutingSwitch {
    // private HashMap<Integer, Integer> serverToBlockID; // this is used by receiveFromIntermediary() to encapsulate tcp packet, and search for dest block

    // Routing table
    // private HashMap<Integer, ArrayList<Integer>> shortestPathsWithinBlock; // it will just store the shortest paths

    // private HashMap<Integer, ArrayList<Integer>> destinationBlockToSwitchIDs; // stores the list of next hop IDs when hopping from this switch to another block

    // protected HashMap<Integer, ArrayList<Integer>> blockIDtoEntrySwitches; // stores the list of entry switches to a target block ID in the current block

    // private HashSet<Integer> listOfAttachedServers;

    private ArrayList<Integer> valiantGroups;


    /**
     * Constructor for Source Routing switch WITH a transport layer attached to it.
     *
     * @param identifier            Network device identifier
     * @param transportLayer        Underlying server transport layer instance (set null, if none)
     * @param intermediary          Flowlet intermediary instance (takes care of hash adaptation for flowlet support)
     */
    public BlockValiantEcmpSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, int blockIDArg, int totalBlocks) {
        super(identifier, transportLayer, intermediary, blockIDArg);
        GraphDetails details = Simulator.getConfiguration().getGraphDetails();
        valiantGroups = new ArrayList<Integer>();
        for (int i = 0; i < totalBlocks; ++i) {
            if (i != this.blockID) {
                valiantGroups.add(i);
            }
        }
        // serverToBlockID = new HashMap<Integer, Integer>(); // this is used by receiveFromIntermediary() to encapsulate tcp packet, and search for dest block
        // shortestPathsWithinBlock = new HashMap<Integer, ArrayList<Integer>>(); // it will just store the shortest paths
        // destinationBlockToSwitchIDs = new HashMap<Integer, ArrayList<Integer>>();
        // listOfAttachedServers = new HashSet<Integer>();
        // blockIDtoEntrySwitches = new HashMap<Integer, ArrayList<Integer>>();
    }

    /**
     * Receives as a server, which means that this device instance has a transport layer.
     */
    /*
    private void receiveAsServer(BlockValiantEncapsulation encapsulation) {
        int dstServerID = encapsulation.getDestinationId();
        // check if reached destination, if so, passto intermediary, if not, forward to connected ToR.
        if (dstServerID == this.identifier) {
            this.passToIntermediary(encapsulation.getPacket());
        } else {
            throw new IllegalStateException("Technically a packet shouldn't get here");
            //this.targetIdToOutputPort.get(this.torID).enqueue(encapsulation);
        }
    }
    */

    /**
     * given a destination block ID, and a random number between 0 and 1,
     * returns the entry switch to the destination block
     */
    private int getRandomValiantBlock() {
        int targetGroup = this.valiantGroups.get(this.rng.nextInt(this.valiantGroups.size()));
        return targetGroup;
    }

    @Override
    public void receive(Packet genericPacket) {
        // Convert to encapsulation
        BlockValiantEncapsulation encapsulation = (BlockValiantEncapsulation) genericPacket;
        int dstServerID = encapsulation.getDestinationId();
        int srcServerID = encapsulation.getSourceId();
        int destinationBlockID = encapsulation.getDestinationBlock();
        int sourceBlockID = this.serverToBlockID.get(srcServerID);
        // check if currently at a server, rather than at a switch
        if (this.isServer()) {
            receiveAsServer(encapsulation.getPacket());
            return;
        } else if (this.listOfAttachedServers.contains(dstServerID)) {
        // Check if it has arrived at the server connected to the current switch
            this.targetIdToOutputPort.get(dstServerID).enqueue(encapsulation);
            return;
        }

        // if we get here, it means that the packet is at a switch, and is in transit to its destination
        // check if we are in the source switch
        if (this.listOfAttachedServers.contains(srcServerID) && this.blockID != encapsulation.getDestinationBlock()) {
            // make a routing decision here
            int valiantGroup = getRandomValiantBlock();
            if (valiantGroup == encapsulation.getDestinationBlock()) {
                encapsulation.markPassedValiant();
                encapsulation.setValiantBlock(this.blockID);
            } else {
                encapsulation.setValiantBlock(valiantGroup);
            }
        } 
        
        // mark encapsulation on whether if we've passed the valiant block before detailed routing decision
        //if (this.blockID == encapsulation.getValiantBlock() || this.blockID == encapsulation.getDestinationBlock()) {
        //    encapsulation.markPassedValiant();
        // }

        // Option 1 : Check if we are in the destination block already
        List<Integer> possibleNextSteps = null;
        if (destinationBlockID == this.blockID) {
            // simply route minimally to the destination when we already are in the destination block
            possibleNextSteps = this.shortestPathsWithinBlock.get(dstServerID);
        } else {
            // case 1 : already in the valiant block, then just route to destination block via valiant exit switch
            if (encapsulation.getValiantBlock() == this.blockID) {
                encapsulation.markPassedValiant();
                // prelim of case 1 : check if the entry switch to the destination block has been set
                int entrySwitchID = encapsulation.getValiantExitSwitchID();
                // set the entry switch if it has not been set
                if (entrySwitchID < 0) {
                    List<Integer> possibleEntrySwitches = this.blockIDtoEntrySwitches.get(destinationBlockID);
                    int randomlyPickedEntrySwitchID = 
                        possibleEntrySwitches.get(this.rng.nextInt(possibleEntrySwitches.size()));
                    encapsulation.setValiantExitSwitch(randomlyPickedEntrySwitchID);
                    entrySwitchID = randomlyPickedEntrySwitchID;
                }

                if (this.identifier == entrySwitchID) {
                    possibleNextSteps = this.destinationBlockToSwitchIDs.get(destinationBlockID);
                } else {
                    possibleNextSteps = this.shortestPathsWithinBlock.get(entrySwitchID);
                }
            } else {
            // case 2 : not in valiant block, then just route to valiant block
                int entrySwitchID = encapsulation.getEntrySwitchToValiantBlock();
                // set the entry switch if it has not been set
                if (entrySwitchID < 0) {
                    List<Integer> possibleEntrySwitches = this.blockIDtoEntrySwitches.get(encapsulation.getValiantBlock());
                    int randomlyPickedEntrySwitchID = 
                        possibleEntrySwitches.get(this.rng.nextInt(possibleEntrySwitches.size()));
                    encapsulation.setEntrySwitchToValiantBlock(randomlyPickedEntrySwitchID);
                    entrySwitchID = randomlyPickedEntrySwitchID;
                }

                if (this.identifier == entrySwitchID) {
                    possibleNextSteps = this.destinationBlockToSwitchIDs.get(encapsulation.getValiantBlock());
                } else {
                    possibleNextSteps = this.shortestPathsWithinBlock.get(entrySwitchID);
                }
            }
        }
        // Forward to the next switch (automatically advances path progress)
        int pathIndex = this.rng.nextInt(possibleNextSteps.size()); 
        int nextHopID = possibleNextSteps.get(pathIndex);
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
        BlockValiantEncapsulation encapsulation = new BlockValiantEncapsulation(
                packet,  destinationBlock, destinationBlock
        );
        
        // Send to network if it is server
        
        this.targetIdToOutputPort.get(this.torID).enqueue(encapsulation);
    }

    /*
    public void addDestinationToNextHopID(int destinationID, int nextHopID) {
        // Check for not possible identifier
        System.out.println("add destination for switch : " + this.identifier + " destID : " + destinationID + " nextHopID : " + nextHopID );
        if (!connectedTo.contains(nextHopID)) {
            throw new IllegalArgumentException("Cannot add hop to a network device to which it is not connected (" + nextHopID + ")");
        }

        if (!shortestPathsWithinBlock.containsKey(destinationID)) {
            this.shortestPathsWithinBlock.put(destinationID, new ArrayList<Integer>());
        }
        this.shortestPathsWithinBlock.get(destinationID).add(nextHopID);
    }
    */

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n\nBlockValiantEcmpSwitch<id=" + getIdentifier() + ", connected=" + connectedTo + ",\nrouting:\n");
        /*
        for (int i = 0; i < destinationToNextSwitch.size(); i++) {
            builder.append("\tfor " + i + " next hops are "  + destinationToNextSwitch.get(i) + "\n");
        }
        */
        //builder.append(",\ninclusive valiant range: [" + lowBoundValiantRangeIncl + ", " + highBoundValiantRangeIncl + "]\n");
        builder.append(">\n\n");
        return builder.toString();
    }

}
