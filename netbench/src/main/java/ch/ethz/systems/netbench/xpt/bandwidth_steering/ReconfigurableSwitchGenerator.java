package ch.ethz.systems.netbench.xpt.bandwidth_steering;

import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.run.infrastructure.IntermediaryGenerator;
import ch.ethz.systems.netbench.core.run.infrastructure.NetworkDeviceGenerator;
import java.io.*;
import java.util.HashMap;
import java.util.HashSet;


public class ReconfigurableSwitchGenerator extends NetworkDeviceGenerator {

    private final int numNodes;
    private final IntermediaryGenerator intermediaryGenerator;
    private HashMap<Integer, Integer> deviceIdToPodId;
    private final String podIdFilename;

    public ReconfigurableSwitchGenerator(IntermediaryGenerator intermediaryGenerator, int numNodes, String podIdFilenameArg) {
        SimulationLogger.logInfo("Network device", "Reconfigurable Switch Generator (numNodes=" + numNodes + ")");

        // Standard fields
        this.numNodes = numNodes;
        this.intermediaryGenerator = intermediaryGenerator;
        this.podIdFilename = podIdFilenameArg;
        //this.deviceToPodId = new HashMap<Integer, Integer>();
        this.deviceIdToPodId = new HashMap<Integer, Integer>();
        readPodCollectionGraph(this.podIdFilename);
    }

    /**
     * Reads the string file that records which pod each network device belongs to.
     */
    private void readPodCollectionGraph(String filename) {
        // The file should contain all of the elements and the corresponding pod IDs.
        File file = new File(filename); 
        HashSet<Integer> podIdSet = new HashSet<Integer>();
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
        System.out.println("debug: generate single parameter");
        
        return this.generate(identifier, null);
    }

    @Override
    public NetworkDevice generate(int identifier, TransportLayer transportLayer) {
        // returns either a leaf switch or an aggregation switch, depending on the situation
        int devicePodId = deviceIdToPodId.get(identifier);
        return new ReconfigurableNetworkSwitch(identifier, transportLayer, intermediaryGenerator.generate(identifier), devicePodId);
    }

}
