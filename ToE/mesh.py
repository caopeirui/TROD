import sys
import numpy as np
from root_constant import ROOT_PATH
from utils.base import DcnBase

class Topology(DcnBase):
    """
    Desc:  mesh topology 
    """
    def get_connects_matrix(self):
        """
        Desc:  pod 
        """
        pods_num = self._pods_num
        d_ij = np.ones((pods_num, pods_num))
        d_ij = d_ij * self._r_egress[0] / (pods_num - 1)
        for i in range(pods_num):
            d_ij[i][i] = 0

        d_ij = self.to_integer_topo(d_ij)
        return d_ij

    def get_connects_matrix_unevenly(self):
        """
        Desc:  mesh topology pods 
        """
        x_ij = np.zeros((self._pods_num, self._pods_num), dtype=np.int)
        pods_links = self._r_egress + self._r_ingress
        #  list tuple pods links   
        pods_num_links = [(i, pods_links[i]) for i in range(len(pods_links))]
        #  
        pods_num_links = sorted(pods_num_links, key = lambda t: t[1])

        #  pod pod 
        for i in range(self._pods_num - 1):
            current_pod = pods_num_links[i][0]
            #  pod   
            used_links = x_ij[:, current_pod].sum() + x_ij[current_pod, :].sum()
            current_links = pods_num_links[i][1] - used_links

            #  pod pod 
            remain_cnt = self._pods_num - 1 - i
            mean_links = current_links // remain_cnt
            #  
            for j in range(i + 1, self._pods_num):
                #  pod 
                dst_pod = pods_num_links[j][0]
                # print(dst_pod)
                x_ij[current_pod][dst_pod] += mean_links // 2
                x_ij[dst_pod][current_pod] += mean_links // 2

        return x_ij


    def topology(self, config_file, *args, **kwargs):
        super().topology(config_file, *args, **kwargs)
        return self.get_connects_matrix()



if __name__ == "__main__":
    pass