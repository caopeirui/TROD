package ch.ethz.systems.netbench.ext.poissontraffic;

import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.run.traffic.TrafficPlanner;

import java.util.Map;
import java.io.*; // for file reading

public class FromFileArrivalPlanner extends TrafficPlanner {

    private final String arrivalFilename;

    /**
     * Constructor.
     *
     * @param idToTransportLayerMap     Maps a network device identifier to its corresponding transport layer
     * @param arrivals                  File name of arrival plan
     */
    public FromFileArrivalPlanner(Map<Integer, TransportLayer> idToTransportLayerMap, String filename) {
        super(idToTransportLayerMap);
        this.arrivalFilename = filename;
        SimulationLogger.logInfo("Flow planner", "FROM_FILE_ARRIVAL_PLANNER(arrival file name=" + arrivalFilename + ")");
    }

    /**
     * Creates plan based on the given string:
     * (start_time, src_id, dst_id, flow_size_byte);(start_time, src_id, dst_id, flow_size_byte);...
     *
     * @param durationNs    Duration in nanoseconds
     */
    @Override
    public void createPlan(long durationNs) {
        File file = new File(this.arrivalFilename); 
        try (FileReader fr = new FileReader(file)) {
            BufferedReader br = new BufferedReader(fr);     
            String st; 
            while ((st = br.readLine()) != null) {
                // check if the first character forms a comment
                if (st.charAt(0) != '#') {
                    String[] arrivalSpl = st.split(",");
                    int lineLength = arrivalSpl.length;
                    this.registerFlow(
                        Long.valueOf(arrivalSpl[0].trim()),    // time of entry
                        Integer.valueOf(arrivalSpl[1].trim()), // source id
                        Integer.valueOf(arrivalSpl[2].trim()),  // destination id
                        Long.valueOf(arrivalSpl[3].trim()) // size in terms of bytes
                    );
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
