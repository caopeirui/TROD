package ch.ethz.systems.netbench.ext.wcmp;

import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.run.infrastructure.IntermediaryGenerator;
import ch.ethz.systems.netbench.core.run.infrastructure.NetworkDeviceGenerator;

public class WcmpSwitchGenerator extends NetworkDeviceGenerator {

    private final int numNodes;
    private final IntermediaryGenerator intermediaryGenerator;

    public WcmpSwitchGenerator(IntermediaryGenerator intermediaryGenerator, int numNodes) {
        SimulationLogger.logInfo("Network device", "WCMP_SWITCH(numNodes=" + numNodes + ")");

        // Standard fields
        this.numNodes = numNodes;
        this.intermediaryGenerator = intermediaryGenerator;

    }

    @Override
    public NetworkDevice generate(int identifier) {
        return this.generate(identifier, null);
    }

    @Override
    public NetworkDevice generate(int identifier, TransportLayer transportLayer) {
        return new WcmpSwitch(identifier, transportLayer, numNodes, intermediaryGenerator.generate(identifier));
    }

}
