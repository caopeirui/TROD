import numpy as np
from utils.base import DcnBase
import math
from root_constant import ROOT_PATH


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

    
    def traffic_engineering(self, actual_traffic):
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

        w_routing = {}
        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                for k in range(pods_num + 1):
                    if i != k and i != j and j != k:
                        if k == 0:
                            w_routing[f'w_{i}_0_{j}'] = 1
                        else:
                            w_routing[f'w_{i}_{k}_{j}'] = 0
        self._w_routing = w_routing
        return w_routing, None


    def routing(self, config_file, traffic, *args, **kwargs):
        super().routing(config_file, *args, **kwargs)
        w_routing, _ = self.traffic_engineering(traffic)
        # w_routing, mlu = self.linear_programming_routing(traffic)
        # w_routing, mlu = self.again_routing(traffic, mlu)
        return w_routing, None


if __name__ == "__main__":
    pass
