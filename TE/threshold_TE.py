import math
from root_constant import ROOT_PATH
import numpy as np
import gurobipy as grb
from utils.base import DcnBase

class Routing(DcnBase):
    """
    Desc:  routing  link utilization
    """
 
    _w_routing = {}
    
    def __init__(self, topology_pods):
        """
        Desc:  routing     
               pods 
        Inputs:
            - topology_pods(2-dimension list): Number of s_i egress links connected to igress links of s_j.
        """
        self._topology_pods = topology_pods


    
    def traffic_engineering(self, actual_traffic, Sij, virtual_r):
        if not isinstance(actual_traffic, np.ndarray):
            traffic = np.array(actual_traffic)
        else:
            traffic = actual_traffic

        pods_num = self._pods_num

        name_w_ikj = [
            f'w_{i}_{k}_{j}'
            for i in range(1, pods_num + 1)
            for k in range(pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != k and i != j and j != k
        ]
        routing = {k : 0 for k in name_w_ikj}

        R = virtual_r.sum()
        
        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                if i != j:
                    if traffic[i - 1][j - 1] > Sij[i - 1][j - 1]:
                        #  
                        # routing[f'w_{i}_{0}_{j}'] = Sij[i - 1][j - 1] / traffic[i - 1][j - 1]
                        sum_Wikj = 0
                        for k in range(1, pods_num + 1):
                            if k != i and k != j:
                                if traffic[i - 1][j - 1] > 0:
                                    Wikj = max(0.0, traffic[i - 1][j - 1] - Sij[i - 1][j - 1]) \
                                        * virtual_r[k - 1] / (R - min(virtual_r[i - 1], virtual_r[j - 1]))
                                    routing[f'w_{i}_{k}_{j}'] = Wikj / traffic[i - 1][j - 1]
                                    sum_Wikj += Wikj
                                else:
                                    routing[f'w_{i}_{k}_{j}'] = 0
                        if traffic[i - 1][j - 1] > 0:
                            routing[f'w_{i}_{0}_{j}'] = (traffic[i - 1][j - 1] - sum_Wikj) / traffic[i - 1][j - 1]
                    else:
                        routing[f'w_{i}_{0}_{j}'] = 1
     
        self._w_routing = routing

        return routing


    def routing(self, config_file, cur_traffic, *args, **kwargs):
        super().routing(config_file, *args, **kwargs)
        Sij = kwargs['Sij']
        virtual_r = kwargs['virtual_r']
    
        #  traffic threshold     threshold routing 
        #  link utilization 
        # ( ):  threshold  last traffic 
        new_Sij = self.scale_Sij(Sij, virtual_r)
        threshold = new_Sij

        #  evaluate traffic routing   link utilization 
        w_routing = self.traffic_engineering(cur_traffic, threshold, virtual_r)
        return w_routing, None

    def scale_Sij(self, Sij, virtual_r):
        record_shape = Sij.shape
        capacities = self.get_capacities()

        m = grb.Model('scale_Sij')
        m.Params.OutputFlag = 0
        alpha = m.addVar(vtype=grb.GRB.CONTINUOUS, lb=0, name='alpha')
        
        m.addConstrs(
            alpha * (Sij[i][j] + virtual_r[i] * virtual_r[j]) <= capacities[i][j]
            for i in range(record_shape[0])
            for j in range(record_shape[1])
            if i != j
        )

        m.setObjective(alpha, grb.GRB.MAXIMIZE)
        m.optimize()
        if m.status == grb.GRB.Status.OPTIMAL:
            alpha_opt = m.objVal
        else:
            print('scale_Sij No solution')
            exit()

        #     scale Sij
        new_Sij = Sij
        for i in range(record_shape[0]):
            for j in range(record_shape[1]):
                if i != j:
                    new_Sij[i][j] = capacities[i][j] / alpha_opt - virtual_r[i] * virtual_r[j]

        #     threshold
        return new_Sij * alpha_opt
