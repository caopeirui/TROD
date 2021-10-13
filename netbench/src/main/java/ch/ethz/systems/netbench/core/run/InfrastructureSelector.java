package ch.ethz.systems.netbench.core.run;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.config.exceptions.PropertyValueInvalidException;
import ch.ethz.systems.netbench.core.run.infrastructure.IntermediaryGenerator;
import ch.ethz.systems.netbench.core.run.infrastructure.LinkGenerator;
import ch.ethz.systems.netbench.core.run.infrastructure.NetworkDeviceGenerator;
import ch.ethz.systems.netbench.core.run.infrastructure.OutputPortGenerator;
import ch.ethz.systems.netbench.core.run.infrastructure.TransportLayerGenerator;
import ch.ethz.systems.netbench.ext.bare.BareTransportLayerGenerator;
import ch.ethz.systems.netbench.ext.basic.EcnTailDropOutputPortGenerator;
import ch.ethz.systems.netbench.ext.basic.EcnTailDropOutputPortDifferentQueueGenerator;
import ch.ethz.systems.netbench.ext.basic.EcnTailDropOutputPortDifferentQueueDifferentInjectionGenerator;
import ch.ethz.systems.netbench.ext.basic.PerfectSimpleLinkGenerator;
import ch.ethz.systems.netbench.ext.basic.PerfectSimpleLinkDifferentInjectionBandwidthGenerator;
import ch.ethz.systems.netbench.ext.demo.DemoIntermediaryGenerator;
import ch.ethz.systems.netbench.ext.demo.DemoTransportLayerGenerator;
import ch.ethz.systems.netbench.ext.ecmp.EcmpSwitchGenerator;
import ch.ethz.systems.netbench.ext.ecmp.ForwarderSwitchGenerator;
import ch.ethz.systems.netbench.ext.wcmp.WcmpSwitchGenerator;
import ch.ethz.systems.netbench.ext.threshold_routing.ThresholdSwitchGenerator;
import ch.ethz.systems.netbench.ext.flowlet.IdentityFlowletIntermediaryGenerator;
import ch.ethz.systems.netbench.ext.flowlet.UniformFlowletIntermediaryGenerator;
import ch.ethz.systems.netbench.ext.hybrid.EcmpThenValiantSwitchGenerator;
import ch.ethz.systems.netbench.ext.valiant.RangeValiantSwitchGenerator;
import ch.ethz.systems.netbench.xpt.asaf.routing.priority.PriorityFlowletIntermediaryGenerator;
import ch.ethz.systems.netbench.xpt.newreno.newrenodctcp.NewRenoDctcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.newreno.newrenotcp.NewRenoTcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.simple.simpledctcp.SimpleDctcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.simple.simpletcp.SimpleTcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.sourcerouting.EcmpThenSourceRoutingSwitchGenerator;
import ch.ethz.systems.netbench.xpt.sourcerouting.SourceRoutingSwitchGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.ports.BoundedPriorityOutputPortGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.ports.PriorityOutputPortGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.ports.UnlimitedOutputPortGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.tcp.buffertcp.BufferTcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.tcp.distmeantcp.DistMeanTcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.tcp.distrandtcp.DistRandTcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.tcp.lstftcp.LstfTcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.tcp.pfabric.PfabricTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.tcp.pfzero.PfzeroTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.tcp.sparktcp.SparkTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.tcp.sphalftcp.SpHalfTcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.voijslav.tcp.sptcp.SpTcpTransportLayerGenerator;
import ch.ethz.systems.netbench.xpt.trafficawaresourcerouting.TrafficAwareSourceRoutingSwitchGenerator;
import ch.ethz.systems.netbench.xpt.blockvaliant.BlockValiantSwitchGenerator;
import ch.ethz.systems.netbench.xpt.ugal.BlockUgalGQueueBasedSwitchGenerator;
import ch.ethz.systems.netbench.xpt.ugal.BlockUgalLQueueBasedSwitchGenerator;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.ReconfigurableSwitchGenerator;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_link.ReconfigurableLinkGenerator;
import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_outport.ReconfigurableOutputPortGenerator;
import ch.ethz.systems.netbench.xpt.infiniband.simpleIB.SimpleInfinibandSwitchGenerator;
import ch.ethz.systems.netbench.xpt.infiniband.simpleIB.SimpleInfinibandOutputPortGenerator;
import ch.ethz.systems.netbench.xpt.infiniband.reconfigurableIB.ReconfigurableInfinibandSwitchGenerator;
import ch.ethz.systems.netbench.xpt.infiniband.reconfigurableIB.ReconfigurableInfinibandOutputPortGenerator;
import ch.ethz.systems.netbench.xpt.infiniband.InfinibandTransportLayerGenerator;


