package ch.ethz.systems.netbench.ext.threshold_routing;

import ch.ethz.systems.netbench.core.network.NetworkDevice;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
public class Path {
    Integer [] path;

    public Path(List)

    Integer getSrcID() {
        return this.
    }

    boolean isEqual(Path path) {

    }
}
*/

public class ThresholdRoutingUtility {

    private static final int INFINITY = 999999999;

    // need to figure out the path ratio
    public ThresholdRoutingUtility() {
        // Cannot be instantiated
    }

    /**
     * Initializes the multi-forwarding ECMP routing tables in the network devices.
     * The network devices must be ECMP switches, and should have been generated
     * corresponding to the topology graph defined in the run configuration.
     *
     * @param idToNetworkDevice     Mapping of network device identifier to network device
     */
    public static void populatePathRoutingTables(Map<Integer, NetworkDevice> idToNetworkDevice, String filename) {
        System.out.print("Reading threshold file...");

        File file = new File(filename); 
        try (FileReader fr = new FileReader(file)) {
            BufferedReader br = new BufferedReader(fr);     
            String st; 
            while ((st = br.readLine()) != null) {
                if (st.length() == 0) {
                    continue;
                }
                //   add
                if (st.startsWith("#")) {
                    //  
                    continue;
                }
                
                String[] strArray = st.split(",", 0);
                int node_id = Integer.parseInt(strArray[0]);
                String instruction_type = strArray[1];
                
                // java ==
                if (instruction_type.equals("threshold")) {
                    int threshold_entry_id = Integer.parseInt(strArray[2]);
                    double threshold = Double.parseDouble(strArray[3]);
                    int next_hop = Integer.parseInt(strArray[4]);
                    int num_ecmp_ports = Integer.parseInt(strArray[5]);
                    List<Integer> ecmp_next_hop = new ArrayList<>();
                    for (int i = 0; i < num_ecmp_ports; i++) {
                        ecmp_next_hop.add(Integer.parseInt(strArray[6 + i]));
                    }
                    ((ThresholdSwitchRoutingInterface) idToNetworkDevice.get(node_id)).addThresholdTableEntry(threshold_entry_id, threshold,
                        next_hop, ecmp_next_hop);
                   
                } else if (instruction_type.equals("match")) {
                    int src_id = Integer.parseInt(strArray[2]);
                    int dst_id = Integer.parseInt(strArray[3]);
                    int threshold_entry_id = Integer.parseInt(strArray[4]);
                    ((ThresholdSwitchRoutingInterface) idToNetworkDevice.get(node_id)).addSrcDstToThresholdTableId(src_id, dst_id, threshold_entry_id);
                } else {
                    System.out.println("Threshold file may be faulty!");
                }
            }
            br.close();
        } catch (FileNotFoundException fe) {
            System.out.println("File not found");
        } catch (IOException ie) {
            System.out.println("IO exception not found");
        }

    }

}
