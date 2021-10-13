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
 * Locally-aware UGAL implementation (picks paths based on queue lengths at the source switch)
 * needs to rely on backpressure to detect congestion
 */
public class BlockUgalLQueueBasedSwitch extends BlockSwitch {

    // protected HashMap<Integer, Integer> serverToBlockID; // this is used by receiveFromIntermediary() to encapsulate tcp packet, and search for dest block

    // Routing table
    // protected HashMap<Integer, ArrayList<Integer>> shortestPathsWithinBlock; // it will just store the shortest paths

    // protected HashMap<Integer, ArrayList<Integer>> destinationBlockToSwitchIDs; // stores the list of next hop IDs when hopping from this switch to another block

    // protected HashMap<Integer, ArrayList<Integer>> blockIDtoEntrySwitches; // stores the list of entry switches to a target block ID in the current block

    // protected HashSet<Integer> listOfAttachedServers;

    // private HashMap<ImmutablePair<Integer, Integer>, ArrayList<OutputPort>> interBlockOutputPorts; // given a pair of (srcBlock, dstBlock), retrieves a list of output ports

    private final int totalBlocks;
    // private ArrayList<Integer> valiantGroups;

    /**
     * Constructor for Source Routing switch WITH a transport layer attached to it.
     *
     * @param identifier            Network device identifier
     * @param transportLayer        Underlying server transport layer instance (set null, if none)
     * @param intermediary          Flowlet intermediary instance (takes care of hash adaptation for flowlet support)
     */
    public BlockUgalLQueueBasedSwitch(int identifier, TransportLayer transportLayer, Intermediary intermediary, int blockIDArg, int totalBlocks) {
        super(identifier, transportLayer, intermediary, blockIDArg);
        this.totalBlocks = totalBlocks;
    }

    private OutputPort findNextHopIDWithMinimalQueue(ArrayList<Integer> possibleNextHopIDs) {
        OutputPort leastCongestedOutputPort = null;
        long leastCongestedQueueLength = -1;
        if (possibleNextHopIDs.size() == 0) {
            throw new IllegalStateException("No next steps possible to indicated switch. Possibly not in the same blocks?");
        } else {
            ArrayList<OutputPort> equallyUncongestedOutputPorts = new ArrayList<OutputPort>();
            for (int nextHopID : possibleNextHopIDs) {
                long queueLength = this.targetIdToOutputPort.get(nextHopID).getBufferOccupiedBits();
                if (leastCongestedQueueLength < 0 || queueLength < leastCongestedQueueLength) {
                    leastCongestedOutputPort = this.targetIdToOutputPort.get(nextHopID);
                    leastCongestedQueueLength = queueLength;
                } 
            }
            for (int nextHopID : possibleNextHopIDs) {
                if (this.targetIdToOutputPort.get(nextHopID).getBufferOccupiedBits() == leastCongestedQueueLength) {
                    equallyUncongestedOutputPorts.add(this.targetIdToOutputPort.get(nextHopID));
                } 
            }
            int randomIndex = this.rng.nextInt(equallyUncongestedOutputPorts.size());
            leastCongestedOutputPort = equallyUncongestedOutputPorts.get(randomIndex);
        }
        return leastCongestedOutputPort;
    }

    private ImmutablePair<Integer, OutputPort> findLeastCongestedPathToTargetBlock(int dstBlock) {
        if (this.blockID == dstBlock) {
            throw new IllegalArgumentException("Cannot receive source and dest blocks that are the same");
        }
        ArrayList<Integer> entrySwitchesToDestBlock = 
            this.blockIDtoEntrySwitches.get(dstBlock);
        long minQueueSize = -1;
        int minEntrySwitch = -1;
        OutputPort minQueuePort = null;
        ArrayList<OutputPort> equallyUncongestedOutputPorts = new ArrayList<OutputPort>();
        for (int entrySwitch : entrySwitchesToDestBlock) {
            OutputPort tmpOutputPort = null;
            if (entrySwitch == this.identifier) {
                tmpOutputPort = findNextHopIDWithMinimalQueue(this.destinationBlockToSwitchIDs.get(dstBlock));
            } else {
                tmpOutputPort = findNextHopIDWithMinimalQueue(this.shortestPathsWithinBlock.get(entrySwitch));
            }
            if (minQueueSize < 0 || minQueueSize > tmpOutputPort.getBufferOccupiedBits()) {
                minQueuePort = tmpOutputPort;
                minEntrySwitch = entrySwitch;
                minQueueSize = tmpOutputPort.getBufferOccupiedBits();
            }
        }
        return new ImmutablePair(minEntrySwitch, minQueuePort);

    }