class InfrastructureSelector {

    private InfrastructureSelector() {
        // Only static class
    }

    /**
     * Select the network device generator, which, given its identifier,
     * generates an appropriate network device (possibly with transport layer).
     *
     * Selected using following properties:
     * network_device=...
     * network_device_intermediary=...
     *
     * @return  Network device generator.
     */
    static NetworkDeviceGenerator selectNetworkDeviceGenerator() {

        /*
         * Select intermediary generator.
         */
        IntermediaryGenerator intermediaryGenerator;
        switch (Simulator.getConfiguration().getPropertyOrFail("network_device_intermediary")) {

            case "demo": {
                intermediaryGenerator = new DemoIntermediaryGenerator();
                break;
            }

            case "identity": {
                intermediaryGenerator = new IdentityFlowletIntermediaryGenerator();
                break;
            }

            case "uniform": {
                intermediaryGenerator = new UniformFlowletIntermediaryGenerator();
                break;
            }

            case "low_high_priority": {
                intermediaryGenerator = new PriorityFlowletIntermediaryGenerator();
                break;
            }

            default:
                throw new PropertyValueInvalidException(
                        Simulator.getConfiguration(),
                        "network_device_intermediary"
                );

        }

        /*
         * Select network device generator.
         */
        switch (Simulator.getConfiguration().getPropertyOrFail("network_device")) {

            case "forwarder_switch":
                return new ForwarderSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "ecmp_switch":
                return new EcmpSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "wcmp_switch":
                return new WcmpSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "threshold_switch":
                return new ThresholdSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "traffic_aware_source_routing_switch":
                return new TrafficAwareSourceRoutingSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "block_valiant_switch":
                return new BlockValiantSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "block_ugalg_queue_switch":
                return new BlockUgalGQueueBasedSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "block_ugall_queue_switch":
                return new BlockUgalLQueueBasedSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "random_valiant_ecmp_switch":
                return new RangeValiantSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "ecmp_then_random_valiant_ecmp_switch":
                return new EcmpThenValiantSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "source_routing_switch":
                return new SourceRoutingSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "ecmp_then_source_routing_switch":
                return new EcmpThenSourceRoutingSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "reconfigurable_switch":
                return new ReconfigurableSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes(), Simulator.getConfiguration().getPropertyOrFail("pod_id_filename"));

            case "simple_infiniband_switch":
                return new SimpleInfinibandSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes());

            case "reconfigurable_infiniband_switch":
                return new ReconfigurableInfinibandSwitchGenerator(intermediaryGenerator, Simulator.getConfiguration().getGraphDetails().getNumNodes(), Simulator.getConfiguration().getPropertyOrFail("pod_id_filename"));

