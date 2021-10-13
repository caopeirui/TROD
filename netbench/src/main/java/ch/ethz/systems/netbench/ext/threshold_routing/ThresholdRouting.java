package ch.ethz.systems.netbench.ext.threshold_routing;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class ThresholdRouting {
    private TokenBucket token_;
    private Integer direct_path_;
    // WCMP can be realized by repeating certain next id multiple times.
    private List<Integer> ecmp_paths_;
    private Random rng; 

    private HashMap<Long, Integer> flow_id_to_next_hop_;

    public ThresholdRouting (double threshold_bps, Integer direct_path,
                             List<Integer> ecmp_paths) {
        token_  = new TokenBucket(2 * (long)threshold_bps * TokenBucket.PERIOD, threshold_bps);
        direct_path_ = direct_path;
        ecmp_paths_ = ecmp_paths;

        flow_id_to_next_hop_ = new HashMap<Long, Integer>();
        rng = new Random();
    }

    public Integer FindNextHopId (long flow_id, long packet_size_in_bits) {
        if (token_.TryConsumeBucket(packet_size_in_bits)) {
            return direct_path_;
        }

        if (flow_id_to_next_hop_.containsKey(flow_id)) {
            return flow_id_to_next_hop_.get(flow_id);
        }
        final int random_number = this.rng.nextInt(ecmp_paths_.size());
        Integer next_hop = ecmp_paths_.get(random_number);
        flow_id_to_next_hop_.put(flow_id, next_hop);
        return next_hop;
    }
    
    
}