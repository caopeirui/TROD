package ch.ethz.systems.netbench.xpt.infiniband.reconfigurableIB;

import edu.asu.emit.algorithm.graph.Graph;
import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.Link;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.core.run.infrastructure.OutputPortGenerator;


public class ReconfigurableInfinibandOutputPortGenerator extends OutputPortGenerator {

    private final long unitMaxQueueSizeBytes;   // Queue size bytes for port with multiplicity of 1.
    private final long reconfigLatencyNs;   // Records the reconfiguration latency in nanoseconds.
    private final int numVCs;                   // Number of virtual channels

    public ReconfigurableInfinibandOutputPortGenerator(long maxQueueSizeBytes, long reconfigLatencyNs) {
        this.unitMaxQueueSizeBytes = maxQueueSizeBytes;
        this.reconfigLatencyNs = reconfigLatencyNs;
        this.numVCs = Simulator.getConfiguration().getIntegerPropertyWithDefault("num_vcs", 1);
        assert(this.numVCs > 0);
        SimulationLogger.logInfo("Port", "ReconfigurableInfinibandOutputPortGenerator(maxQueueSizeBytes=" + maxQueueSizeBytes + ")");
    }

    @Override
    public OutputPort generate(NetworkDevice ownNetworkDevice, NetworkDevice towardsNetworkDevice, Link link) {
        int srcNetworkDeviceId = ownNetworkDevice.getIdentifier();
        int targetNetworkDeviceId = towardsNetworkDevice.getIdentifier();
        Graph graph = Simulator.getConfiguration().getGraph();
        long initialMultiplicity = graph.getEdgeWeight(graph.getVertex(srcNetworkDeviceId), graph.getVertex(targetNetworkDeviceId));
        // check whether if the src and dst switches belong to the same pod. 
        if (this.numVCs == 1) { 
            return new ReconfigurableInfinibandOutputPort(ownNetworkDevice, towardsNetworkDevice, link, this.unitMaxQueueSizeBytes, this.reconfigLatencyNs, initialMultiplicity);
        } else {
            return new ReconfigurableInfinibandVCOutputPort(ownNetworkDevice, towardsNetworkDevice, link, this.unitMaxQueueSizeBytes, this.reconfigLatencyNs, initialMultiplicity, this.numVCs);
        }
    }

}
