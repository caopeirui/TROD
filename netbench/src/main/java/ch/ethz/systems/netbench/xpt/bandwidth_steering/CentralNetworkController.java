//  :  

package ch.ethz.systems.netbench.xpt.bandwidth_steering;

import ch.ethz.systems.netbench.core.network.*;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Random; // random number generator used to determining how to route traffic


/**
 * A network controller manager with global information of current topology, routing weights,
 * and also caches path information for flows. This central network controller is connected 
 * to every switch in the network. Currently the network controller only supports at most 2
 * interpod hops, where a packet can take at most 1 intermediate hop before reaching its 
 * destination pod.
 */
public class CentralNetworkController {

	// Topology related.
	private final Map<Integer, NetworkDevice> idToAllNetworkDevices;
	private final Map<Integer, Integer> deviceIdToPodId; // Maps each device id to its corresponding pod id.
	private final int totalPods; // The number of pods in the entire network
	private HashMap<Integer, ReconfigurableNetworkSwitch> podIdToBoundarySwitch; // saves the reference to the boundary switch of each pod
	
	// private HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> sentTraffic;
	
	
	
	// Reconfiguration events.
	private HashMap<Integer, HashMap<Integer, Long>> reconfigurationDetails; // Maps to a switch id to a list of pairs, each pair is port id and new target switch id

	// Routing utility.
	// Continuously maintained everytime reconfiguration happens. Records the boundary switch between any two pod
	// pair, and the number of port ids in each boundary switch that leads there.
	private HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> currentInterpodRoutingWeights; // Currently supports at most 2 inter-pod hops.
	private HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> afterReconfigurationCompletionInterpodRoutingWeights;
	
	// Routing cache for the flows
	private HashMap<Long, Integer> cachedFlowInterPodPaths;  // caching the intermediate pod of the flow id

	// Random number generator.
	private Random rng;

	/**
	 * Constructor for the central network controller object.
	 * @param idToAllNetworkDevicesArg			Maps an id to all of the network devices in the network.
	 * @param deviceIdToPodIdArg 				Maps the device ids to the pods they belong to
	 * @param totalPodsArg						The total number of pods in the entire network.
	 */
	public CentralNetworkController(Map<Integer, NetworkDevice> idToAllNetworkDevicesArg, 
			Map<Integer, Integer> deviceIdToPodIdArg,
			int totalPodsArg,
			HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> initialInterpodRoutingWeights) {
		if (totalPodsArg <= 0) {
			throw new IllegalArgumentException("CentralNetworkController cannot accept pod numbers of 0 or lower.");
		}
		this.totalPods = totalPodsArg;
		this.idToAllNetworkDevices = idToAllNetworkDevicesArg;
		this.deviceIdToPodId = deviceIdToPodIdArg;
		// Initialize the reconfigurationDetails
		this.rng = new Random();
		this.cachedFlowInterPodPaths = new HashMap<Long, Integer>();
		this.currentInterpodRoutingWeights = initialInterpodRoutingWeights;
		this.podIdToBoundarySwitch = new HashMap<Integer, ReconfigurableNetworkSwitch>();
	}

	/**
	 * Updates the interpod routing weights such that the current interpod routing weights are changed
	 * to the most recent version. Does not return anything.
	 */
	public void updateInterpodRoutingWeights(long reconfigEpochNs, HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> newRoutingWeights) {
		// Start repopulating the routing tables.

		this.currentInterpodRoutingWeights = newRoutingWeights;
	}

	/**
	 * Triggers the reconfiguration event for the entire topology.
	 * @param reconfigurationDetailsArg			A hashmap that maps a device id to be reconfigured to a list of port ids to be reconfigured.
	 */
	public void triggerReconfiguration(HashMap<Integer, HashMap<Integer, Long>> reconfigurationDetailsArg,
			HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> duringRoutingWeights, 
			HashMap<Integer, HashMap<Integer, HashMap<Integer, Double>>> afterRoutingWeights) {
		if (!this.reconfigurationDetails.isEmpty()) {
			throw new IllegalStateException("New reconfiguration event triggered event before the previous reconfiguration event hasn't completed");
		}
		this.reconfigurationDetails = reconfigurationDetailsArg;
		// First go into all of the boundary switchs' and mark ports to be reconfigured as drained
		for (int currentBoundarySwitchPodId : this.reconfigurationDetails.keySet()) {
			// An array of pair of ports and new target network device
			ReconfigurableNetworkSwitch boundarySwitch = this.podIdToBoundarySwitch.get(currentBoundarySwitchPodId);
			int boundarySwitchId = boundarySwitch.getIdentifier();
			//boundarySwitch.triggerReconfiguration(this.reconfigurationDetails.get(currentBoundarySwitchPodId));
		}
		//this.updateInterpodRoutingWeights(duringRoutingWeights); // Update the interpod routing tables first.
		this.afterReconfigurationCompletionInterpodRoutingWeights = afterRoutingWeights;
		// clear all the cached interpod paths, since the interpod routing weights might change
		this.cachedFlowInterPodPaths.clear();
		return;
	}