    // finds the least congested inter-block path and programs the packet encapsulation
    // so that the caller methods does not have to worry about programming the packets
    private void makeLocalizedRoutingDecision(BlockUgalEncapsulation encapsulation) {
        int srcBlock = this.serverToBlockID.get(encapsulation.getSourceId());
        int dstBlock = this.serverToBlockID.get(encapsulation.getDestinationId());
        if (srcBlock == dstBlock) {
            return;
            //throw new IllegalArgumentException("No need to route through inter-block channels if packet is within block");
        }
        ImmutablePair<Integer, OutputPort> entrySwitchPortPair = findLeastCongestedPathToTargetBlock(dstBlock);
        OutputPort directPathOutputPort = entrySwitchPortPair.getValue(); 
        int directPathEntrySwitchID = entrySwitchPortPair.getKey();
        long directPathQueueSize = directPathOutputPort.getBufferOccupiedBits();

        // next, go through the blocks and find the valiant path with minimal congestion
        long minIndirectPathQueueSize = -1;
        OutputPort minIndirectPathOutputPort = null;
        int minIndirectPathEntrySwitch = -1;
        int valiantGroup = -1;
        for (int blockID = 0; blockID < totalBlocks; ++blockID) {
            if (blockID == srcBlock || blockID == dstBlock) {
                continue;
            }
            ImmutablePair<Integer, OutputPort> tmpIndirectEntrySwitchPortPair = findLeastCongestedPathToTargetBlock(blockID);
            OutputPort tmpIndirectPathOutputPort = tmpIndirectEntrySwitchPortPair.getValue();   
            int tmpIndirectPathEntrySwitch = tmpIndirectEntrySwitchPortPair.getKey();
            if (minIndirectPathQueueSize < 0 || 
                    minIndirectPathQueueSize > 
                        tmpIndirectPathOutputPort.getBufferOccupiedBits()) {
                minIndirectPathQueueSize = 
                    tmpIndirectPathOutputPort.getBufferOccupiedBits();
                minIndirectPathOutputPort = tmpIndirectPathOutputPort;
                minIndirectPathEntrySwitch = tmpIndirectPathEntrySwitch;
                valiantGroup = blockID;
            }
        }
        // take valiant path (add another condition (indirect path queue size can be negative if there are only two blocks in the network))
        if (minIndirectPathQueueSize >= 0 && minIndirectPathQueueSize < directPathQueueSize) {
            // set the valiant block
            encapsulation.setValiantBlock(valiantGroup);
            encapsulation.setEntrySwitchToValiantBlock(minIndirectPathEntrySwitch);
        } else {
        // take direct path
            // mark that we've entered valiant
            encapsulation.markEnteredValiant();
            encapsulation.setValiantBlock(srcBlock);
            encapsulation.setValiantExitSwitch(directPathEntrySwitchID);
            encapsulation.setEntrySwitchToValiantBlock(this.identifier);
        }
    }


    @Override
    public void receive(Packet genericPacket) {
        // Convert to encapsulation
        BlockUgalEncapsulation encapsulation = (BlockUgalEncapsulation) genericPacket;
        int dstServerID = encapsulation.getDestinationId();
        int dstBlockID = this.serverToBlockID.get(dstServerID);
        int srcServerID = encapsulation.getSourceId();
        int srcBlockID = this.serverToBlockID.get(srcServerID);
        
        // if we are near the destination, then just receive or send to port connecting dst server connected to this switch
        if (this.identifier == dstServerID) {
            receiveAsServer(encapsulation.getPacket());
            return;
        } else if (this.listOfAttachedServers.contains(dstServerID)) {
            this.targetIdToOutputPort.get(dstServerID).enqueue(encapsulation);
            return;
        }

        // make routing decisions here
        if (this.listOfAttachedServers.contains(srcServerID)) {
            this.makeLocalizedRoutingDecision(encapsulation);
        }

        if (this.blockID == encapsulation.getValiantBlockID()) {
            encapsulation.markEnteredValiant();
        }

        List<Integer> possibleNextSteps = null;
        // check if we are already in the block belonging to the destination server ID
        // if so, then just route directly to the destination server 
        if (this.blockID == dstBlockID) {
            // check if currently at a server, rather than at a switch
            possibleNextSteps = this.shortestPathsWithinBlock.get(dstServerID);
        } else {
            // case 1 : already in the valiant block, then just route to destination block via valiant exit switch
            if (encapsulation.getValiantBlockID() == this.blockID) {
                encapsulation.markEnteredValiant();
                // prelim of case 1 : check if the entry switch to the destination block has been set
                int entrySwitchID = encapsulation.getValiantExitSwitchID();
                // set the entry switch if it has not been set
                if (entrySwitchID < 0) {
                    List<Integer> possibleEntrySwitches = this.blockIDtoEntrySwitches.get(dstBlockID);
                    int randomlyPickedEntrySwitchID = 
                        possibleEntrySwitches.get(this.rng.nextInt(possibleEntrySwitches.size()));
                    encapsulation.setValiantExitSwitch(randomlyPickedEntrySwitchID);
                    entrySwitchID = randomlyPickedEntrySwitchID;
                }

                if (this.identifier == entrySwitchID) {
                    possibleNextSteps = this.destinationBlockToSwitchIDs.get(dstBlockID);
                } else {
                    possibleNextSteps = this.shortestPathsWithinBlock.get(entrySwitchID);
                }
            } else {
            // case 2 : not in valiant block, then just route to valiant block
                int entrySwitchID = encapsulation.getEntrySwitchToValiantBlock();
                // set the entry switch if it has not been set
                if (entrySwitchID < 0) {
                    List<Integer> possibleEntrySwitches = this.blockIDtoEntrySwitches.get(encapsulation.getValiantBlockID());
                    int randomlyPickedEntrySwitchID = 
                        possibleEntrySwitches.get(this.rng.nextInt(possibleEntrySwitches.size()));
                    encapsulation.setEntrySwitchToValiantBlock(randomlyPickedEntrySwitchID);
                    entrySwitchID = randomlyPickedEntrySwitchID;
                }

                if (this.identifier == entrySwitchID) {
                    possibleNextSteps = this.destinationBlockToSwitchIDs.get(encapsulation.getValiantBlockID());
                } else {
                    possibleNextSteps = this.shortestPathsWithinBlock.get(entrySwitchID);
                }
            }
        }
        int randomIndex = this.rng.nextInt(possibleNextSteps.size());
        int nextHopID = possibleNextSteps.get(randomIndex);
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
        builder.append("\n\nBlockUgalLQueueBasedSwitch<id=" + getIdentifier() + ", connected=" + connectedTo + ",\nrouting:\n");
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
