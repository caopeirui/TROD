package ch.ethz.systems.netbench.xpt.infiniband.reconfigurableIB;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.run.infrastructure.IntermediaryGenerator;
import ch.ethz.systems.netbench.core.run.infrastructure.NetworkDeviceGenerator;
import ch.ethz.systems.netbench.ext.wcmp.loadbalancer.*;

import java.util.*;
import java.io.*; // for file reading

public class ReconfigurableInfinibandSwitchGenerator extends NetworkDeviceGenerator {

    private final int numNodes;
    private final IntermediaryGenerator intermediaryGenerator;
    private HashMap<Integer, Integer> deviceIdToPodId;
    private final boolean statefulLoadBalancing;
    private final boolean enablePacketSpraying;
    private final long inputBufferMaxSizeBytes;
    private final int numVCs;

    public ReconfigurableInfinibandSwitchGenerator(IntermediaryGenerator intermediaryGenerator, int numNodes, String podCollectionStringFilename) {

        // Standard fields
        this.numNodes = numNodes;
        this.intermediaryGenerator = intermediaryGenerator;

        // Input queue size
        this.inputBufferMaxSizeBytes = Simulator.getConfiguration().getLongPropertyOrFail("infiniband_input_queue_size_bytes");

        // Initialize the pod id to device id map deviceIdToPodId
        this.deviceIdToPodId = new HashMap<>();

        // Log creation
        SimulationLogger.logInfo("Network device", "RECONFIGURABLE_INFINIBAND_SWITCH(numNodes=" + numNodes + ")");

        // Initialize the load balancer (choosing between stateful or stateless). Defaults to stateless.
        this.statefulLoadBalancing = Simulator.getConfiguration().getBooleanPropertyWithDefault("stateful_load_balancing", false);

        // Also decide whether if we can spray packet, or if packets of a given flow must traverse the same path.
        this.enablePacketSpraying = Simulator.getConfiguration().getBooleanPropertyWithDefault("enable_packet_spraying", false);

        // Number of virtual channels
        this.numVCs = Simulator.getConfiguration().getIntegerPropertyWithDefault("num_vcs", 1);
        assert(this.numVCs > 0);
        readPodCollectionGraph(podCollectionStringFilename);
    }


    /**
     * Reads the string file that records which pod each network device belongs to.
     */
    private void readPodCollectionGraph(String filename) {
        // The file should contain all of the elements and the corresponding pod IDs.
        File file = new File(filename); 
        try (FileReader fr = new FileReader(file)) {
            BufferedReader br = new BufferedReader(fr);     
            String st; 
            while ((st = br.readLine()) != null) {
                if (st.length() > 0 && st.charAt(0) != '#') {
                    // Format must be: network_device_id, has_reconfigurable_port (T/F), pod_id 
                    String[] strArray = st.split(",", 0);
                    int network_device_id = Integer.parseInt(strArray[0]);                    
                    int pod_id = Integer.parseInt(strArray[1]);
                    deviceIdToPodId.put(network_device_id, pod_id);
                }
            }
            br.close();
        } catch (FileNotFoundException fe) {
            System.out.println("File not found");
        } catch (IOException ie) {
            System.out.println("IO exception not found");
        }
        // Check if the file pod id identifies all the network devices, including the servers.
    }

    @Override
    public NetworkDevice generate(int identifier) {
        return this.generate(identifier, null);
    }

    @Override
    public NetworkDevice generate(int identifier, TransportLayer transportLayer) {
        if (this.numVCs == 1) {
            return new ReconfigurableInfinibandSwitch(identifier, transportLayer, intermediaryGenerator.generate(identifier), this.deviceIdToPodId.get(identifier), inputBufferMaxSizeBytes * 8L, this.statefulLoadBalancing, this.enablePacketSpraying);
        } else {
            return new ReconfigurableInfinibandVCSwitch(identifier, transportLayer, intermediaryGenerator.generate(identifier), this.deviceIdToPodId.get(identifier), inputBufferMaxSizeBytes * 8L, this.statefulLoadBalancing, this.enablePacketSpraying, this.numVCs);
        }
    }
}
