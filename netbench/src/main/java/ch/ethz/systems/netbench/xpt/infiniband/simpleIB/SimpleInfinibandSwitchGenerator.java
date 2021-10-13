package ch.ethz.systems.netbench.xpt.infiniband.simpleIB;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.run.infrastructure.IntermediaryGenerator;
import ch.ethz.systems.netbench.core.run.infrastructure.NetworkDeviceGenerator;

public class SimpleInfinibandSwitchGenerator extends NetworkDeviceGenerator {

    private final int numNodes;
    private final IntermediaryGenerator intermediaryGenerator;
    private final int numVCs;

    public SimpleInfinibandSwitchGenerator(IntermediaryGenerator intermediaryGenerator, int numNodes) {

        // Standard fields
        this.numNodes = numNodes;
        this.intermediaryGenerator = intermediaryGenerator;
        this.numVCs = Simulator.getConfiguration().getIntegerPropertyWithDefault("num_vcs", 1);
        assert(this.numVCs >= 1);

        // Log creation
        SimulationLogger.logInfo("Network device", "SIMPLE_INFINIBAND_SWITCH(numNodes=" + numNodes + ")");

    }

    @Override
    public NetworkDevice generate(int identifier) {
        return this.generate(identifier, null);
    }

    @Override
    public NetworkDevice generate(int identifier, TransportLayer transportLayer) {
        // Input queue size
        long inputBufferMaxSizeBytes = Simulator.getConfiguration().getLongPropertyOrFail("infiniband_input_queue_size_bytes");
        if (this.numVCs > 1) {
            return new SimpleInfinibandVCSwitch(identifier, transportLayer, intermediaryGenerator.generate(identifier), inputBufferMaxSizeBytes * 8L, numVCs);
        } else {
            return new SimpleInfinibandSwitch(identifier, transportLayer, intermediaryGenerator.generate(identifier), inputBufferMaxSizeBytes * 8L);
        }
    }
}