	/**
	 * Called by the network devices to signal that its port has just completed reconfiguration. The
	 * network controller can now undrain this port.
	 * @param deviceID 			The device ID for the network switch signalling the output port reconfiguration completion.
	 * @param portID 			The port id that is signalling that reconfiguration is complete.
	 */
	public void signalOutputPortReconfigurationCompleted(int sourceDeviceId, int targetDeviceId) {
		//HashMap<Integer, ArrayList<Integer>> toReconfigSwitchesAndPorts = this.reconfigurationDetails.get(0);
		if (!reconfigurationDetails.containsKey(sourceDeviceId) || !reconfigurationDetails.get(sourceDeviceId).containsKey(targetDeviceId)) {
			throw new IllegalStateException("Cannot locate the output port and device ID for this reconfiguration output port event");
		}
		// Next, make sure to update the current boundary switch list
		ReconfigurableNetworkSwitch srcBoundarySwitch = (ReconfigurableNetworkSwitch) this.idToAllNetworkDevices.get(sourceDeviceId);
		ReconfigurableNetworkSwitch targetBoundarySwitch = (ReconfigurableNetworkSwitch) this.idToAllNetworkDevices.get(targetDeviceId);

		int srcPodId = srcBoundarySwitch.getPodId();
		int dstPodId = targetBoundarySwitch.getPodId();

		// Add boundary switch and port ID to the boundary switch set
		reconfigurationDetails.get(srcPodId).remove(dstPodId);
		if (reconfigurationDetails.get(srcPodId).isEmpty()) {
			reconfigurationDetails.remove(srcPodId);
		}
		// Before exiting, check if this port marks the last port to complete reconfiguration, if so, pop the reconfigurationDetails list
		// and also update the routing weights.
		if (reconfigurationDetails.isEmpty()) {
			reconfigurationDetails = null; // set the reconfiguration details to null to indicate that the most recent reconfiguration has been completed.
			//this.updateInterpodRoutingWeights(this.afterReconfigurationCompletionInterpodRoutingWeights);
			this.afterReconfigurationCompletionInterpodRoutingWeights = null;
		}
	}

	// Finds the pod-to-pod path, and decides at random whether 
	public int findIntermediatePod(int srcPod, int dstPod, long flowId) {
		//   annotate
		System.out.println("  debug CentralNetworkController findIntermediatePod()");
		// if (this.cachedFlowInterPodPaths.containsKey(flowId)) {
		// 	return this.cachedFlowInterPodPaths.get(flowId);
		// }
		double randomNumber = this.rng.nextDouble();
		HashMap<Integer, Double> pathWeightsCollection = currentInterpodRoutingWeights.get(srcPod).get(dstPod);
		double carrySumWeight = 0;
		for (Integer intermediatePod : pathWeightsCollection.keySet()) {
			double pathWeight = pathWeightsCollection.get(intermediatePod);
			if (pathWeight == 0) {
				continue;
			}
			carrySumWeight += pathWeight;
			if (carrySumWeight > randomNumber) {
				//   annotiate
				// this.cachedFlowInterPodPaths.put(flowId, intermediatePod);    //   annotate
				return intermediatePod;
			}
		}
		return -1;
	}

	public void setBoundarySwitchOfPod(int boundarySwitchId, int podId) {
		if (podId >= this.totalPods || podId < 0) {
			throw new IllegalStateException("Invalid pod id, must be between 0 and " + (totalPods - 1) + ".");
		}
		if (this.podIdToBoundarySwitch.containsKey(podId)) {
			throw new IllegalStateException("Pod with id " + (podId) + " already has an aggregation switch with id " + this.podIdToBoundarySwitch.get(podId).getIdentifier());
		}
		this.podIdToBoundarySwitch.put(podId, (ReconfigurableNetworkSwitch) this.idToAllNetworkDevices.get(boundarySwitchId));
	}

	public int getDeviceIdOfPodAggregationSwitch(int podId) {
		return this.podIdToBoundarySwitch.get(podId).getIdentifier();
	}
	

	/**
	 * Consultation function called by individual network devices to figure out 
	 * which pod deviceId belongs to.
	 */
	public int translateDeviceIdToPodId(int deviceId) {
		if (this.deviceIdToPodId.containsKey(deviceId)) {
			return this.deviceIdToPodId.get(deviceId);
		}
		return -1;
	}


