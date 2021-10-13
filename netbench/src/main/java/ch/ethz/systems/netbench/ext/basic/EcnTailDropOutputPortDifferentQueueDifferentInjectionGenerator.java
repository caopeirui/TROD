package ch.ethz.systems.netbench.ext.basic;

import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.Link;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.core.run.infrastructure.OutputPortGenerator;
import ch.ethz.systems.netbench.core.Simulator;
import edu.asu.emit.algorithm.graph.Graph;
import edu.asu.emit.algorithm.graph.Vertex;

import java.util.Set;

public class EcnTailDropOutputPortDifferentQueueDifferentInjectionGenerator extends OutputPortGenerator {

    private final long singleMaxQueueSizeBytes;
    private final long singleEcnThresholdKBytes;
    private final long injectionEjectionMultiplicity;

    public EcnTailDropOutputPortDifferentQueueDifferentInjectionGenerator(long maxQueueSizeBytes, long ecnThresholdKBytes, long injectionEjectionMultiplicityArg) {
        this.singleMaxQueueSizeBytes = maxQueueSizeBytes;
        this.singleEcnThresholdKBytes = ecnThresholdKBytes;
        this.injectionEjectionMultiplicity = injectionEjectionMultiplicityArg;
        SimulationLogger.logInfo("Port", "ECN_TAIL_DROP_INJECTION_MULTIPLICITY(singleMaxQueueSizeBytes=" + maxQueueSizeBytes + ", singleEcnThresholdKBytes=" + ecnThresholdKBytes + ")");
    }

    @Override
    public OutputPort generate(NetworkDevice ownNetworkDevice, NetworkDevice towardsNetworkDevice, Link link) {
        Graph graph = Simulator.getConfiguration().getGraph();
        GraphDetails graphDetails = Simulator.getConfiguration().getGraphDetails();
        Set<Integer> setOfServers = graphDetails.getServerNodeIds();

        Vertex src = graph.getVertex(ownNetworkDevice.getIdentifier());
        Vertex dst = graph.getVertex(towardsNetworkDevice.getIdentifier());
        long edgeWeight = graph.getEdgeWeight(src, dst);
        if (setOfServers.contains(src) || setOfServers.contains(dst)) {
            return new EcnTailDropOutputPort(ownNetworkDevice, 
                                        towardsNetworkDevice, 
                                        link, 
                                        edgeWeight * injectionEjectionMultiplicity * singleMaxQueueSizeBytes, 
                                        edgeWeight * injectionEjectionMultiplicity * singleEcnThresholdKBytes);
        }
        return new EcnTailDropOutputPort(ownNetworkDevice, 
                                        towardsNetworkDevice, 
                                        link, 
                                        edgeWeight * singleMaxQueueSizeBytes, 
                                        edgeWeight * singleEcnThresholdKBytes);
    }

}
