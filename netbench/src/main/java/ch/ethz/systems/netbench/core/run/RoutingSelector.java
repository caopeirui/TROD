package ch.ethz.systems.netbench.core.run;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.config.exceptions.PropertyValueInvalidException;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.run.routing.RoutingPopulator;
import ch.ethz.systems.netbench.ext.ecmp.EcmpSwitchRouting;
import ch.ethz.systems.netbench.ext.ecmp.ForwarderSwitchRouting;
import ch.ethz.systems.netbench.ext.threshold_routing.ThresholdSwitchRouting;
import ch.ethz.systems.netbench.ext.wcmp.WcmpSwitchRouting;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurablePodRouting;
import ch.ethz.systems.netbench.xpt.blockvaliant.BlockValiantRouting;
import ch.ethz.systems.netbench.xpt.infiniband.reconfigurableIB.ReconfigurablePodInfinibandRouting;
import ch.ethz.systems.netbench.xpt.infiniband.simpleIB.InfinibandECMPRouting;
import ch.ethz.systems.netbench.xpt.sourcerouting.EcmpThenKspNoShortestRouting;
import ch.ethz.systems.netbench.xpt.sourcerouting.EcmpThenKspRouting;
import ch.ethz.systems.netbench.xpt.sourcerouting.KShortestPathsSwitchRouting;
import ch.ethz.systems.netbench.xpt.trafficawaresourcerouting.TrafficAwareSourceRouting;
import ch.ethz.systems.netbench.xpt.ugal.BlockUgalGQueueBasedRouting;
import ch.ethz.systems.netbench.xpt.ugal.BlockUgalLQueueBasedRouting;
import java.util.Map;



public class RoutingSelector {

    /**
     * Select the populator which populates the routing state in all network devices.
     *
     * Selected using following property:
     * network_device_routing=...
     *
     * @param idToNetworkDevice     Identifier to instantiated network device
     */
    public static RoutingPopulator selectPopulator(Map<Integer, NetworkDevice> idToNetworkDevice) {

        switch (Simulator.getConfiguration().getPropertyOrFail("network_device_routing")) {

            case "single_forward": {
                return new ForwarderSwitchRouting(
                        idToNetworkDevice
                );
            }

            case "ecmp": {
                return new EcmpSwitchRouting(
                        idToNetworkDevice
                );
            }

            case "wcmp": {
                String filename = Simulator.getConfiguration().getPropertyWithDefault("wcmp_path_weights_filename", "");
                return new WcmpSwitchRouting(
                        idToNetworkDevice, 
                        filename
                );
            }

            case "threshold_routing": {
                String filename = Simulator.getConfiguration().getPropertyWithDefault("threshold_path_weights_filename", "");
                return new ThresholdSwitchRouting(
                        idToNetworkDevice, 
                        filename
                );
            }
            /*case "k_paths": {
                return new KPathsSwitchRouting(
                        idToNetworkDevice
                );
            }*/

            case "reconfigurable_switch_routing": {
                String wcmpPathWeightsFilename = Simulator.getConfiguration().getPropertyOrFail("wcmp_path_weights_filename");
                // the WCMP of interpod routing
                return new ReconfigurablePodRouting(idToNetworkDevice, wcmpPathWeightsFilename);
            }

            case "reconfigurable_infiniband_switch_routing": {
                String wcmpPathWeightsFilename = Simulator.getConfiguration().getPropertyOrFail("wcmp_path_weights_filename");
                // the WCMP of interpod routing
                return new ReconfigurablePodInfinibandRouting(idToNetworkDevice, wcmpPathWeightsFilename);
            }

            case "simple_infiniband_ecmp": {
                // the WCMP of interpod routing
                return new InfinibandECMPRouting(idToNetworkDevice);
            }

            case "traffic_aware_source_routing": {
                String blockWeightFilename = Simulator.getConfiguration().getPropertyOrFail("routing_weight_filename");
                return new TrafficAwareSourceRouting(
                        idToNetworkDevice, 
                        blockWeightFilename
                );
            }

            case "block_valiant_routing": {
                return new BlockValiantRouting (
                        idToNetworkDevice
                );

            }

            case "block_ugalG_queue_based_routing": {
                return new BlockUgalGQueueBasedRouting (
                        idToNetworkDevice
                );

            }

            case "block_ugalL_queue_based_routing": {
                return new BlockUgalLQueueBasedRouting (
                        idToNetworkDevice
                );

            }

            case "k_shortest_paths": {
                return new KShortestPathsSwitchRouting(
                        idToNetworkDevice
                );
            }

            case "ecmp_then_k_shortest_paths": {
                return new EcmpThenKspRouting(
                        idToNetworkDevice
                );
            }

            case "ecmp_then_k_shortest_paths_without_shortest": {
                return new EcmpThenKspNoShortestRouting(
                        idToNetworkDevice
                );
            }

            default:
                throw new PropertyValueInvalidException(
                        Simulator.getConfiguration(),
                        "network_device_routing"
                );

        }

    }

}
