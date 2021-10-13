package ch.ethz.systems.netbench.xpt.infiniband.simpleIB;

import edu.asu.emit.algorithm.graph.Graph;
import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.Link;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.core.run.infrastructure.OutputPortGenerator;


public class SimpleInfinibandOutputPortGenerator extends OutputPortGenerator {

    private final long maxQueueSizeBytes;
    private final int numVCs;

    public SimpleInfinibandOutputPortGenerator(long maxQueueSizeBytes) {
        this.maxQueueSizeBytes = maxQueueSizeBytes;
        this.numVCs = Simulator.getConfiguration().getIntegerPropertyWithDefault("num_vcs", 1);
        assert(this.numVCs >= 1);
        SimulationLogger.logInfo("Port", "SimpleInfinibandOutputPortGenerator(maxQueueSizeBytes=" + maxQueueSizeBytes + ")");
    }

    @Override
    public OutputPort generate(NetworkDevice ownNetworkDevice, NetworkDevice towardsNetworkDevice, Link link) {
        int srcNetworkDeviceId = ownNetworkDevice.getIdentifier();
        int targetNetworkDeviceId = towardsNetworkDevice.getIdentifier();
        Graph graph = Simulator.getConfiguration().getGraph();
        long multiplicity = graph.getEdgeWeight(graph.getVertex(srcNetworkDeviceId), graph.getVertex(targetNetworkDeviceId));
        // check whether if the src and dst switches belong to the same pod. 
        if (numVCs > 1) {
            return new SimpleInfinibandVCOutputPort(ownNetworkDevice, towardsNetworkDevice, link, multiplicity * maxQueueSizeBytes, numVCs);
        } else {
            return new SimpleInfinibandOutputPort(ownNetworkDevice, towardsNetworkDevice, link, multiplicity * maxQueueSizeBytes);
        }
    }

}
