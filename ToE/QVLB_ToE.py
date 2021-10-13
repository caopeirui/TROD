from utils.base import DcnBase
import numpy as np
import math
from utils.base import DcnBase

def find_p_percentile(traffic_seq, p, i, j):
    record = []
    for tm in traffic_seq:
        record.append(tm[i][j])
    record = np.array(record)
    return np.percentile(record, p)


class Topology(DcnBase):
    def topology(self, config_file, *args, **kwargs):
        super().topology(config_file, *args, **kwargs)
        traffic_seq = kwargs['traffic_seq']
        topo = self.get_topology(traffic_seq)

        return topo

    
    def get_burst_a(self):
        return self._burst_a
    

    def get_threshold(self):
        return self._threshold


    def get_topology(self, traffic_seq):
        self._traffic_sequence = traffic_seq
        self._traffic_count = len(traffic_seq)

        pods_num = self.get_pods_num()
        bandwidth = self.get_bandwidth()

        threshold = np.zeros((pods_num, pods_num))
        for i in range(pods_num):
            for j in range(pods_num):
                threshold[i][j] = find_p_percentile(traffic_seq, 90, i, j)

        burst_a = self.calc_burst_a(traffic_seq, threshold)
        #  burst_a 
        self._burst_a = burst_a

        ori_topo = np.zeros((pods_num, pods_num))
        for i in range(pods_num):
            for j in range(pods_num):
                if i != j:
                    ori_topo[i][j] = 2 * burst_a[i] * burst_a[j] / burst_a.sum() / bandwidth[i][j]

        topo = self._compute_topology(ori_topo)
        topo = self.to_integer_topo(topo)
        topo = self.fill_residual_links(topo)

        #  threshold,  threshold 
        self._threshold = (topo - ori_topo) * self.get_bandwidth()

        return topo


    def calc_burst_a(self, traffic_seq, threshold):
        pods_num = self.get_pods_num()
        burst_a = np.zeros(pods_num)
        for i in range(pods_num):
            for tm in traffic_seq:
                delta_sum = 0
                for j in range(pods_num):
                    if i != j:
                        delta = tm[i][j] - threshold[i][j]
                        delta = 0 if delta < 0 else delta
                        delta_sum += delta
                if delta_sum > burst_a[i]:
                    burst_a[i] = delta_sum

        for i in range(pods_num):
            for tm in traffic_seq:
                delta_sum = 0
                for j in range(pods_num):
                    if i != j:
                        delta = tm[j][i] - threshold[j][i]
                        delta = 0 if delta < 0 else delta
                        delta_sum += delta
                if delta_sum > burst_a[i]:
                    burst_a[i] = delta_sum
        return burst_a

    def _compute_topology(self, base_topology):
        # Set the diagonal entries as 0.
        for tm in self._traffic_sequence:
            for pod in range(self._pods_num):
                tm[pod][pod] = 0

        remain_ingress = self._r_ingress.astype(np.float)
        remain_egress = self._r_egress.astype(np.float)
        remain_rows = [i for i in range(self._pods_num)]
        remain_cols = [i for i in range(self._pods_num)]

        d_ij = np.zeros((self._pods_num, self._pods_num), dtype=np.float)

        # Set up base topology
        for i in range(self._pods_num):
            for j in range(self._pods_num):
                d_ij[i][j] = base_topology[i][j]
                remain_ingress[j] -= base_topology[i][j]
                remain_egress[i] -= base_topology[i][j]

        while True:
            # Computes the max scaling factor
            mlu = [self._find_mlu(tm, remain_rows, remain_cols, remain_ingress, remain_egress) for tm in self._traffic_sequence]
            max_tm = np.zeros((self._pods_num, self._pods_num), dtype=np.float)
            for multiple in range(0, self._traffic_count):
                tm = self._traffic_sequence[multiple]
                if mlu[multiple] < 0.0001:
                    continue
                max_scale_up = 1 / mlu[multiple]
                for row in remain_rows:
                    for col in remain_cols:
                        max_tm[row][col] = max(tm[row][col] * max_scale_up, max_tm[row][col])
            overall_mlu = self._find_mlu(max_tm, remain_rows, remain_cols, remain_ingress, remain_egress)
            if overall_mlu < 0.0001:
                break
            overall_scale_up = 1 / overall_mlu
            # Updates topology
            for row in remain_rows:
                for col in remain_cols:
                    scale_entry = max_tm[row][col] * overall_scale_up
                    remain_egress[row] -= scale_entry
                    remain_ingress[col] -= scale_entry
                    d_ij[row][col] += scale_entry
            # Remove rows and cols with no capacity
            rows_to_be_removed = []
            for row in remain_rows:
                if remain_egress[row] < 0.0001:
                    rows_to_be_removed.append(row)
            for row in rows_to_be_removed:
                remain_rows.remove(row)
            cols_to_be_removed = []
            for col in remain_cols:
                if remain_ingress[col] < 0.0001:
                    cols_to_be_removed.append(col)
            for col in cols_to_be_removed:
                remain_cols.remove(col)
            # Break if remain_ingress or remain_egress is empty
            if len(remain_rows) == 0 or len(remain_cols) == 0:
                break
            
        return d_ij


    def _find_mlu(self, tm, rows, cols, ingress_capacity, egress_capacity):
        mlu = 0.0
        for row in rows:
            mlu = max(mlu, sum([tm[row][col] for col in cols]) / egress_capacity[row])
        for col in cols:
            mlu = max(mlu, sum([tm[row][col] for row in rows]) / ingress_capacity[col])
        return mlu


if __name__ == "__main__":
    pass