	/*
	 * Given a list of boudnary switch options and the corresponding output ports, selects the boundary switch at random
	 * and returns the boundary switch id along with its port id.
	 *
	 * @param boundarySwitchAndPorts			A hash map that maps boundary switch to the set of port ids.
	 
	private ArrayList<AggregationSwitchPortPair> computeInterpodPathBoundarySwitchAndPort(int srcPodId, 
			int intermediatePodId, int dstPodId) {
		HashMap<Integer, HashSet<Integer>> boundarySwitchAndPorts;
		if (intermediatePodId == dstPodId) {
			boundarySwitchAndPorts = this.interpodBoundarySwitches.get(srcPodId).get(dstPodId);
		} else {
			// need to consider only switches such that each port must have a path to the destination
			boundarySwitchAndPorts = new HashMap<Integer, HashSet<Integer>>();
			for (Integer boundarySwitchId : this.interpodBoundarySwitches.get(srcPodId).get(intermediatePodId).keySet()) {
				ReconfigurableNetworkSwitch srcBoundarySwitch = (ReconfigurableNetworkSwitch) this.idToAllNetworkDevices.get(boundarySwitchId);
				for (Integer portId : this.interpodBoundarySwitches.get(srcPodId).get(dstPodId).get(boundarySwitchId)) {
					// only insert this port id for consideration if the port id leads to a intermediate aggregation switch that has a path 
					// to the destination pod.
					int intermediatePodAggregationSwitchId = srcBoundarySwitch.getCurrentReconfigurablePortTargetDeviceId(portId);
					// check if this intermediate pod's aggregation switch id is a boundary switch from intermediate pod to the destination pod.
					if (this.interpodBoundarySwitches.get(intermediatePodId).get(dstPodId).containsKey(intermediatePodAggregationSwitchId)) {
						// insert this port into the boundarySwitchAndPorts
						if (!boundarySwitchAndPorts.containsKey(boundarySwitchId)) {
							boundarySwitchAndPorts.put(boundarySwitchId, new HashSet<Integer>());
						}
						boundarySwitchAndPorts.get(boundarySwitchId).add(portId);
					}
				}
			}
		}
		// Technically one would need to solve the max-flow problem to figure out which switch should hold the largest weight
		// for routing.
		int totalPorts = 0;
		for (int boundarySwitchId : boundarySwitchAndPorts.keySet()) {
			HashSet<Integer> setOfOutputPorts = boundarySwitchAndPorts.get(boundarySwitchId);
			totalPorts += setOfOutputPorts.size();
		}

		// Now get a random number in range [0, 1)
		final double randomNumber = this.rng.nextDouble();
		int foundBoundarySwitchId = -1;
		// Choose the boundary switch id.
		double cumulativeWeight = 0;
		int selectedBoundarySwitchNumPorts = -1;
		for (int boundarySwitchId : boundarySwitchAndPorts.keySet()) {
			HashSet<Integer> setOfOutputPorts = boundarySwitchAndPorts.get(boundarySwitchId);
			cumulativeWeight += (setOfOutputPorts.size() / totalPorts);
			foundBoundarySwitchId = boundarySwitchId;
			if (cumulativeWeight > randomNumber) {
				selectedBoundarySwitchNumPorts = setOfOutputPorts.size();
				break;
			}
		}

		// Next find the port in foundBoundarySwitchId.
		int portIndex = this.rng.nextInt(selectedBoundarySwitchNumPorts);
		int selectedPortIndex = -1;
		int portOffset = 0;
		for (Integer portId : boundarySwitchAndPorts.get(foundBoundarySwitchId)) {
			if (portId == portIndex) {
				selectedPortIndex = portIndex;
				break;
			}
		}

		ArrayList<AggregationSwitchPortPair> truePath = new ArrayList<AggregationSwitchPortPair>();
		truePath.add(new AggregationSwitchPortPair(foundBoundarySwitchId, selectedPortIndex));
		if (intermediatePodId != dstPodId) {
			// Find the intermediate pod id switch and port
			// First find the aggregation switch we'd reach when we get to the intermediate pod
			ReconfigurableNetworkSwitch srcPodBoundarySwitch = (ReconfigurableNetworkSwitch) this.idToAllNetworkDevices.get(foundBoundarySwitchId);
			int intermediatePodBoundarySwitchId = srcPodBoundarySwitch.getCurrentReconfigurablePortTargetDeviceId(selectedPortIndex);
			// Now randomly select the outgoing port
			int randomIntermediatePortIndex = this.rng.nextInt(this.interpodBoundarySwitches.get(intermediatePodId).get(dstPodId).get(intermediatePodBoundarySwitchId).size());
			int offset = 0;
			for (Integer intermediatePodSwitchPortId : this.interpodBoundarySwitches.get(intermediatePodId).get(dstPodId).get(intermediatePodBoundarySwitchId)) {
				if (offset == randomIntermediatePortIndex) {
					truePath.add(new AggregationSwitchPortPair(intermediatePodBoundarySwitchId, intermediatePodSwitchPortId));
					break;
				}
				offset++;
			}
		}
		return truePath;
	}
	*/
}
