package ch.ethz.systems.netbench.xpt.ugal;


import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.network.*;
import ch.ethz.systems.netbench.ext.basic.TcpPacket;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.ImmutablePair;

import ch.ethz.systems.netbench.xpt.trafficawaresourcerouting.BlockSwitch;

import ch.ethz.systems.netbench.xpt.ugal.BlockUgalEncapsulation;

import java.util.Set;
import java.util.HashSet;

import java.util.List;
import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;
import java.util.Random;


/**
 * Globally-aware UGAL implementation (picks paths based on queue lengths)
 */
public class BlockUgalGQueueBasedSwitch extends BlockSwitch {

    // protected HashMap<Integer, Integer> serverToBlockID; // this is used by receiveFromIntermediary() to encapsulate tcp packet, and search for dest block

    // Routing table
    // protected HashMap<Integer, ArrayList<Integer>> shortestPathsWithinBlock; // it will just store the shortest paths

    // protected HashMap<Integer, ArrayList<Integer>> destinationBlockToSwitchIDs; // stores the list of next hop IDs when hopping from this switch to another block

    // protected HashMap<Integer, ArrayList<Integer>> blockIDtoEntrySwitches; // stores the list of entry switches to a target block ID in the current block

    // protected HashSet<Integer> listOfAttachedServers;

    private HashMap<ImmutablePair<Integer, Integer>, ArrayList<OutputPort>> interBlockOutputPorts; // given a pair of (srcBlock, dstBlock), retrieves a list of output ports

    private final int totalBlocks;
    // private ArrayList<Integer> valiantGroups;

