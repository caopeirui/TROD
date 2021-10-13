package ch.ethz.systems.netbench.ext.wcmp.loadbalancer;

import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;

import java.util.*;
import javafx.util.Pair; 

public class StatefulLoadBalancer extends LoadBalancer {
	// Stores the amount of bytes used to in each of the paths between every src-dst pair
	private HashMap<Path, Double> scoresAlongPaths;	// Records the amount of traffic sent along each path.

	private long totalTraffic; // Records the total traffic sent between the source-destination pair.

	public StatefulLoadBalancer(PathSplitWeights routingWeights) {
		super(routingWeights);
		this.scoresAlongPaths = new HashMap<>();
		// Initialize the random number generator.
		initializeInternalStates();
	}

	/**
	 * Constructor v2: no arguments, intialize pathSplitWeights to null
	 **/
	public StatefulLoadBalancer() {
		super(null);
		// Do not call the internal initializer yet
	}

	/** 
	 * Initializes the internal states used to keep track of load balancing decisions.
	 *
	 **/
	private void initializeInternalStates() {
		// For each src, dst pair, initialize the source and destination node pairs.
		this.scoresAlongPaths.clear();
		this.totalTraffic = 0L;
		Map<Path, Double> pathWeights = this.pathSplitWeights.getPathSplitWeights();
		for (Map.Entry<Path, Double> pathWeightPair : pathWeights.entrySet()) {
			this.scoresAlongPaths.put(pathWeightPair.getKey(), pathWeightPair.getValue());
		}
	}

	/**
	 * Gets the new path that a packet of size must traverse so that the link loads are most well balanced.
	 * We consider well-balanced as being that the traffic proportion sent along each path is closest to 
	 * to the prescribes routing weights.
	 *
	 * @return The next path traverse by the new packet.
	 **/
	@Override
	public Path getPath() {
		double maxScore = -Double.MAX_VALUE;
		Path bestPath = null;
		for (Map.Entry<Path, Double> entry : this.scoresAlongPaths.entrySet()) {
			if (entry.getValue() > maxScore) {
				maxScore = entry.getValue();
				bestPath = entry.getKey();
			}
		}
		return bestPath;
	}

	/**
	 * Logs the path taken and the amount of traffic that took said path.
	 **/
	@Override
	public void logPathTaken(Path pathTaken, long trafficSize) {
		// Check validity of input
		assert(trafficSize > 0);
		assert(this.scoresAlongPaths.containsKey(pathTaken));
		// First update the total traffic sent.
		this.totalTraffic += trafficSize;
		// Second update the score of the path taken
		for (Map.Entry<Path, Double> pathScorePair : this.scoresAlongPaths.entrySet()) {
			Path currentPath = pathScorePair.getKey();
			double pathRoutingWeight = this.pathSplitWeights.getPathWeight(currentPath);
			double currentScore = pathScorePair.getValue();
			if (currentPath.equals(pathTaken)) {
				this.scoresAlongPaths.put(currentPath, currentScore - (1 - pathRoutingWeight) * trafficSize);
			} else {
				this.scoresAlongPaths.put(currentPath, currentScore + pathRoutingWeight * trafficSize);
			}
		}
	}

	/**
	 * Resets the internal state, such as when there is an update to the path split weights.
	 **/
	@Override
	public void reset(PathSplitWeights routingWeights) {
		this.pathSplitWeights = routingWeights;
		initializeInternalStates();
	}
}
