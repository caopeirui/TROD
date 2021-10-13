package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_outport;

import edu.asu.emit.algorithm.graph.Graph;
import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.Link;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.OutputPort;
import ch.ethz.systems.netbench.core.run.infrastructure.OutputPortGenerator;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitch;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_link.ReconfigurableLink;

public class ReconfigurableOutputPortGenerator extends OutputPortGenerator {

    private final long maxQueueSizeBytes;
    private final long ecnThresholdKBytes;
    private final long reconfigLatencyNs;

    public ReconfigurableOutputPortGenerator(long maxQueueSizeBytes, long ecnThresholdKBytes, long reconfigLatencyNsArg) {
        this.maxQueueSizeBytes = maxQueueSizeBytes;
        this.ecnThresholdKBytes = ecnThresholdKBytes;
        this.reconfigLatencyNs = reconfigLatencyNsArg;
        System.out.println("\n\n\n\nThe reconfiguration latency is: " + reconfigLatencyNsArg + "\n\n\n\n");
        SimulationLogger.logInfo("Port", "ReconfigurableOutputPortGenerator(maxQueueSizeBytes=" + maxQueueSizeBytes + ", ecnThresholdKBytes=" + ecnThresholdKBytes + ")");
    }

    @Override
    public OutputPort generate(NetworkDevice ownNetworkDevice, NetworkDevice towardsNetworkDevice, Link link) {
        int srcNetworkDeviceId = ownNetworkDevice.getIdentifier();
        int targetNetworkDeviceId = towardsNetworkDevice.getIdentifier();
        int srcPodId = ((ReconfigurableNetworkSwitch) ownNetworkDevice).getPodId();
        int targetPodId = ((ReconfigurableNetworkSwitch) towardsNetworkDevice).getPodId();
        Graph graph = Simulator.getConfiguration().getGraph();
        long multiplicity = graph.getEdgeWeight(graph.getVertex(srcNetworkDeviceId), graph.getVertex(targetNetworkDeviceId));
        // check whether if the src and dst switches belong to the same pod. 
        boolean isStatic = false;
        if (targetPodId == srcPodId) {
            isStatic = true;
        }
        return new ReconfigurableOutputPort(ownNetworkDevice, towardsNetworkDevice, (ReconfigurableLink) link, maxQueueSizeBytes, ecnThresholdKBytes, reconfigLatencyNs, multiplicity, isStatic);
    }

}
