import numpy as np
from utils.base import DcnBase
import math
import gurobipy as grb
from root_constant import ROOT_PATH

class LPscale(DcnBase):
    """
    Desc:  topo  traffic scale
    """
 
    _w_routing = {}
    
    def __init__(self, topology_pods, conf_file):
        """
        Desc:  routing     
               pods 
        Inputs:
            - topology_pods(2-dimension list): Number of s_i egress links connected to igress links of s_j.
        """
        self.load_init_config(conf_file)
        self._topology_pods = topology_pods


    def scale_traffic(self, a_traffic):
        pods_num = self._pods_num
        used_flag = np.zeros((pods_num, pods_num), dtype=int)
        res = a_traffic.copy()
   
        w_routing, alpha = self._linear_programming(res, used_flag)
        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                if i != j and used_flag[i - 1][j - 1] == 0:
                    sum_t = 0
                    for k in range(pods_num + 1):
                        if k != i and k != j:
                            sum_t += w_routing[f'w_{i}_{k}_{j}']
                    #     alpha * t_ij == W_ij + sum(W_jik)
                    #  alpha 
                    #   if alpha * a_traffic[i - 1][j - 1] == sum_t:
                    if alpha * a_traffic[i - 1][j - 1] >= sum_t * 0.9:
                        used_flag[i - 1][j - 1] = 1
                        res[i - 1][j - 1] = sum_t

        w_routing, alpha = self._linear_programming(res, used_flag)
        if alpha is None:
            print(' ')
        else:
            res[used_flag == 0] = res[used_flag == 0] * alpha
        return res


    def _linear_programming(self, actual_traffic, used_flag = None):
        if not isinstance(actual_traffic, np.ndarray):
            traffic = np.array(actual_traffic)
        else:
            traffic = actual_traffic
    
        if not isinstance(self._bandwidth, np.ndarray):
            bandwidth = np.array(self._bandwidth)
        else:
            bandwidth = self._bandwidth

        pods_num = self._pods_num
        capacity = self._topology_pods * bandwidth

        m = grb.Model('LP_scale')
        m.Params.OutputFlag = 0
        alpha = m.addVar(lb = 0, vtype = grb.GRB.CONTINUOUS, name = 'alpha')

        #  w_ routing 
        name_w_ikj = [
            f'w_{i}_{k}_{j}'
            for i in range(1, pods_num + 1)
            for k in range(pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != k and i != j and j != k
        ]
        w = m.addVars(name_w_ikj, lb = 0, vtype = grb.GRB.CONTINUOUS, name = 'w')

        # alpha * t_ij <= W_ij + sum(W_ikj)
        m.addConstrs(
            grb.quicksum(
                w[f'w_{i}_{k}_{j}']
                if k != 0 else w[f'w_{i}_{0}_{j}']
                for k in range(0, pods_num + 1)
                if k != i and k != j
            ) >= alpha * traffic[i - 1][j - 1]
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != j and used_flag[i - 1][j - 1] == 0
        )
        m.addConstrs(
            grb.quicksum(
                w[f'w_{i}_{k}_{j}']
                if k != 0 else w[f'w_{i}_{0}_{j}']
                for k in range(0, pods_num + 1)
                if k != i and k != j
            ) >= traffic[i - 1][j - 1]
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != j and used_flag[i - 1][j - 1] == 1
        )

        # summation(W_) == capacity
        m.addConstrs(
            grb.quicksum(
                w[f'w_{k}_{i}_{j}'] + w[f'w_{i}_{j}_{k}']
                if k != 0 else w[f'w_{i}_{0}_{j}']
                for k in range(0, pods_num + 1)
                if k != i and k != j
            ) == capacity[i - 1][j - 1]
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != j
        )

        m.setObjective(alpha, grb.GRB.MAXIMIZE)
        # m.write('debug.lp')
        m.optimize()
        if m.status == grb.GRB.Status.OPTIMAL:
            w_routing = m.getAttr('X', w)
            self._w_routing = w_routing
            return w_routing, m.objVal
        else:
            # np.set_printoptions(precision=2, suppress=True, linewidth=200)
            # print(traffic)
            # m.write('debug.lp')
            print('No solution')
            return None, None


if __name__ == "__main__":
    pass
