package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_link;

import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.Link;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.run.infrastructure.LinkGenerator;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableNetworkSwitch;

public class ReconfigurableLinkGenerator extends LinkGenerator {

    private final long delayNs;
    private final long delayNsServer; // delay of wires between ToRs and servers
    private final long bandwidthBitPerNs;

    public ReconfigurableLinkGenerator(long delayNsArg, long delayNsServerArg, long bandwidthBitPerNs) {
        this.delayNs = delayNsArg;
        this.delayNsServer = delayNsServerArg;
        this.bandwidthBitPerNs = bandwidthBitPerNs;
        SimulationLogger.logInfo("Link", "ReconfigurableLinkGenerator(delayNs=" + delayNs + ", bandwidthBitPerNs=" + bandwidthBitPerNs + ")");
    }

    @Override
    public Link generate(NetworkDevice fromNetworkDevice, NetworkDevice toNetworkDevice, long link_multiplicity) {
        // ReconfigurableNetworkSwitch srcSwitch = (ReconfigurableNetworkSwitch) fromNetworkDevice;
        // ReconfigurableNetworkSwitch dstSwitch = (ReconfigurableNetworkSwitch) toNetworkDevice;
        // figure out whether either the source switch or the dst switch is a server
        
        long delay = this.delayNs;
        if (fromNetworkDevice.isServer() || toNetworkDevice.isServer()) {
            delay = this.delayNsServer;
        }
        return new ReconfigurableLink(delay, bandwidthBitPerNs, link_multiplicity);
    }
}
