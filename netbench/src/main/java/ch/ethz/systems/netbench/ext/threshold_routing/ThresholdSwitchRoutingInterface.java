package ch.ethz.systems.netbench.ext.threshold_routing;
import java.util.List;


public interface ThresholdSwitchRoutingInterface {

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

    void addSrcDstToThresholdTableId(Integer src_id, Integer dst_id, int threshold_table_id);
    void addThresholdTableEntry(int threshold_table_id, double threshold_bps,
                                Integer direct_path, List<Integer> ecmp_paths);
}
