package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_link;

import java.util.*;

/**
 * Interface that must be implemented by network switches that wish to be run alongside the 
 * infiniband transport layer protocol. Since Infiniband uses backpressure
 */
public interface ReconfigurableLinkInterface {

    /**
     * Changes the multiplicity of the link bandwidth.
     * 
     * @param newMultiplicity       The new link multiplicity
     */
    public void setMultiplicity(long newMultiplicity);

    /**
     * Gets the current multiplicity of the link bandwidth.
     * 
     * @return The current link bandwidth multiplicity  
     */
    public long getMultiplicity();

}
