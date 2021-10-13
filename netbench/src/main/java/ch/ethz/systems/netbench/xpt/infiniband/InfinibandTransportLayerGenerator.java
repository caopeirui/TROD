package ch.ethz.systems.netbench.xpt.infiniband;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.run.infrastructure.TransportLayerGenerator;

public class InfinibandTransportLayerGenerator extends TransportLayerGenerator {

	private final boolean checkPacketDeliveryOrder;

    public InfinibandTransportLayerGenerator() {
        // No parameters needed
        SimulationLogger.logInfo("Transport layer", "Simple Infiniband Transport Layer Generator");
        // Check whether if the transport layer needs to check for in-order delivery. Defaults to false
        this.checkPacketDeliveryOrder = Simulator.getConfiguration().getBooleanPropertyWithDefault("infiniband_check_delivery_order", false);
    }

    @Override
    public TransportLayer generate(int identifier) {
        return new InfinibandTransportLayer(identifier, checkPacketDeliveryOrder);
    }

}
