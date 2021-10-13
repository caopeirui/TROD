package ch.ethz.systems.netbench.ext.threshold_routing;

import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.run.routing.RoutingPopulator;
import java.util.Map;


public class ThresholdSwitchRouting extends RoutingPopulator {

    private final Map<Integer, NetworkDevice> idToNetworkDevice;
    private final String pathWeightsFilename;

    public ThresholdSwitchRouting(Map<Integer, NetworkDevice> idToNetworkDevice, String ThresholdPathFilename) {
        this.idToNetworkDevice = idToNetworkDevice;
        this.pathWeightsFilename = ThresholdPathFilename;
        SimulationLogger.logInfo("Routing", "Threshold");
    }

    /**
     * Initialize the multi-forwarding routing tables in the network devices.
     */
    @Override
    public void populateRoutingTables() {
        ThresholdRoutingUtility.populatePathRoutingTables(idToNetworkDevice, this.pathWeightsFilename);
    }

}
