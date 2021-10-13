package ch.ethz.systems.netbench.ext.wcmp.routingweight;

import javafx.util.Pair; 
import java.util.*;

/**
 * 
 * Descirbes the routing weights between paths for a source-destination node pair in the network.
 * The sum of path split weights amongst different paths between two nodes must be 1.
 */
public class PathSplitWeights {

	private final int src;	// source node

	private final int dst;	// destination node

	private Map<Path, Double> interNodeRoutingWeights;

	/**
	 * Constructor 1: Basic constructor
	 **/
	public PathSplitWeights(int src, int dst) {
		assert(src != dst);
		this.src = src;
		this.dst = dst;
		this.interNodeRoutingWeights = new HashMap<>();
	}

	public PathSplitWeights(int src, int dst, Map<Path, Double> pathSplitRatios) {
		assert(src != dst);
		this.src = src;
		this.dst = dst;
		this.interNodeRoutingWeights = pathSplitRatios;
	}

	/**
	 * Clears out the interNodeRoutingWeights, but does not clear the src and dst.
	 **/
	public void clear() {
		if (this.interNodeRoutingWeights != null) {
			this.interNodeRoutingWeights.clear();
		}
	}

	public int getSrc() {
		return this.src;
	}

	public int getDst() {
		return this.dst;
	}

	/**
	 * Returns a value which is the sum of path split ratio across all paths.
	 *
	 * @return The sum of path split ratios across all paths.
	 **/
	public double sumPathSplitWeights() {
		double pathSum = 0;
		for (Map.Entry<Path, Double> pathWeightPair : interNodeRoutingWeights.entrySet()) {
			pathSum += pathWeightPair.getValue();
		}
		return pathSum;
	}


	/** 
	 * Update the routing weights, if path never existed before, will insert path into routing weights.
	 **/
	public void updatePathWeight(Path currentPath, double newWeight) {
		assert(newWeight >= 0 && newWeight <= 1);
		assert(currentPath.getSrc() == src && currentPath.getDst() == dst);
		// Finally go and check whether if the path exists.
		interNodeRoutingWeights.put(currentPath, newWeight);
	}

	/**
	 * Returns the weight in range [0, 1] belonging to the path.
	 **/
	public double getPathWeight(Path path) {
		assert(interNodeRoutingWeights.containsKey(path));
		return interNodeRoutingWeights.get(path);
	}

	/**
	 * Given a source and destination, gets the list of paths and the corresponding weights.
	 **/
	public Map<Path, Double> getPathSplitWeights() {
		return interNodeRoutingWeights;
	}
}