    /**
     * Constructor for Source Routing switch WITH a transport layer attached to it.
     *
     * @param identifier            Network device identifier
     * @param transportLayer        Underlying server transport layer instance (set null, if none)
     * @param intermediary          Flowlet intermediary instance (takes care of hash adaptation for flowlet support)
     */
    public BlockUgalGQueueBasedSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, int blockIDArg, int totalBlocks) {
        super(identifier, transportLayer, intermediary, blockIDArg);

        this.totalBlocks = totalBlocks;
        // UGAL_G - port specfic items
        this.interBlockOutputPorts = null;
    }

    /**
     * Returns the output port connecting this switch to nextHopID (which also has to be a switch)
     */ 
    protected OutputPort getOutputPort(int nextHopID) {
        return this.targetIdToOutputPort.get(nextHopID);
    }

    public void setInterblockOutputPorts(HashMap<ImmutablePair<Integer, Integer>, ArrayList<OutputPort>> interblockOutputPorts) {
        this.interBlockOutputPorts = interblockOutputPorts;
    }

    private OutputPort findLeastCongestedOutputPortBetweenTwoBlocks(int srcBlock, int dstBlock) {
        if (srcBlock == dstBlock) {
            throw new IllegalArgumentException("Cannot receive source and dest blocks that are the same");
        }
        ImmutablePair blockPair = new ImmutablePair(srcBlock, dstBlock);
        long minQueueSize = -1;
        OutputPort minQueuePort = null;
        for (OutputPort port : this.interBlockOutputPorts.get(blockPair)) {
            long queueSize = port.getBufferOccupiedBits();
            if (minQueueSize < 0) {
                minQueueSize = queueSize;
                minQueuePort = port;
            } else {
                if (minQueueSize > queueSize) {
                    minQueueSize = queueSize;
                    minQueuePort = port;
                }
            }
        }
        if (minQueueSize < 0) {
            throw new IllegalStateException("Cannot find output ports between blocks " + srcBlock + " and " + dstBlock);
        }
        ArrayList<OutputPort> equallyUncongestedPorts = new ArrayList<OutputPort>();
        for (OutputPort port : this.interBlockOutputPorts.get(blockPair)) {
            if (port.getBufferOccupiedBits() == minQueueSize) {
                equallyUncongestedPorts.add(port);
            }
        }
        int randomIndex = this.rng.nextInt(equallyUncongestedPorts.size());
        return equallyUncongestedPorts.get(randomIndex);
    }


    // finds the least congested inter-block path and programs the packet encapsulation
    // so that the caller methods does not have to worry about programming the packets
    private void findLeastCongestionInterblockPath(BlockUgalEncapsulation encapsulation) {
        int srcBlock = this.serverToBlockID.get(encapsulation.getSourceId());
        int dstBlock = this.serverToBlockID.get(encapsulation.getDestinationId());
        if (srcBlock == dstBlock) {
            return;
            //throw new IllegalArgumentException("No need to route through inter-block channels if packet is within block");
        }
        OutputPort directPathOutputPort = findLeastCongestedOutputPortBetweenTwoBlocks(srcBlock, dstBlock); 
        long directPathQueueSize = directPathOutputPort.getBufferOccupiedBits();
        // next, go through the blocks and find the valiant path with minimal congestion
        long minIndirectPathQueueSize = -1;
        OutputPort minIndirectPathOutputPort1 = null;
        OutputPort minIndirectPathOutputPort2 = null;
        int valiantGroup = -1;
        for (int blockID = 0; blockID < totalBlocks; ++blockID) {
            if (blockID == srcBlock || blockID == dstBlock) {
                continue;
            }
            OutputPort indirectPathOutputPort1 = 
                findLeastCongestedOutputPortBetweenTwoBlocks(srcBlock, blockID);
            OutputPort indirectPathOutputPort2 = 
                findLeastCongestedOutputPortBetweenTwoBlocks(blockID, dstBlock);
            if (minIndirectPathQueueSize < 0 || 
                    minIndirectPathQueueSize > 
                        indirectPathOutputPort1.getBufferOccupiedBits() + 
                        indirectPathOutputPort2.getBufferOccupiedBits()) {
                minIndirectPathQueueSize = 
                    indirectPathOutputPort1.getBufferOccupiedBits() + indirectPathOutputPort2.getBufferOccupiedBits();
                minIndirectPathOutputPort1 = indirectPathOutputPort1;
                minIndirectPathOutputPort2 = indirectPathOutputPort2;
                valiantGroup = blockID;
            }
        }

        // take valiant path (add another condition (indirect path queue size can be negative if there are only two blocks in the network))
        if (minIndirectPathQueueSize < directPathQueueSize && minIndirectPathQueueSize >= 0) {
            // set the valiant block
            encapsulation.setValiantBlock(valiantGroup);
            // set the entry switch to valiant group
            encapsulation.setEntrySwitchToValiantBlock(minIndirectPathOutputPort1.getOwnId());
            // set the valiant exit switch
            encapsulation.setValiantExitSwitch(minIndirectPathOutputPort2.getOwnId());
        } else {
        // take direct path
            // mark that we've entered valiant
            encapsulation.markEnteredValiant();
            encapsulation.setValiantBlock(srcBlock);
            encapsulation.setValiantExitSwitch(directPathOutputPort.getOwnId());
            encapsulation.setEntrySwitchToValiantBlock(this.identifier);
        }
    }


    @Override
    public void receive(Packet genericPacket) {
        // Convert to encapsulation
        BlockUgalEncapsulation encapsulation = (BlockUgalEncapsulation) genericPacket;
        int dstServerID = encapsulation.getDestinationId();
        int srcServerID = encapsulation.getSourceId();
        
        // make routing decisions here because we are at the source switch
        if (this.listOfAttachedServers.contains(srcServerID)) {
            this.findLeastCongestionInterblockPath(encapsulation);
        }

        // check if we've passed the valiant group
        if (this.blockID == encapsulation.getValiantBlockID()) {
            encapsulation.markEnteredValiant();
        }

        int nextHopID = -1;

        // check if we are already in the block belonging to the destination server ID
        // if so, then just route directly to the destination server 
        if (this.blockID == encapsulation.getDestinationBlockID()) {
            // check if currently at a server, rather than at a switch
            if (this.isServer()) {
                receiveAsServer(encapsulation.getPacket());
                return;
            } else if (this.listOfAttachedServers.contains(dstServerID)) {
                // or Check if it has arrived at the server connected to the current switch
                nextHopID = dstServerID;
            } else {
                ArrayList<Integer> possibleNextHopIDs = this.shortestPathsWithinBlock.get(dstServerID);
                int randomIndex = this.rng.nextInt(possibleNextHopIDs.size());
                nextHopID = possibleNextHopIDs.get(randomIndex);
            }
        } else {
            // if we are not in the destination block, then check if we've entered the valiant block
            ArrayList<Integer> possibleNextHopIDs;
            if (encapsulation.enteredValiant()) {
                int valiantExitSwitchID = encapsulation.getValiantExitSwitchID();
                if (this.identifier == valiantExitSwitchID) {
                    possibleNextHopIDs = this.destinationBlockToSwitchIDs.get(encapsulation.getDestinationBlockID());
                } else {
                    possibleNextHopIDs = this.shortestPathsWithinBlock.get(valiantExitSwitchID);
                }
                
            } else {
                // route to valiant group
                int entrySwitchIDToValiantBlock = encapsulation.getEntrySwitchToValiantBlock();
                if (this.identifier == entrySwitchIDToValiantBlock) {
                    possibleNextHopIDs = this.destinationBlockToSwitchIDs.get(encapsulation.getValiantBlockID());
                } else {
                    possibleNextHopIDs = this.shortestPathsWithinBlock.get(entrySwitchIDToValiantBlock);
                }
            }
            int randomIndex = this.rng.nextInt(possibleNextHopIDs.size());
            nextHopID = possibleNextHopIDs.get(randomIndex);
        }

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
        BlockUgalEncapsulation encapsulation = new BlockUgalEncapsulation(
                packet, destinationBlock
        );
        
        // Send to network if it is server
        this.targetIdToOutputPort.get(this.torID).enqueue(encapsulation);
    }

    

   @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n\nBlockUgalGQueueBasedSwitch<id=" + getIdentifier() + ", connected=" + connectedTo + ",\nrouting:\n");
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
