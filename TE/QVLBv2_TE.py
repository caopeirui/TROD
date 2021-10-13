import math
from root_constant import ROOT_PATH
import numpy as np
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


    def calc_normal_link_utilization(self, evaluation_traffic):
        threshold = self._threshold
        self.convert_weight(evaluation_traffic, threshold)
        return super().calc_normal_link_utilization(evaluation_traffic)


    def calc_sensitivity(self):
        pods_num = self.get_pods_num()
        bandwidth = self.get_bandwidth()
        remain_capacity = self._topology_pods * bandwidth - self._threshold
        routing = {}
        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                if i != j:
                    total_2hop_capacity = 0
                    for k in range(1, pods_num + 1):
                        if k != i and k != j:
                            total_2hop_capacity += min(remain_capacity[i - 1][k - 1], remain_capacity[k - 1][j - 1])
                    for k in range(0, pods_num + 1):
                        if k != i and k != j:
                            if k != 0:
                                routing[f'w_{i}_{k}_{j}'] = min(remain_capacity[i - 1][k - 1], remain_capacity[k - 1][j - 1]) / total_2hop_capacity
                            else:
                                routing[f'w_{i}_0_{j}'] = 0

        ori_routing = self._w_routing
        self._w_routing = routing
        sen = super().calc_sensitivity()
        self._w_routing = ori_routing
        return sen

    
    def convert_weight(self, actual_traffic, threshold):
        """
        Desc:  threshold weight
         
        """
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

        remain_capacity = self._topology_pods * self.get_bandwidth() - threshold
        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                if i != j:
                    if traffic[i - 1][j - 1] > threshold[i - 1][j - 1]:
                        total_2hop_capacity = 0
                        for k in range(1, pods_num + 1):
                            if k != i and k != j:
                                total_2hop_capacity += min(remain_capacity[i - 1][k - 1], remain_capacity[k - 1][j - 1])
                        routing[f'w_{i}_{0}_{j}'] = threshold[i - 1][j - 1] / traffic[i - 1][j - 1]
                        for k in range(1, pods_num + 1):
                            if k != i and k != j:
                                Wikj = (traffic[i - 1][j - 1] - threshold[i - 1][j - 1]) * min(remain_capacity[i - 1][k - 1], remain_capacity[k - 1][j - 1]) / total_2hop_capacity
                                routing[f'w_{i}_{k}_{j}'] = Wikj / traffic[i - 1][j - 1]
                    else:
                        routing[f'w_{i}_{0}_{j}'] = 1
     
        self._w_routing = routing
        
        return routing


    def routing(self, config_file, traffic, *args, **kwargs):
        super().routing(config_file, *args, **kwargs)
        self._threshold = kwargs['threshold']
        return None, None
