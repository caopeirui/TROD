package ch.ethz.systems.netbench.ext.wcmp;

import ch.ethz.systems.netbench.core.network.OutputPort;

public interface WcmpSwitchRoutingInterface {

    /**
     * Add another hop opportunity to the routing table for the given destination.
     *
     * @param destinationId     Destination identifier
     * @param nextHopId         A network device identifier where it could go to next (must have already been added
     *                          as connection}, else will throw an illegal
     *                          argument exception.
     * @param weight 			Weight that this path is taking
     */
    void addDestinationToNextSwitch(int destinationId, int nextHopId, double weight);

}
