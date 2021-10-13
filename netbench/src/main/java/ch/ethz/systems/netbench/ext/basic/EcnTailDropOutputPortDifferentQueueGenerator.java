package ch.ethz.systems.netbench.ext.basic;

import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.Link;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.core.run.infrastructure.OutputPortGenerator;
import ch.ethz.systems.netbench.core.Simulator;
import edu.asu.emit.algorithm.graph.Graph;
import edu.asu.emit.algorithm.graph.Vertex;


public class EcnTailDropOutputPortDifferentQueueGenerator extends OutputPortGenerator {

    private final long singleMaxQueueSizeBytes;
    private final long singleEcnThresholdKBytes;

    public EcnTailDropOutputPortDifferentQueueGenerator(long maxQueueSizeBytes, long ecnThresholdKBytes) {
        this.singleMaxQueueSizeBytes = maxQueueSizeBytes;
        this.singleEcnThresholdKBytes = ecnThresholdKBytes;
        SimulationLogger.logInfo("Port", "ECN_TAIL_DROP(singleMaxQueueSizeBytes=" + maxQueueSizeBytes + ", singleEcnThresholdKBytes=" + ecnThresholdKBytes + ")");
    }

    @Override
    public OutputPort generate(NetworkDevice ownNetworkDevice, NetworkDevice towardsNetworkDevice, Link link) {
        Graph graph = Simulator.getConfiguration().getGraph();
        Vertex src = graph.getVertex(ownNetworkDevice.getIdentifier());
        Vertex dst = graph.getVertex(towardsNetworkDevice.getIdentifier());
        long edgeWeight = graph.getEdgeWeight(src, dst);
        return new EcnTailDropOutputPort(ownNetworkDevice, 
                                        towardsNetworkDevice, 
                                        link, 
                                        edgeWeight * singleMaxQueueSizeBytes, 
                                        edgeWeight * singleEcnThresholdKBytes);
    }

}
