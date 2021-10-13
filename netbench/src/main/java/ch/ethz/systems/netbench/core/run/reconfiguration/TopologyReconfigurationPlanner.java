package ch.ethz.systems.netbench.core.run.reconfiguration;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
// import ch.ethz.systems.netbench.core.network.ReconfigurableOutputPortNetworkDevice;

import java.util.Map;

public abstract class TopologyReconfigurationPlanner {

    protected final Map<Integer, NetworkDevice> idToNetworkDevice;

    /**
     * Constructor.
     *
     * @param idToTransportLayerMap     Maps a network device identifier to its corresponding transport layer
     */
    public TopologyReconfigurationPlanner(Map<Integer, NetworkDevice> idToNetworkDeviceArg) {
        // Create mappings
        this.idToNetworkDevice = idToNetworkDeviceArg;
    }

    /**
     * Register the starting reconfiguration event for a device ID.
     *
     * @param time          Time at which it starts in nanoseconds
     * @param deviceID      Network device identifier
     * @param dstId         Destination network device identifier
     * @param flowSizeByte  Flow size in bytes
     */
    protected abstract void planReconfigurationEvents();
        //ReconfigurableOutputPortNetworkDevice device = idToReconfigurableNetworkDevice.get(deviceID);
        //OCSReconfigurationStartEvent reconfig_event = new OCSReconfigurationStartEvent(device.getIdentifier());
        //Simulator.registerEvent(reconfig_event);
}
