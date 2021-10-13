package ch.ethz.systems.netbench.ext.wcmp.loadbalancer;

import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;

import java.util.*;
import javafx.util.Pair; 

public class StatelessLoadBalancer extends LoadBalancer {
	// Random number generator
	private final Random rng;

	/**
	 * Constructor v1: Initialize with routing weights
	 **/
	public StatelessLoadBalancer(PathSplitWeights pathSplitWeights) {
		super(pathSplitWeights);
		// Initialize the random number generator.
		this.rng = new Random();
		this.rng.setSeed(1037658);	// Set the seed of the random number generator.
	}

	/**
	 * Constructor v2: no arguments, intialize pathSplitWeights to null
	 **/
	public StatelessLoadBalancer() {
		super(null);
		this.rng = new Random();
		this.rng.setSeed(1037658);	// Set the seed of the random number generator.
	}

	/**
	 * Gets the new path that a packet of size must traverse so that the link loads are most well balanced.
	 * We consider well-balanced as the weight of traffic traversing each path being closest to the given path weights.
	 *
	 * @return The next path traverse by the new packet.
	 **/
	@Override
	public Path getPath() {
		// Figure out which path to take based on random number generator.
		Map<Path, Double> pathWeightPairs = this.pathSplitWeights.getPathSplitWeights();
		assert(!pathWeightPairs.isEmpty());
		// Generate a random number in range [0, 1).
		double randomNumber = this.rng.nextDouble();
		double cumulativeWeight = 0;
		Path solnPath = null;
		Path finalPath = null;	// Store the previous path in the event that the valid path cannot be found.
		for (Map.Entry<Path, Double> pathWeightPair : pathWeightPairs.entrySet()) {
			finalPath = pathWeightPair.getKey();
			cumulativeWeight += pathWeightPair.getValue();
			if (cumulativeWeight > randomNumber) {
				solnPath = pathWeightPair.getKey();
				break;
			}
		}
		// It's possible to have solnPath be null if the sum of all path weights is slightly smaller than 1, and the
		// randomly generated number is slightly greater than the path weight sums.
		if (solnPath == null) {
			solnPath = finalPath;
		}
		return solnPath;
	}

	/**
	 * Update the path taken between src and dst with trafficSize
	 **/
	@Override
	public void logPathTaken(Path pathTaken, long trafficSize) {
		// do nothing since this is stateless.
	}

	/**
	 * Resets the internal state, such as when there is an update to the path split weights.
	 **/
	@Override
	public void reset(PathSplitWeights newSplitWeights) {
		this.pathSplitWeights = newSplitWeights;
		// do nothing, since there is nothing to reset.
	}
}
