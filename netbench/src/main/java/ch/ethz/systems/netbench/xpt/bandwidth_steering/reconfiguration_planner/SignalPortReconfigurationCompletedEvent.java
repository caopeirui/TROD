package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfiguration_planner;

import ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_outport.ReconfigurableOutputPortInterface;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.Event;

/**
 * Event for the complete reconfiguration of a port.
 */
public class SignalPortReconfigurationCompletedEvent extends Event {

    private final ReconfigurableOutputPortInterface reconfigurableOutputPortInterface;

    /**
     * Packet arrival event constructor.
     *
     * @param timeFromNowNs      Time in simulation nanoseconds from now
     * @param networkDevice      Network device at which the OCS reconfiguration is happening to 
     */
    public SignalPortReconfigurationCompletedEvent(long timeFromNowNs, 
            ReconfigurableOutputPortInterface reconfigurableOutputPortArg) {
        super(timeFromNowNs);
        this.reconfigurableOutputPortInterface = reconfigurableOutputPortArg;
    }

    @Override
    public void trigger() {
        reconfigurableOutputPortInterface.signalReconfigurationEnded();
    }

    @Override
    public String toString() {
        return "SignalPortReconfigurationCompletedEvent<" + ", " + this.getTime() + ">";
    }

}
