package ch.ethz.systems.netbench.ext.wcmp.loadbalancer;

import ch.ethz.systems.netbench.ext.wcmp.routingweight.*;

/**
 * Load balancer is a class of algorithm that load balances traffic across all the paths
 * according to the paths split ratio.
 */
public abstract class LoadBalancer {

	// Current path routing weights for all node pairs.
	protected PathSplitWeights pathSplitWeights;

	/** 
	 *
	 **/
	LoadBalancer(PathSplitWeights pathSplitWeights) {
		this.pathSplitWeights = pathSplitWeights;
	}

	/**
	 * Clears out the current routing weights.
	 **/
	public void clear() {
		this.pathSplitWeights = null;
	}

	/**
	 * Sets the routing weights to a new one.
	 **/
	public void setRoutingWeights(PathSplitWeights newRoutingWeights) {
		this.pathSplitWeights = newRoutingWeights;
	}

	/**
	 * Based on the 
	 **/
	public abstract Path getPath();

	/**
	 * Updates the path taken and the traffic that took said paths.
	 **/
	public abstract void logPathTaken(Path pathTaken, long trafficSize);

	/**
	 * Resets the internal state, such as when there is an update to the path split weights.
	 **/
	public abstract void reset(PathSplitWeights newSplitWeights);
}