            default:
                throw new PropertyValueInvalidException(
                        Simulator.getConfiguration(),
                        "network_device"
                );

        }

    }

    /**
     * Select the link generator which creates a link instance given two
     * directed network devices.
     *
     * Selected using following property:
     * link=...
     *
     * @return  Link generator
     */
    static LinkGenerator selectLinkGenerator() {

        switch (Simulator.getConfiguration().getPropertyOrFail("link")) {

            case "perfect_simple":
                return new PerfectSimpleLinkGenerator(
                        Simulator.getConfiguration().getLongPropertyOrFail("link_delay_ns"),
                        Simulator.getConfiguration().getLongPropertyOrFail("link_bandwidth_bit_per_ns")
                );

            case "perfect_simple_different_injection_bandwidth":
                return new PerfectSimpleLinkDifferentInjectionBandwidthGenerator(
                        Simulator.getConfiguration().getLongPropertyOrFail("link_delay_ns"),
                        Simulator.getConfiguration().getLongPropertyOrFail("link_bandwidth_bit_per_ns"),
                        Simulator.getConfiguration().getLongPropertyOrFail("injection_link_bandwidth_bit_per_ns")
                );

            case "reconfigurable_link":
                return new ReconfigurableLinkGenerator( // might also need the different link latency in injection interpod and intrapod links
                    Simulator.getConfiguration().getLongPropertyOrFail("link_delay_ns"), 
                    Simulator.getConfiguration().getLongPropertyOrFail("server_link_delay_ns"), 
                    Simulator.getConfiguration().getLongPropertyOrFail("link_bandwidth_bit_per_ns")
                );

            default:
                throw new PropertyValueInvalidException(
                        Simulator.getConfiguration(),
                        "link"
                );

        }

    }

    /**
     * Select the output port generator which creates a port instance given two
     * directed network devices and the corresponding link.
     *
     * Selected using following property:
     * output_port=...
     *
     * @return  Output port generator
     */
    static OutputPortGenerator selectOutputPortGenerator() {
        System.out.println("The name of output generator is: " + Simulator.getConfiguration().getPropertyOrFail("output_port"));
        switch (Simulator.getConfiguration().getPropertyOrFail("output_port")) {

            case "ecn_tail_drop":

                return new EcnTailDropOutputPortGenerator(
                        Simulator.getConfiguration().getLongPropertyOrFail("output_port_max_queue_size_bytes"),
                        Simulator.getConfiguration().getLongPropertyOrFail("output_port_ecn_threshold_k_bytes")
                );

            case "ecn_tail_drop_diff_queue_size":

                return new EcnTailDropOutputPortDifferentQueueGenerator(
                        Simulator.getConfiguration().getLongPropertyOrFail("output_port_max_queue_size_bytes"),
                        Simulator.getConfiguration().getLongPropertyOrFail("output_port_ecn_threshold_k_bytes")
                );

            case "ecn_tail_drop_diff_queue_size_different_injection":

                return new EcnTailDropOutputPortDifferentQueueDifferentInjectionGenerator(
                        Simulator.getConfiguration().getLongPropertyOrFail("output_port_max_queue_size_bytes"),
                        Simulator.getConfiguration().getLongPropertyOrFail("output_port_ecn_threshold_k_bytes"),
                        Simulator.getConfiguration().getLongPropertyWithDefault("injection_queue_multiplier", 10)
                );

            case "reconfigurable_output_port":

                return new ReconfigurableOutputPortGenerator(
                    Simulator.getConfiguration().getLongPropertyOrFail("output_port_max_queue_size_bytes"),
                    Simulator.getConfiguration().getLongPropertyOrFail("output_port_ecn_threshold_k_bytes"),
                    Simulator.getConfiguration().getLongPropertyOrFail("link_reconfig_latency_ns")
                );

            case "simple_infiniband_output_port":

                return new SimpleInfinibandOutputPortGenerator(
                    Simulator.getConfiguration().getLongPropertyOrFail("output_port_max_queue_size_bytes")
                );

            case "reconfigurable_infiniband_output_port":

                return new ReconfigurableInfinibandOutputPortGenerator(
                    Simulator.getConfiguration().getLongPropertyOrFail("output_port_max_queue_size_bytes"),
                    Simulator.getConfiguration().getLongPropertyOrFail("link_reconfig_latency_ns")
                );

            case "priority":
                return new PriorityOutputPortGenerator();

            case "bounded_priority":
                return new BoundedPriorityOutputPortGenerator(
                        Simulator.getConfiguration().getLongPropertyOrFail("output_port_max_queue_size_bytes")*8
                );

            case "unlimited":
                return new UnlimitedOutputPortGenerator();

            default:
                throw new PropertyValueInvalidException(
                        Simulator.getConfiguration(),
                        "output_port"
                );

        }

    }

    /**
     * Select the transport layer generator.
     *
     * @return  Transport layer generator
     */
    static TransportLayerGenerator selectTransportLayerGenerator() {

        switch (Simulator.getConfiguration().getPropertyOrFail("transport_layer")) {

            case "demo":
                return new DemoTransportLayerGenerator();

            case "bare":
                return new BareTransportLayerGenerator();

            case "tcp":
                return new NewRenoTcpTransportLayerGenerator();

            case "lstf_tcp":
                return new LstfTcpTransportLayerGenerator();

            case "sp_tcp":
                return new SpTcpTransportLayerGenerator();

            case "sp_half_tcp":
                return new SpHalfTcpTransportLayerGenerator();

            case "pfabric":
                return new PfabricTransportLayerGenerator();
                
            case "pfzero":
                return new PfzeroTransportLayerGenerator();
                
            case "buffertcp":
                return new BufferTcpTransportLayerGenerator();

            case "distmean":
                return new DistMeanTcpTransportLayerGenerator();

            case "distrand":
                return new DistRandTcpTransportLayerGenerator();
            
            case "sparktcp":
                return new SparkTransportLayerGenerator();
                
            case "dctcp":
                return new NewRenoDctcpTransportLayerGenerator();

            case "simple_tcp":
                return new SimpleTcpTransportLayerGenerator();

            case "simple_dctcp":
                return new SimpleDctcpTransportLayerGenerator();

            case "infiniband":
                return new InfinibandTransportLayerGenerator();

            default:
                throw new PropertyValueInvalidException(
                        Simulator.getConfiguration(),
                        "transport_layer"
                );

        }

    }

}
