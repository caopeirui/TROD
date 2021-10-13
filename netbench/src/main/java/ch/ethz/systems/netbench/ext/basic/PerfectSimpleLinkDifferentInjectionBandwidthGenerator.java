package ch.ethz.systems.netbench.ext.basic;

import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.Link;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.run.infrastructure.LinkGenerator;

public class PerfectSimpleLinkDifferentInjectionBandwidthGenerator extends LinkGenerator {

    private final long delayNs;
    private final long networkBandwidthBitPerNs;
    private final long injectionBandwidthBitPerNs;

    public PerfectSimpleLinkDifferentInjectionBandwidthGenerator(long delayNs, long networkBandwidthBitPerNs, long injectionBandwidthBitPerNs) {
        this.delayNs = delayNs;
        this.networkBandwidthBitPerNs = networkBandwidthBitPerNs;
        this.injectionBandwidthBitPerNs = injectionBandwidthBitPerNs;
        SimulationLogger.logInfo("Link", 
                                    "PERFECT_SIMPLE_LINK_DIFF_INJECTION(delayNs=" + 
                                    delayNs + 
                                    ", networkBandwidthBitPerNs=" + 
                                    networkBandwidthBitPerNs + 
                                    ", injectionBandwidthBitPerNs=" + 
                                    injectionBandwidthBitPerNs + 
                                    ")");
    }

    @Override
    public Link generate(NetworkDevice fromNetworkDevice, NetworkDevice toNetworkDevice, long link_multiplicity) {
        long actualBandwidth;
        if (fromNetworkDevice.isServer() || toNetworkDevice.isServer()) {
            actualBandwidth = link_multiplicity * this.injectionBandwidthBitPerNs;
        } else {
            actualBandwidth = link_multiplicity * this.networkBandwidthBitPerNs; 
        }
        return new PerfectSimpleLink(delayNs, actualBandwidth);
    }

}
