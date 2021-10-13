package ch.ethz.systems.netbench.ext.threshold_routing;

import ch.ethz.systems.netbench.core.Simulator;

public class TokenBucket {
    // Tokens arrival period in nanoseconds. 
    public static long PERIOD = 1000;

    private long max_tokens_in_bits_;
    private long cur_num_tokens_in_bits_;
    private long num_tokens_per_refill_;
    private long last_refill_time_in_ns_;

    
    public TokenBucket (long max_burst_in_bits, double rate_bps) {
        max_tokens_in_bits_ = max_burst_in_bits;
        num_tokens_per_refill_ = (long) rate_bps * PERIOD / 1000000000;
        cur_num_tokens_in_bits_ = num_tokens_per_refill_;
        last_refill_time_in_ns_ = 0;
    }

    public boolean TryConsumeBucket (long packet_size_in_bits) {
        // Refill tokens first.
        long now = Simulator.getCurrentTime();
        long num_periods = (now - last_refill_time_in_ns_) / PERIOD;
        if (num_periods > 0) {
            cur_num_tokens_in_bits_ += num_periods * num_tokens_per_refill_;
            if (cur_num_tokens_in_bits_ > max_tokens_in_bits_) {
                cur_num_tokens_in_bits_ = max_tokens_in_bits_;
            }
            last_refill_time_in_ns_ += num_periods * PERIOD;
        }
        if (cur_num_tokens_in_bits_ >= packet_size_in_bits) {
            cur_num_tokens_in_bits_ -= packet_size_in_bits;
            return true;
        } else {
            return false;
        }
    }
}