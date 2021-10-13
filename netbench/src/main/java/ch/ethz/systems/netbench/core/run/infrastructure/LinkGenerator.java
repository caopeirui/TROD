package ch.ethz.systems.netbench.core.run.infrastructure;

import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.Link;

public abstract class LinkGenerator {
	// link multiplicity is the entry in the adjacency matrix, meaning how many times the number of base bandwidth does this link have
    public abstract Link generate(NetworkDevice fromNetworkDevice, NetworkDevice toNetworkDevice, long link_multiplicity);
}
