package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_outport;

import java.util.*;

/**
 * Interface that must be implemented by network switches that wish to be run alongside the 
 * infiniband transport layer protocol. Since Infiniband uses backpressure
 */
public interface ReconfigurableOutputPortInterface {

    /**
     * Triggers the reconfigurable output port to change from current multiplicty to after multiplicty
     * 
     * @param newMultiplicity       The new link multiplicity
     */
    public void triggerPortReconfiguration(long afterReconfigurationMultiplicityArg);

    /**
     * Signals that the output port has been reconfigured completely
     * 
     * @return The current link bandwidth multiplicity  
     */
    public void signalReconfigurationEnded();

}
