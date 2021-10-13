"""
 
"""
import pandas as pd
import numpy as np
import math

def cosine_similarity(x, y, norm=False):
    assert len(x) == len(y), "len(x) != len(y)"
    zero_list = [0] * len(x)
    if x == zero_list or y == zero_list:
        return float(1) if x == y else float(0)
    res = np.array([[x[i] * y[i], x[i] * x[i], y[i] * y[i]] for i in range(len(x))])
    cos = sum(res[:, 0]) / (np.sqrt(sum(res[:, 1])) * np.sqrt(sum(res[:, 2])))

    #   [-1,+1]  if norm   [0,1]
    return 0.5 * cos + 0.5 if norm else cos

def data_norm(data):
    tmp = np.array(data)

    return (tmp - tmp.min()) / (tmp.max() - tmp.min())


class DcnBase:
    _traffic_count = 0      #  
    _traffic_sequence = []  #   3   
    _traffic = []           #   2  
    _r_ingress = []         #  pod ingress link 
    _r_egress = []          #  pod egress link 
    _port_bandwidth = []    #  pod 
    _bandwidth = []         #  i j pod  2  
    _pods_num = 0           # pod 
    _topology_pods = []     #  i j pod link  2   

    _d_ij = []              #    i j pod link  2   
    

    def __init__(self):
        pass


    def topology(self, config_file, *args, **kwargs):
        if kwargs.get('updown_ratio'):
            self.load_init_config(config_file = config_file, updown_ratio = kwargs['updown_ratio'])
        else:
            self.load_init_config(config_file = config_file)


    def routing(self, config_file, *args, **kwargs):
        self.load_init_config(config_file = config_file)


    def load_init_config(self, config_file, updown_ratio = 1):
        """
        Desc:  csv   
                   np.ndarray 
        """
        pd_config = pd.read_csv(config_file)
        # TODO:  
        self._r_ingress = np.ceil(np.array(pd_config['r_ingress']) * updown_ratio)
        self._r_egress = np.ceil(np.array(pd_config['r_egress']) * updown_ratio)
        self._port_bandwidth = np.array(pd_config['port_bandwidth'])
        self._pods_num = len(self._r_egress)

        #    
        #  i j pod   
        all_bandwith = []
        for bi in range(self._pods_num):
            tmp = []
            for bj in range(self._pods_num):
                tmp.append(min(self._port_bandwidth[bi], self._port_bandwidth[bj]))
            all_bandwith.append(tmp)
        self._bandwidth = np.array(all_bandwith)


    def set_traffic(self, traffic):
        """
        Desc:  
        """
        self._traffic = traffic


    def add_a_traffic(self, traffic):
        """
        Desc:  
        Inputs:
            - traffic(numpy.narray): 2 .
        """
        # TODO: exception handling. eg:  raise ValueError('xxx')
        #       For example, traffic matrix dimensions aren't 2,
        #       or the number of rows and columns of traffic matrix is not consistent.
        self._traffic_sequence.append(traffic)
        self._traffic_count += 1


    def set_traffic_sequence(self, traffic_sequence):
        """
        Desc:  
        Inputs:
            - traffic_sequence: 3 
        """
        self._traffic_sequence = traffic_sequence
        self._traffic_count = len(self._traffic_sequence)


    def set_d_ij(self, d_ij):
        """
        Desc:  i j pod link  2     
        """
        if not isinstance(d_ij, np.ndarray):
            self._d_ij = np.array(d_ij)
        else:
            self._d_ij = d_ij


    def get_bandwidth(self):
        return self._bandwidth


    def get_pods_egress_capacity(self):
        return self._port_bandwidth * self._r_egress


    def get_pods_ingress_capacity(self):
        return self._port_bandwidth * self._r_ingress


    def get_traffic_sequence(self):
        return self._traffic_sequence


    def get_pods_num(self):
        return self._pods_num


    def traffic_engineering(self):
        """
        Desc:    traffic
        """
        pass


    def satisfy_physical_bound(self, topo_links):
        """ topo    True
             False
        """
        ingress = topo_links.sum(axis = 0)   #  
        egress = topo_links.sum(axis = 1)  #  
        
        flag = True
        for i in range(self._pods_num):
            # links    False
            if ingress[i] > self._r_ingress[i] or egress[i] > self._r_egress[i]:
                flag = False
                break
        return flag

    

    def get_capacities(self):
        topology_pods = np.array(self._topology_pods)
        bandwidth = np.array(self._bandwidth)
        capacities = topology_pods * bandwidth
        return capacities



    def calc_link_utilization(self, traffic):
        """
        Desc:  
        Inputs: 
            - traffic(np.array)  
        Returns:
            ( link lu,  lu,  lu)
        """
        w_routing = self._w_routing
        topology_pods = np.array(self._topology_pods)
        bandwidth = np.array(self._bandwidth)
        capacities = topology_pods * bandwidth

        link_utilization =  [[None] * self._pods_num for _ in range(self._pods_num)]
        alu_numerator = 0
        alu_denominator = 0
        for i in range(1, self._pods_num + 1):
            for j in range(1, self._pods_num + 1):
                if i != j:
                    w_sum = 0
                    for k in range(self._pods_num + 1):
                        if k != i and k != j:
                            if k == 0:
                                w_sum += w_routing[f'w_{i}_0_{j}'] * traffic[i - 1][j - 1]
                            else:
                                w_sum += w_routing[f'w_{k}_{i}_{j}'] * traffic[k - 1][j - 1]
                                w_sum += w_routing[f'w_{i}_{j}_{k}'] * traffic[i - 1][k - 1]
                    
                    if capacities[i - 1][j - 1] > 0:
                        link_utilization[i - 1][j - 1] = w_sum / capacities[i - 1][j - 1]
                        #     200g link   10g link  
                        #   200g link   20   10g link
                        #   link_utilization[i - 1][j - 1] * capacities[i - 1][j - 1]   w_sum
                        alu_numerator += w_sum
                        alu_denominator += capacities[i - 1][j - 1]
                    else:
                        if w_sum < 0.000001:
                            link_utilization[i - 1][j - 1] = 0.0
                        else:
                            print("Traffic allocated to edges with no capacity!")
                            exit(0)

        all_link_utilization = np.array([
            link_utilization[j][i]
            for j in range(len(link_utilization[0]))
            for i in range(len(link_utilization))
            if link_utilization[j][i] is not None
            # and not math.isnan(link_utilization[j][i])
        ])
        norm_alu = alu_numerator / alu_denominator
        return link_utilization, all_link_utilization.max(), all_link_utilization.mean(), norm_alu


    def calc_normal_link_utilization(self, traffic):
        """
        Desc:  
        Inputs: 
            - traffic(np.array)  
        Returns:
            (link_utilization, norm_mlu, alu, mlu)
        """

        topology_pods = np.array(self._topology_pods)
        bandwidth = np.array(self._bandwidth)
        capacities = topology_pods * bandwidth

        link_utilization, mlu, alu, norm_alu = self.calc_link_utilization(traffic)
        output = traffic.sum(axis = 1)
        norm_output = output / capacities.sum(axis = 1)
        max_norm_output = norm_output.max()
        in_put = traffic.sum(axis = 0)
        norm_input = in_put / capacities.sum(axis = 0)
        max_norm_input = norm_input.max()
        if max_norm_input < max_norm_output:
            norm_mlu = mlu / max_norm_output
        else:
            norm_mlu = mlu / max_norm_input

        return link_utilization, mlu, alu, norm_mlu, norm_alu

    def ave_hop_count(self, traffic):
        w_routing = self._w_routing
        topology_pods = np.array(self._topology_pods)
        bandwidth = np.array(self._bandwidth)
        capacities = topology_pods * bandwidth
        link_utilization, _, _, _ = self.calc_link_utilization(traffic)
        sum_lu_cap = 0
        sum_traffic = 0
        for i in range(1, self._pods_num + 1):
            for j in range(1, self._pods_num + 1):
                if i != j:
                    sum_lu_cap += link_utilization[i - 1][j - 1] * capacities[i - 1][j - 1]
                    sum_traffic += traffic[i - 1][j - 1]
        
        res = sum_lu_cap / sum_traffic
        return res 


    def get_allocated_traffic(self, traffic):
        w_routing = self._w_routing

        allocated_traffic = np.zeros((self._pods_num, self._pods_num))
        for i in range(1, self._pods_num + 1):
            for j in range(1, self._pods_num + 1):
                if i != j:
                    w_sum = 0
                    for k in range(self._pods_num + 1):
                        if k != i and k != j:
                            if k == 0:
                                w_sum += w_routing[f'w_{i}_0_{j}'] * traffic[i - 1][j - 1]
                            else:
                                w_sum += w_routing[f'w_{k}_{i}_{j}'] * traffic[k - 1][j - 1]
                                w_sum += w_routing[f'w_{i}_{j}_{k}'] * traffic[i - 1][k - 1]
                    
                    allocated_traffic[i - 1][j - 1] = w_sum
        return allocated_traffic


    def to_integer_topo(self, d_ij):
        """      
        """
        ori_shape = d_ij.shape

        #  list
        flat_topo = d_ij.flatten().tolist()
        # print(flat_topo)
        #      key
        index_frac_dict = {}
        for i in range(len(flat_topo)):
            index_frac_dict[i] = math.modf(flat_topo[i])[0]

        #  sorted list  tuple
        index_frac_list = sorted(index_frac_dict.items(), key = lambda x : x[1], reverse = True)
        
        #  
        integer_topo = np.floor(d_ij)

        # print(integer_topo)
        egress_floor_sum = np.sum(integer_topo, axis=1)
        ingress_floor_sum = np.sum(integer_topo, axis=0)
        res_r_egress = self._r_egress - egress_floor_sum
        res_r_ingress = self._r_ingress - ingress_floor_sum
        # print(res_r_egress)
        # print(res_r_ingress)

        for (index, _) in index_frac_list:
            row = math.floor(index / self._pods_num)
            column = index % self._pods_num
            if row == column:
                continue
            if res_r_egress[row] > 0 and res_r_ingress[column] > 0:
                res_r_egress[row] -= 1
                res_r_ingress[column] -= 1
                integer_topo[row][column] += 1
        if self.satisfy_physical_bound(integer_topo) == False:
            print('Error when mapping fractional topology to integer topology!')
            exit(0)
        
        # print(d_ij)
        # print(integer_topo)
        # print('row sum', np.sum(integer_topo, axis=1))
        # print('column sum', np.sum(integer_topo, axis=0))

        """
        res = [math.floor(i) for i in flat_topo]

        for (index, frac) in index_frac_list:
            res[index] = res[index] + 1
            #  link
            for i in range(self._pods_num):
                if index == i * self._pods_num + i:
                    res[index] = 0
            #  egress ingress      
            tmp = np.array(res).reshape(ori_shape)
            if self.satisfy_physical_bound(tmp) == False:
                res[index] = res[index] - 1
        
        integer_topo = np.array(res).reshape(ori_shape)
        """

        self._d_ij = integer_topo
        return integer_topo


    def fill_residual_links(self, topo_links):
        while self.satisfy_physical_bound(topo_links) == True:
            #  np.array    copy   deepcopy 
            last_topo_links = topo_links.copy()
            for i in range(self._pods_num):
                for j in range(self._pods_num):
                    #  link 
                    if i != j:
                        topo_links[i][j] += 1
                        if self.satisfy_physical_bound(topo_links) == False:
                            #    
                            topo_links[i][j] -= 1            
            have_change = False
            for i in range(self._pods_num):
                for j in range(self._pods_num):
                    if topo_links[i][j] != last_topo_links[i][j] and i != j:
                        have_change = True
            if have_change == False:
                break
        self._d_ij = topo_links
        return topo_links


    def calc_sensitivity(self):
        w_routing = self._w_routing
        topology_pods = np.array(self._topology_pods)
        bandwidth = np.array(self._bandwidth)
        capacities = topology_pods

        pods_num = self.get_pods_num()
        
        sensitivity = 0
        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                for k in range(pods_num + 1):
                    if i != j and i != k and j != k:
                        if k == 0:
                            tmp = w_routing[f'w_{i}_0_{j}'] / capacities[i - 1][j - 1]
                            sensitivity = tmp if tmp > sensitivity else sensitivity
                        else:
                            tmp = w_routing[f'w_{k}_{i}_{j}'] / capacities[k - 1][j - 1]
                            sensitivity = tmp if tmp > sensitivity else sensitivity
                            tmp = w_routing[f'w_{i}_{j}_{k}'] / capacities[i - 1][k - 1]
                            sensitivity = tmp if tmp > sensitivity else sensitivity                        
        return sensitivity


if __name__ == "__main__":
    obj = DcnBase()
    obj.load_init_config('~/Desktop/project/reconfigurable_topology/config/pods_config.csv')
    
