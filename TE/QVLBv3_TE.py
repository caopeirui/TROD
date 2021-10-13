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


    def convert_weight(self, traffic_seq, threshold):
        """
        Desc:  threshold weight
         
        """
        pods_num = self._pods_num
        U = np.zeros((pods_num, pods_num), dtype = int)
        for tm in traffic_seq:
            for i in range(pods_num):
                for j in range(pods_num):
                    if i != j:
                        U[i][j] = max(U[i][j], tm[i][j])
        
        name_w_ikj = [
            f'w_{i}_{k}_{j}'
            for i in range(1, pods_num + 1)
            for k in range(pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != k and i != j and j != k
        ]
        routing = {k : 0 for k in name_w_ikj}

        remain_capacity = self._topology_pods * self.get_bandwidth() - threshold
        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                if i != j:
                    if U[i - 1][j - 1] > threshold[i - 1][j - 1]:
                        total_2hop_capacity = 0
                        for k in range(1, pods_num + 1):
                            if k != i and k != j:
                                total_2hop_capacity += min(remain_capacity[i - 1][k - 1], remain_capacity[k - 1][j - 1])
                        routing[f'w_{i}_{0}_{j}'] = threshold[i - 1][j - 1] / U[i - 1][j - 1]
                        for k in range(1, pods_num + 1):
                            if k != i and k != j:
                                Wikj = (U[i - 1][j - 1] - threshold[i - 1][j - 1]) * min(remain_capacity[i - 1][k - 1], remain_capacity[k - 1][j - 1]) / total_2hop_capacity
                                routing[f'w_{i}_{k}_{j}'] = Wikj / U[i - 1][j - 1]
                    else:
                        routing[f'w_{i}_{0}_{j}'] = 1
     
        self._w_routing = routing
        
        return routing


    def routing(self, config_file, traffic, *args, **kwargs):
        super().routing(config_file, *args, **kwargs)
        threshold = kwargs['threshold']
        traffic_seq = kwargs['traffic_seq']
        w_routing = self.convert_weight(traffic_seq, threshold)
        self._w_routing = w_routing
        return w_routing, None
