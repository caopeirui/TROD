import copy
from utils.base import DcnBase
import numpy as np
import gurobipy as grb
import math

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
        percentile = kwargs['percentile'] if kwargs.get('percentile') else 95

        topo = self.get_topology(traffic_seq, percentile)
        return topo


    def get_threshold(self):
        return self._threshold


    def get_topology(self, traffic_seq, percentile):
        self._traffic_sequence = traffic_seq
        self._traffic_count = len(traffic_seq)

        pods_num = self.get_pods_num()
        bandwidth = self.get_bandwidth()

        threshold = np.zeros((pods_num, pods_num))
        for i in range(pods_num):
            for j in range(pods_num):
                if i == j:
                    continue
                #  threshold   bandwidth     bandwidth
                threshold[i][j] = find_p_percentile(traffic_seq, percentile, i, j) / bandwidth[i][j]

        topo = self._compute_topology(threshold)
        topo = self.to_integer_topo(topo)

        min_gap = 10000
        for i in range(pods_num):
            for j in range(pods_num):
                if i != j and topo[i][j] > threshold[i][j]:
                    min_gap = min(min_gap, topo[i][j] - threshold[i][j])
        # Update the threshold values (Here we assume that all the links have the same bandwidth.)
        self._threshold = np.zeros((pods_num, pods_num))
        for i in range(pods_num):
            for j in range(pods_num):
                if i != j:
                    #  42 43   topo*bandwidth  topo*bandwidth
                    if topo[i][j] > threshold[i][j]:
                        self._threshold[i][j] = (topo[i][j] - min_gap) * bandwidth[i][j]
                    else:
                        self._threshold[i][j] = topo[i][j] * bandwidth[i][j]
        return topo


    def _compute_topology(self, threshold_matrix):
        # Set the diagonal entries as 0.
        pods_num = self._pods_num
        for pod in range(pods_num):
            threshold_matrix[pod][pod] = 0

        remain_ingress = self._r_ingress.astype(np.float)
        remain_egress = self._r_egress.astype(np.float)
        remain_rows = [i for i in range(self._pods_num)]
        remain_cols = [i for i in range(self._pods_num)]

        d_ij = copy.deepcopy(threshold_matrix)
        for i in range(pods_num):
            for j in range(pods_num):
                if i == j:
                    continue
                remain_ingress[j] -= d_ij[i][j]
                remain_egress[i] -= d_ij[i][j]
        for i in range(pods_num):
            if remain_ingress[i] < 0 or remain_egress[i] < 0:
                print("Threshold values are too large!")
                exit()

        while True:
            # Computes the max scaling factor
            delta = self._find_delta(remain_rows, remain_cols, remain_ingress, remain_egress)
            if delta < 0.0001:
                break
            # Updates topology
            for row in remain_rows:
                for col in remain_cols:
                    if row == col:
                        continue
                    remain_egress[row] -= delta
                    remain_ingress[col] -= delta
                    d_ij[row][col] += delta
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


    def _find_delta(self, rows, cols, ingress_capacity, egress_capacity):
        # In the first case, there is only a diagonal entry left. Thus,
        # we cannot allocate capacity any more.
        if len(rows) == 1 and len(cols) == 1 and rows[0] == cols[0]:
            return 0
        delta = egress_capacity[rows[0]]
        num_rows = len(rows)
        num_cols = len(cols)
        for row in rows:
            total_gap = egress_capacity[row]
            if row in cols:
                if num_cols > 1:
                    delta = min(delta, total_gap / (num_cols - 1))
            else:
                delta = min(delta, total_gap / num_cols)
        for col in cols:
            total_gap = ingress_capacity[col]
            if col in rows:
                if num_rows > 1:
                    delta = min(delta, total_gap / (num_rows - 1))
            else:
                delta = min(delta, total_gap / num_rows)
        return delta


if __name__ == "__main__":
    pass
