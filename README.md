TROD is a Threshold Routing based Optical Datacenter design in ICNP 2021 paper: TROD: Evolving From Electrical Data Center to Optical Data Center.

Our simulation is extended from an open source network simulator NetBench (https://github.com/ndal-eth/netbench).

The details of our extended code in Netbench are described at [Our Extended Code](#our-extended-code)

## Getting Started

#### 1. Software dependencies

* **Java 8:** Version 8 of Java; both Oracle JDK and OpenJDK are supported and produce under that same seed deterministic results. Additionally the project uses the Apache Maven software project management and comprehension tool (version 3+).

* **Python 3:** Python3 has been installed and teminal can use command `python3`.

#### 2. Building

1. Compile and run all tests in the project, make sure that they all pass; this can be done using the following maven command: `mvn compile test`

2. Build the executable `NetBench.jar` by using the following maven command: `mvn clean compile assembly:single`

#### 3. Running

1. Execute a demo run by using the following command: `java -jar -ea NetBench.jar ./example/runs/demo.properties`

2. After the run, the log files are saved in the `./temp/demo` folder

3. If you have python 2 installed, you can view calculated statistics about flow completion and port utilization (e.g. mean FCT, 99th %-tile port utilization, ....) in the `./temp/demo/analysis` folder.

#### 4. Toy Example

Add the project root path into PYTHONPATH environmnet variables. eg:
```bash
vim ~/.bashrc

# add
export PYTHONPATH=$PYTHONPATH:"[Your path]"

# let it take effect
source ~/.bashrc
```

Trigger an experiment:
```
cd trigger_TROD_netbench
python3 main.py -r
```


## Our Extended Code

### Added List
```
netbench/src/main/java/ch/ethz/systems/netbench/core/run/reconfiguration/TopologyReconfigurationPlanner.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/CentralNetworkController.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/ReconfigurableNetworkSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/ReconfigurableNetworkSwitchInterface.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/ReconfigurablePodRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/ReconfigurableSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/RoutingUtility.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/proto/TopologyReconfigurationDetails.proto
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/proto/TopologyReconfigurationEventsProtos.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfigurable_link/ReconfigurableLink.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfigurable_link/ReconfigurableLinkGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfigurable_link/ReconfigurableLinkInterface.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfigurable_outport/ReconfigurableOutputPort.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfigurable_outport/ReconfigurableOutputPortGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfigurable_outport/ReconfigurableOutputPortInterface.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfiguration_planner/OneReconfigurationState.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfiguration_planner/ReconfigurationTimerEvent.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfiguration_planner/SignalPortReconfigurationCompletedEvent.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfiguration_planner/TopologyReconfigurationEpochsPlanner.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/bandwidth_steering/reconfiguration_planner/TriggerReconfigurationSwitchEvent.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/EcnTailDropOutputPortDifferentQueueDifferentInjectionGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/EcnTailDropOutputPortDifferentQueueGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/PerfectSimpleLinkDifferentInjectionBandwidthGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/FromFileArrivalPlanner.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/threshold_routing/ThresholdRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/threshold_routing/ThresholdRoutingUtility.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/threshold_routing/ThresholdSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/threshold_routing/ThresholdSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/threshold_routing/ThresholdSwitchRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/threshold_routing/ThresholdSwitchRoutingInterface.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/threshold_routing/TokenBucket.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/WcmpRoutingUtility.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/WcmpSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/WcmpSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/WcmpSwitchRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/WcmpSwitchRoutingInterface.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/loadbalancer/LoadBalancer.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/loadbalancer/StatefulLoadBalancer.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/loadbalancer/StatelessLoadBalancer.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/routingweight/Path.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/wcmp/routingweight/PathSplitWeights.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/blockvaliant/BlockValiantEcmpSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/blockvaliant/BlockValiantEncapsulation.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/blockvaliant/BlockValiantRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/blockvaliant/BlockValiantSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/trafficawaresourcerouting/BlockAwareSourceRoutingEncapsulation.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/trafficawaresourcerouting/BlockSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/trafficawaresourcerouting/IntraBlockRoutingUtility.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/trafficawaresourcerouting/RoutingPath.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/trafficawaresourcerouting/TrafficAwareSourceRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/trafficawaresourcerouting/TrafficAwareSourceRoutingSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/trafficawaresourcerouting/TrafficAwareSourceRoutingSwitchGenerator.java
```

### Modified List

```
netbench/src/main/java/ch/ethz/systems/netbench/core/Simulator.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/BaseAllowedProperties.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/GraphDetails.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/GraphReader.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/NBProperties.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/TopologyServerExtender.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/exceptions/ConfigurationReadFailException.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/exceptions/PropertyConflictException.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/exceptions/PropertyMissingException.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/exceptions/PropertyNotExistingException.java
netbench/src/main/java/ch/ethz/systems/netbench/core/config/exceptions/PropertyValueInvalidException.java
netbench/src/main/java/ch/ethz/systems/netbench/core/log/FlowLogger.java
netbench/src/main/java/ch/ethz/systems/netbench/core/log/LogFailureException.java
netbench/src/main/java/ch/ethz/systems/netbench/core/log/LoggerCallback.java
netbench/src/main/java/ch/ethz/systems/netbench/core/log/PortLogger.java
netbench/src/main/java/ch/ethz/systems/netbench/core/log/SimulationLogger.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/Event.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/Intermediary.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/Link.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/NetworkDevice.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/OutputPort.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/Packet.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/PacketArrivalEvent.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/PacketDispatchedEvent.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/PacketHeader.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/Socket.java
netbench/src/main/java/ch/ethz/systems/netbench/core/network/TransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/core/random/RandomManager.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/InfrastructureSelector.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/MainFromIntelliJ.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/MainFromProperties.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/RoutingSelector.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/TrafficSelector.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/infrastructure/BaseInitializer.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/infrastructure/IntermediaryGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/infrastructure/LinkGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/infrastructure/NetworkDeviceGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/infrastructure/OutputPortGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/infrastructure/TransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/routing/RoutingPopulator.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/traffic/FlowStartEvent.java
netbench/src/main/java/ch/ethz/systems/netbench/core/run/traffic/TrafficPlanner.java
netbench/src/main/java/ch/ethz/systems/netbench/core/utility/UnitConverter.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/bare/BarePacket.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/bare/BarePacketResendEvent.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/bare/BareSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/bare/BareTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/bare/BareTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/EcnTailDropOutputPort.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/EcnTailDropOutputPortGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/IpHeader.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/IpPacket.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/PerfectSimpleLink.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/PerfectSimpleLinkGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/TcpHeader.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/basic/TcpPacket.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/demo/DemoIntermediary.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/demo/DemoIntermediaryGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/demo/DemoPacket.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/demo/DemoPacketHeader.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/demo/DemoSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/demo/DemoTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/demo/DemoTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/ecmp/EcmpRoutingUtility.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/ecmp/EcmpSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/ecmp/EcmpSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/ecmp/EcmpSwitchRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/ecmp/EcmpSwitchRoutingInterface.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/ecmp/ForwarderSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/ecmp/ForwarderSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/ecmp/ForwarderSwitchRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/flowlet/FixedGapFlowletIntermediary.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/flowlet/FlowletIntermediary.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/flowlet/FlowletLogger.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/flowlet/IdentityFlowletIntermediary.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/flowlet/IdentityFlowletIntermediaryGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/flowlet/UniformFlowletIntermediary.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/flowlet/UniformFlowletIntermediaryGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/hybrid/EcmpThenValiantSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/hybrid/EcmpThenValiantSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/FromStringArrivalPlanner.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/ParetoDistribution.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/PoissonArrivalPlanner.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/RandomCollection.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/flowsize/FlowSizeDistribution.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/flowsize/OriginalSimonFSD.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/flowsize/PFabricDataMiningLowerBoundFSD.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/flowsize/PFabricDataMiningUpperBoundFSD.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/flowsize/PFabricWebSearchLowerBoundFSD.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/flowsize/PFabricWebSearchUpperBoundFSD.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/flowsize/ParetoFSD.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/poissontraffic/flowsize/UniformFSD.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/trafficpair/TrafficPairPlanner.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/valiant/RangeValiantSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/valiant/RangeValiantSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/valiant/ValiantEcmpSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/valiant/ValiantEncapsulation.java
netbench/src/main/java/ch/ethz/systems/netbench/ext/valiant/ValiantEncapsulationHeader.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/asaf/routing/priority/PriorityFlowletIntermediary.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/asaf/routing/priority/PriorityFlowletIntermediaryGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/newreno/TcpRetransmissionTimeOutEvent.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/newreno/newrenodctcp/NewRenoDctcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/newreno/newrenodctcp/NewRenoDctcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/newreno/newrenodctcp/NewRenoDctcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/newreno/newrenotcp/NewRenoTcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/newreno/newrenotcp/NewRenoTcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/newreno/newrenotcp/NewRenoTcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/simple/TcpPacketResendEvent.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/simple/simpledctcp/SimpleDctcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/simple/simpledctcp/SimpleDctcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/simple/simpledctcp/SimpleDctcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/simple/simpletcp/SimpleTcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/simple/simpletcp/SimpleTcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/simple/simpletcp/SimpleTcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/sourcerouting/EcmpThenKspNoShortestRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/sourcerouting/EcmpThenKspRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/sourcerouting/EcmpThenSourceRoutingSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/sourcerouting/EcmpThenSourceRoutingSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/sourcerouting/KShortestPathsSwitchRouting.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/sourcerouting/SourceRoutingEncapsulation.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/sourcerouting/SourceRoutingPath.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/sourcerouting/SourceRoutingSwitch.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/sourcerouting/SourceRoutingSwitchGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/tcpbase/AckRange.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/tcpbase/AckRangeSet.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/tcpbase/EchoHeader.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/tcpbase/FullExtTcpPacket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/tcpbase/PriorityHeader.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/tcpbase/SelectiveAckHeader.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/tcpbase/TcpLogger.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/ExcelCombinator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/FTEcmpFC.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/FileLineComparator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/FullCrossbarTopologyCreator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/HashTestMain.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/NodeTransportLayerMapper.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/NormalizeIt.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/NumberShortestPathsCheck.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/SequenceUtility.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/TestMain.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/TestSkewness.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/TestSkewness2.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/TrafficPairProbabilitiesCreator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/TwoExclusionRange.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/TwoSequenceHashTest.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/UniformServerLinksToNodesCreator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/XpanderTrafficPairProbabilitiesCreator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/dataprocessing/CalculateAverageStatistics.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/dataprocessing/InFolderCombinator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/dataprocessing/InFolderCombinatorProjecToR.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/dataprocessing/NiceExcelCreator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/dataprocessing/ProcessAll.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/dataprocessing/SingularCombinator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/dataprocessing/SingularCombinatorParetoGraph.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/dataprocessing/SingularCombinatorProjector.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/pairprob/NodePairProbabilityCreator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/pairprob/SPPCAllToAllInFraction.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/pairprob/SPPCDegreeOfSkew.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/pairprob/SPPCNodePairsFromFile.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/pairprob/deprecated/SkewedTrafficPairProbabilitiesCreator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/utility/pairprob/deprecated/SkewedTrafficPairProbabilitiesOneStageCreator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/ports/BoundedPriorityOutputPort.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/ports/BoundedPriorityOutputPortGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/ports/PriorityOutputPort.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/ports/PriorityOutputPortGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/ports/UnlimitedOutputPort.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/ports/UnlimitedOutputPortGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/buffertcp/BufferTcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/buffertcp/BufferTcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/buffertcp/BufferTcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/distmeantcp/DistMeanTcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/distmeantcp/DistMeanTcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/distmeantcp/DistMeanTcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/distrandtcp/DistRandTcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/distrandtcp/DistRandTcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/distrandtcp/DistRandTcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/lstftcp/LstfTcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/lstftcp/LstfTcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/lstftcp/LstfTcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/pfabric/PfabricSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/pfabric/PfabricTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/pfabric/PfabricTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/pfzero/PfzeroSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/pfzero/PfzeroTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/pfzero/PfzeroTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/sparktcp/SparkSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/sparktcp/SparkTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/sparktcp/SparkTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/sphalftcp/SpHalfTcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/sphalftcp/SpHalfTcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/sphalftcp/SpHalfTcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/sptcp/SpTcpSocket.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/sptcp/SpTcpTransportLayer.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/tcp/sptcp/SpTcpTransportLayerGenerator.java
netbench/src/main/java/ch/ethz/systems/netbench/xpt/voijslav/utility/FctDistributions.java
netbench/src/main/java/edu/asu/emit/algorithm/graph/BaseElementWithWeight.java
netbench/src/main/java/edu/asu/emit/algorithm/graph/BaseGraph.java
netbench/src/main/java/edu/asu/emit/algorithm/graph/Graph.java
netbench/src/main/java/edu/asu/emit/algorithm/graph/Path.java
netbench/src/main/java/edu/asu/emit/algorithm/graph/VariableGraph.java
netbench/src/main/java/edu/asu/emit/algorithm/graph/Vertex.java
netbench/src/main/java/edu/asu/emit/algorithm/graph/algorithms/DijkstraShortestPathAlg.java
netbench/src/main/java/edu/asu/emit/algorithm/graph/algorithms/YenTopKShortestPathsAlg.java
netbench/src/main/java/edu/asu/emit/algorithm/utils/QYPriorityQueue.java
netbench/src/test/java/ch/ethz/systems/netbench/core/SimulatorTest.java
netbench/src/test/java/ch/ethz/systems/netbench/core/config/GraphReaderTest.java
netbench/src/test/java/ch/ethz/systems/netbench/core/config/NBPropertiesTest.java
netbench/src/test/java/ch/ethz/systems/netbench/core/config/TopologyServerExtenderTest.java
netbench/src/test/java/ch/ethz/systems/netbench/core/network/EventTest.java
netbench/src/test/java/ch/ethz/systems/netbench/core/network/PacketArrivalEventTest.java
netbench/src/test/java/ch/ethz/systems/netbench/core/network/PacketDispatchedEventTest.java
netbench/src/test/java/ch/ethz/systems/netbench/core/random/RandomManagerTest.java
netbench/src/test/java/ch/ethz/systems/netbench/core/run/traffic/FlowStartEventTest.java
netbench/src/test/java/ch/ethz/systems/netbench/core/utility/UnitConverterTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/bare/BareRunTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/basic/EcnTailDropOutputPortTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/basic/PerfectSimpleLinkTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/demo/DemoIntermediaryTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/demo/DemoPacketTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/demo/DemoRunParallel8Test.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/demo/DemoRunTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/demo/DemoTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/ecmp/EcmpRunTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/ecmp/EcmpSwitchRoutingFatTreeK4Test.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/ecmp/EcmpSwitchRoutingTwoN6Test.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/ecmp/EcmpSwitchTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/ecmp/ForwarderSwitchTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/flowlet/IdentityFlowletIntermediaryTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/flowlet/UniformFlowletIntermediaryTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/hybrid/EcmpThenValiantSwitchTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/poissontraffic/RandomCollectionTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/valiant/RangeValiantSwitchTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/valiant/ValiantEcmpSwitchTest.java
netbench/src/test/java/ch/ethz/systems/netbench/ext/valiant/ValiantEncapsulationTest.java
netbench/src/test/java/ch/ethz/systems/netbench/testutility/TestLogReader.java
netbench/src/test/java/ch/ethz/systems/netbench/testutility/TestTopologyPortsConstruction.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/newreno/TcpRetransmissionTimeOutEventTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/newreno/newrenotcp/TcpBaseTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/newreno/newrenotcp/TcpResendTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/newreno/newrenotcp/TcpSequenceTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/simple/simpletcp/SimpleTcpBaseTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/simple/simpletcp/SimpleTcpSequenceTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/sourcerouting/EcmpThenSourceRoutingSwitchTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/sourcerouting/SourceRoutingEncapsulationTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/sourcerouting/SourceRoutingSwitchExtendedTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/sourcerouting/SourceRoutingSwitchTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/tcpbase/AckRangeSetTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/tcpbase/TcpPacketTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/utility/TwoExclusionRangeTest.java
netbench/src/test/java/ch/ethz/systems/netbench/xpt/vojislav/ports/BoundedPriorityOutputPortTest.java
```
