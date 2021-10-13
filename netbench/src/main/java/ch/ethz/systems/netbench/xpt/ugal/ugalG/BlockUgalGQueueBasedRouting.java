package ch.ethz.systems.netbench.xpt.ugal;

import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.xpt.blockvaliant.BlockValiantRouting;
import edu.asu.emit.algorithm.graph.Graph;
import edu.asu.emit.algorithm.graph.Vertex;
import edu.asu.emit.algorithm.graph.algorithms.YenTopKShortestPathsAlg;

import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import static ch.ethz.systems.netbench.core.Simulator.getConfiguration;

public class BlockUgalGQueueBasedRouting extends BlockValiantRouting {

    //private HashMap<Integer, HashMap<Integer, List<Pair<Integer, Double>>>> routingWeights;

    public BlockUgalGQueueBasedRouting(Map<Integer, NetworkDevice> idToNetworkDevice) {
        // initialize the parent class
        super(idToNetworkDevice);
        SimulationLogger.logInfo("Routing", "BLOCK_UGALG_QUEUE_BASED_ROUTING");
    }

    private void locateAllInterBlockOutputPorts(Graph graph) {

        HashMap<ImmutablePair<Integer, Integer>, ArrayList<OutputPort>> interBlockOutputPorts = 
            new HashMap<ImmutablePair<Integer, Integer>, ArrayList<OutputPort>>();

        for (int deviceID : this.idToNetworkDevice.keySet()) {
            // skip if current device is merely a server
            if (this.idToNetworkDevice.get(deviceID).isServer()) {
                continue;
            }

            Vertex switchVertex = graph.getVertex(deviceID);

            BlockUgalGQueueBasedSwitch srcSwitch = (BlockUgalGQueueBasedSwitch) this.idToNetworkDevice.get(deviceID);
            int srcBlockID = srcSwitch.getBlockID();
            List<Vertex> neighborVertices = graph.getAdjacentVertices( switchVertex );
            for (Vertex neighborVertex : neighborVertices) {
                BlockUgalGQueueBasedSwitch dstSwitch = (BlockUgalGQueueBasedSwitch) this.idToNetworkDevice.get(neighborVertex.getId());
                int dstBlockID = dstSwitch.getBlockID();
                if (dstBlockID != srcBlockID && !dstSwitch.isServer()) {
                    // inserting this output port
                    ImmutablePair blockPair = new ImmutablePair(srcBlockID, dstBlockID);
                    if (!interBlockOutputPorts.containsKey(blockPair)) {
                        interBlockOutputPorts.put(blockPair, new ArrayList<OutputPort>());
                    }
                    interBlockOutputPorts.get(blockPair).add(srcSwitch.getOutputPort(dstSwitch.getIdentifier()));
                }
            }
        }

        // go into each switch, and set the reference of all the inter-block output ports
        for (int deviceID : this.idToNetworkDevice.keySet()) {
            if (this.idToNetworkDevice.get(deviceID).isServer()) {
                continue;
            }
            BlockUgalGQueueBasedSwitch currentSwitch = (BlockUgalGQueueBasedSwitch) this.idToNetworkDevice.get(deviceID);

            currentSwitch.setInterblockOutputPorts(interBlockOutputPorts);
        }
    }

    @Override
    public void populateRoutingTables() {

        GraphDetails details = getConfiguration().getGraphDetails();
        Graph graph = getConfiguration().getGraph();

        // Populate ECMP routing state
        for (int identifier : idToNetworkDevice.keySet()) {
            BlockUgalGQueueBasedSwitch device = (BlockUgalGQueueBasedSwitch) idToNetworkDevice.get(identifier);
            if (!device.isServer()) {
                int blockID = device.getBlockID();
                this.switchIDToBlockID.put(identifier, blockID);
                if (!this.blockIDToSwitchIDs.containsKey(blockID)) {
                    this.blockIDToSwitchIDs.put(blockID, new HashSet<Integer>());
                }
                this.blockIDToSwitchIDs.get(blockID).add(identifier);
            }
        }
        
        // Step 0 : read in the switches and what block they belong to.
        // this.assignSwitchIDToCorrespondingBlockID();

        this.populateRoutingTablesIntraBlock(graph, details);

        HashMap<Integer, Integer> serverIDToBlockIDMap = new HashMap<Integer, Integer>();
        for (int serverID : details.getServerNodeIds()) {
            BlockUgalGQueueBasedSwitch device = (BlockUgalGQueueBasedSwitch) this.idToNetworkDevice.get(serverID);
            int torID = device.getToRID();
            serverIDToBlockIDMap.put(serverID, this.switchIDToBlockID.get(torID));
        }
        for (int identifier : this.idToNetworkDevice.keySet()) {
            BlockUgalGQueueBasedSwitch device = (BlockUgalGQueueBasedSwitch) this.idToNetworkDevice.get(identifier);
            device.setServerIDToBlockIDMap(serverIDToBlockIDMap);
        }
        // Step 1 : read in the routing weights for each switch
        // Create graph and prepare shortest path algorithm


        // Adding for each switch in each block, where are the entry switches to each target block ID.
        // only valid for BlockUgalGQueueBasedSwitch.
        for (int blockID : blockIDToSwitchIDs.keySet()) {
            HashSet<Integer> switchIDsInBlock = blockIDToSwitchIDs.get(blockID);

            HashMap<Integer, HashSet<Integer>> entrySwitchesToTargetBlocks = new HashMap<Integer, HashSet<Integer>>();

            for (int switchID : switchIDsInBlock) {
                Vertex switchVertex = graph.getVertex(switchID);
                List<Vertex> neighborVertices = graph.getAdjacentVertices( switchVertex );
                for (Vertex neighbor : neighborVertices) {
                    // check if neighbor is a server or a switch
                    if (idToNetworkDevice.get(neighbor.getId()).isServer()) {
                        continue;
                    }
                    int neighborBlockID = this.switchIDToBlockID.get(neighbor.getId());
                    if (neighborBlockID != blockID) {
                        if (!entrySwitchesToTargetBlocks.containsKey(neighborBlockID)) {
                            entrySwitchesToTargetBlocks.put(neighborBlockID, new HashSet<Integer>());
                        }
                        entrySwitchesToTargetBlocks.get(neighborBlockID).add(switchID);
                    }
                }
            }

            for (int switchID : switchIDsInBlock) {
                BlockUgalGQueueBasedSwitch currentSwitchEntity = (BlockUgalGQueueBasedSwitch) idToNetworkDevice.get(switchID);
                for (int targetBlockID : entrySwitchesToTargetBlocks.keySet()) {
                    for (int entrySwitch : entrySwitchesToTargetBlocks.get(targetBlockID)) {
                        currentSwitchEntity.addEntrySwitchToDestinationBlockID(targetBlockID, entrySwitch);
                    }
                }
            }
        }

        // assign the interblock output ports 
        locateAllInterBlockOutputPorts(graph);
    }
}
