package ch.ethz.systems.netbench.ext.wcmp;

import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.run.routing.RoutingPopulator;

import java.util.Map;

public class WcmpSwitchRouting extends RoutingPopulator {

    private final Map<Integer, NetworkDevice> idToNetworkDevice;
    private final String pathWeightsFilename;

    public WcmpSwitchRouting(Map<Integer, NetworkDevice> idToNetworkDevice, String wcmpPathFilename) {
        this.idToNetworkDevice = idToNetworkDevice;
        this.pathWeightsFilename = wcmpPathFilename;
        SimulationLogger.logInfo("Routing", "WCMP");
    }

    /**
     * Initialize the multi-forwarding routing tables in the network devices.
     */
    @Override
    public void populateRoutingTables() {
        WcmpRoutingUtility.populatePathRoutingTables(idToNetworkDevice, this.pathWeightsFilename);
    }

}
