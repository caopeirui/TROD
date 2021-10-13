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

public abstract class BlockSwitch extends NetworkDevice {
    // keeps tabs of which server ID belongs to which block ID
    protected HashMap<Integer, Integer> serverToBlockID; // this is used by receiveFromIntermediary() to encapsulate tcp packet, and search for dest block

    // Routing table
    // it will just store the possible next hop switch IDs to get to a specific destination switch,
    // Note : the destination switch MUST share the same block ID as this switch
    protected HashMap<Integer, ArrayList<Integer>> shortestPathsWithinBlock; 

    // stores the list of next hop IDs when hopping from this switch to another block
    protected HashMap<Integer, ArrayList<Integer>> destinationBlockToSwitchIDs; 

    protected HashMap<Integer, ArrayList<Integer>> blockIDtoEntrySwitches;

    protected HashSet<Integer> listOfAttachedServers;

    // Block ID that this switch belongs to
    protected int blockID; 

    protected int torID;

    protected Random rng; 

    /**
     * Constructor for Source Routing switch WITH a transport layer attached to it.
     *
     * @param identifier            Network device identifier
     * @param transportLayer        Underlying server transport layer instance (set null, if none)
     * @param intermediary          Flowlet intermediary instance (takes care of hash adaptation for flowlet support)
     * @param blockIDArg            Block ID to which this device belongs 
     */
    public BlockSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, int blockIDArg) {
        super(identifier, transportLayer, intermediary);
        
        this.serverToBlockID = new HashMap<Integer, Integer>(); // this is used by receiveFromIntermediary() to encapsulate tcp packet, and search for dest block
        this.shortestPathsWithinBlock = new HashMap<Integer, ArrayList<Integer>>(); // it will just store the shortest paths
        this.destinationBlockToSwitchIDs = new HashMap<Integer, ArrayList<Integer>>();
        this.blockIDtoEntrySwitches = new  HashMap<Integer, ArrayList<Integer>>();
        this.listOfAttachedServers = new HashSet<Integer>();

        this.rng = new Random();
        this.blockID = blockIDArg;
        GraphDetails details = Simulator.getConfiguration().getGraphDetails();
        if (this.isServer()) {
            int torID = details.getTorIdOfServer(identifier);
            this.torID = torID;
        } else {
            this.torID = -1;
        }
        
    }

    /**
     * Retrieves the ToR switch ID if this device is a server and belongs to a
     */
    public int getToRID() {
        return this.torID;
    }

    public int getBlockID() {
        return this.blockID;
    }

    /**
     * Receives as a server, which means that this device instance has a transport layer.
     */
    protected void receiveAsServer(TcpPacket tcpPacket) {
        int dstServerID = tcpPacket.getDestinationId();
        // check if reached destination, if so, passto intermediary, if not, forward to connected ToR.
        if (dstServerID == this.identifier) {
            this.passToIntermediary(tcpPacket);
        } else {
            throw new IllegalStateException("Technically a packet shouldn't get here");
            //this.targetIdToOutputPort.get(this.torID).enqueue(encapsulation);
        }
    }

    public void addDestinationToNextHopID(int destinationID, int nextHopID) {
        // Check for not possible identifier
        // System.out.println("add destination for switch : " + this.identifier + " destID : " + destinationID + " nextHopID : " + nextHopID );
        if (!connectedTo.contains(nextHopID)) {
            throw new IllegalArgumentException("Cannot add hop to a network device to which it is not connected (" + nextHopID + ")");
        }

        if (!shortestPathsWithinBlock.containsKey(destinationID)) {
            this.shortestPathsWithinBlock.put(destinationID, new ArrayList<Integer>());
        }
        this.shortestPathsWithinBlock.get(destinationID).add(nextHopID);
    }

    /**
     * Adds a target destination block to this switch, and the ID of the next hop switch that is going to get
     * the packet there.
     */
    public void addDestinationBlock(int blockID, int nextHopID) {
        // Check for not possible identifier
        if (!connectedTo.contains(nextHopID)) {
            throw new IllegalArgumentException("Cannot add hop to a network device to which it is not connected (" + nextHopID + ")");
        }        
        if (!this.destinationBlockToSwitchIDs.containsKey(blockID)) {
            this.destinationBlockToSwitchIDs.put(blockID, new ArrayList<Integer>());
        }
        this.destinationBlockToSwitchIDs.get(blockID).add(nextHopID);
    }

    public void addConnectedServer(int serverID) {
        if (this.isServer()) {
            throw new IllegalArgumentException("Cannot be adding servers to a network device that is already a server");
        }
        this.listOfAttachedServers.add(serverID);
    }

    public void addEntrySwitchToDestinationBlockID(int targetBlock, int entrySwitchID) {
        if (targetBlock == this.blockID) {
            throw new IllegalArgumentException("Should not add entry switch to destination block that is the same block as the current switch");
        }
        if (!this.blockIDtoEntrySwitches.containsKey(targetBlock)) {
            this.blockIDtoEntrySwitches.put(targetBlock, new ArrayList());
        }
        // check if the entry switch to target block has already been included.
        if (!this.blockIDtoEntrySwitches.get(targetBlock).contains(entrySwitchID)) {
            this.blockIDtoEntrySwitches.get(targetBlock).add(entrySwitchID);
        }
        
    }

    /**
     * Sets the map of all server IDs to their respective block IDs. This allows the Block Switches
     * to determine to which block a server belongs to, so it knows which target block to route towards
     * just by reading a packet's destination ID.
     */
    public void setServerIDToBlockIDMap(HashMap<Integer, Integer> serverToBlockMap) {
        this.serverToBlockID = serverToBlockMap;
    }
}
