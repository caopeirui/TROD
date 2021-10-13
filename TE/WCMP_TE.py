import numpy as np
from utils.base import DcnBase
import math
import gurobipy as grb
from root_constant import ROOT_PATH
from utils.linear_programming import LinearProgramming

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
        """ gurobi 
        """
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

        m = grb.Model('traffic_engineering_grb')
        m.Params.OutputFlag = 0
        mlu = m.addVar(lb = 0, vtype = grb.GRB.CONTINUOUS, name='mlu')

        name_w_ikj = [
            f'w_{i}_{k}_{j}'
            for i in range(1, pods_num + 1)
            for k in range(pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != k and i != j and j != k
        ]
        w = m.addVars(name_w_ikj, lb = 0, ub = 1, vtype = grb.GRB.CONTINUOUS, name='w')

        #  
        m.addConstrs(
                grb.quicksum(
                    w[f'w_{k}_{i}_{j}'] + w[f'w_{i}_{j}_{k}']
                    if k != 0 else w[f'w_{i}_{0}_{j}']
                    for k in range(0, pods_num + 1)
                    if k != i and k != j
                ) == 0
                for i in range(1, pods_num + 1)
                for j in range(1, pods_num + 1)
                if i != j and capacity[i - 1][j - 1] == 0 
            )

        # summation(w_ikj) = 1
        m.addConstrs(
            (grb.quicksum(
                w[f'w_{i}_{k}_{j}'] for k in range(0, pods_num + 1)
                if k != i and k != j
            ) == 1
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != j),
            name = 'sumOneConstrs'
        )
        # summation(T_ij*w_) <= u * capacity
        if len(traffic.shape) == 2:
            #  
            m.addConstrs(
                grb.quicksum(
                    traffic[k - 1][j - 1] * w[f'w_{k}_{i}_{j}']
                    + traffic[i - 1][k - 1] * w[f'w_{i}_{j}_{k}']
                    if k != 0 else traffic[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                    for k in range(0, pods_num + 1)
                    if k != i and k != j
                ) <= mlu * capacity[i - 1][j - 1]
                for i in range(1, pods_num + 1)
                for j in range(1, pods_num + 1)
                if i != j
            )
            # hop count constraints
            m.addConstr(
                grb.quicksum(
                    traffic[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                    for i in range(1, pods_num + 1)
                    for j in range(1, pods_num + 1)
                    if i != j
                ) >= np.sum(traffic) * self._direct_ratio
            )

        elif len(traffic.shape) == 3:
            #  
            for t in traffic:
                m.addConstrs(
                    grb.quicksum(
                        t[k - 1][j - 1] * w[f'w_{k}_{i}_{j}']
                        + t[i - 1][k - 1] * w[f'w_{i}_{j}_{k}']
                        if k != 0 else t[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                        for k in range(0, pods_num + 1)
                        if k != i and k != j
                    ) <= mlu * capacity[i - 1][j - 1]
                    for i in range(1, pods_num + 1)
                    for j in range(1, pods_num + 1)
                    if i != j
                )
                # hop count constraints
                m.addConstr(
                    grb.quicksum(
                        t[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                        for i in range(1, pods_num + 1)
                        for j in range(1, pods_num + 1)
                        if i != j
                    ) >= np.sum(t) * self._direct_ratio
                )

        m.setObjective(mlu, grb.GRB.MINIMIZE)
        # m.write('debug.lp')
        m.optimize()
        if m.status == grb.GRB.Status.OPTIMAL:
            # print(m.objVal)
            solution = m.getAttr('X', w)
            w_routing = {}
            for w_name in name_w_ikj:
                w_routing[w_name] = solution[w_name]
            self._w_routing = w_routing
            return w_routing, m.objVal
        else:
            print('No solution')


    def desensitized_traffic_engineering(self, actual_traffic, mlu):
        """ gurobi 
        """
        if not isinstance(actual_traffic, np.ndarray):
            traffic = np.array(actual_traffic)
        else:
            traffic = actual_traffic
    
        if not isinstance(self._bandwidth, np.ndarray):
            bandwidth = np.array(self._bandwidth)
        else:
            bandwidth = self._bandwidth

        pods_num = self._pods_num
        topology_pods = self._topology_pods
        capacity = self._topology_pods * bandwidth

        m = grb.Model('traffic_engineering_grb')
        m.Params.OutputFlag = 0

        name_w_ikj = [
            f'w_{i}_{k}_{j}'
            for i in range(1, pods_num + 1)
            for k in range(pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != k and i != j and j != k
        ]
        w = m.addVars(name_w_ikj, lb = 0, ub = 1, vtype = grb.GRB.CONTINUOUS, name='w')

        #  
        m.addConstrs(
                grb.quicksum(
                    w[f'w_{k}_{i}_{j}'] + w[f'w_{i}_{j}_{k}']
                    if k != 0 else w[f'w_{i}_{0}_{j}']
                    for k in range(0, pods_num + 1)
                    if k != i and k != j
                ) == 0
                for i in range(1, pods_num + 1)
                for j in range(1, pods_num + 1)
                if i != j and capacity[i - 1][j - 1] == 0 
            )

        # summation(w_ikj) = 1
        m.addConstrs(
            (grb.quicksum(
                w[f'w_{i}_{k}_{j}'] for k in range(0, pods_num + 1)
                if k != i and k != j
            ) == 1
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != j),
            name = 'sumOneConstrs'
        )
        # sensitivity constraints
        sensitivity = m.addVar(lb = 0, vtype = grb.GRB.CONTINUOUS, name='sensitivity')
        m.addConstrs(
            (w[f'w_{i}_{0}_{j}'] <= sensitivity * topology_pods[i - 1][j - 1]
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != j),
            name = 'sensitivityConstrs1'
        )

        m.addConstrs(
            (w[f'w_{i}_{k}_{j}'] <= sensitivity * topology_pods[i - 1][k - 1]
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            for k in range(1, pods_num + 1)
            if i != j and i != k and j != k),
            name = 'sensitivityConstrs2'
        )

        m.addConstrs(
            (w[f'w_{i}_{k}_{j}'] <= sensitivity * topology_pods[k - 1][j - 1]
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            for k in range(1, pods_num + 1)
            if i != j and i != k and j != k),
            name = 'sensitivityConstrs3'
        )
        # summation(T_ij*w_) <= u * capacity
        if len(traffic.shape) == 2:
            #  
            m.addConstrs(
                grb.quicksum(
                    traffic[k - 1][j - 1] * w[f'w_{k}_{i}_{j}']
                    + traffic[i - 1][k - 1] * w[f'w_{i}_{j}_{k}']
                    if k != 0 else traffic[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                    for k in range(0, pods_num + 1)
                    if k != i and k != j
                ) <= mlu * capacity[i - 1][j - 1]
                for i in range(1, pods_num + 1)
                for j in range(1, pods_num + 1)
                if i != j
            )
            # hop count constraints
            m.addConstr(
                grb.quicksum(
                    traffic[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                    for i in range(1, pods_num + 1)
                    for j in range(1, pods_num + 1)
                    if i != j
                ) >= np.sum(traffic) * self._direct_ratio
            )

        elif len(traffic.shape) == 3:
            #  
            for t in traffic:
                m.addConstrs(
                    grb.quicksum(
                        t[k - 1][j - 1] * w[f'w_{k}_{i}_{j}']
                        + t[i - 1][k - 1] * w[f'w_{i}_{j}_{k}']
                        if k != 0 else t[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                        for k in range(0, pods_num + 1)
                        if k != i and k != j
                    ) <= mlu * capacity[i - 1][j - 1]
                    for i in range(1, pods_num + 1)
                    for j in range(1, pods_num + 1)
                    if i != j
                )
                # hop count constraints
                m.addConstr(
                    grb.quicksum(
                        t[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                        for i in range(1, pods_num + 1)
                        for j in range(1, pods_num + 1)
                        if i != j
                    ) >= np.sum(t) * self._direct_ratio
                )

        m.setObjective(sensitivity, grb.GRB.MINIMIZE)
        # m.write('debug.lp')
        m.optimize()
        if m.status == grb.GRB.Status.OPTIMAL:
            # print(m.objVal)
            solution = m.getAttr('X', w)
            w_routing = {}
            for w_name in name_w_ikj:
                w_routing[w_name] = solution[w_name]
            self._w_routing = w_routing
            return w_routing
        else:
            print('No solution')


    def traffic_engineering_min_ahc(self, actual_traffic, mlu):
        """ gurobi 
        """
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

        m = grb.Model('traffic_engineering_grb')
        m.Params.OutputFlag = 0

        name_w_ikj = [
            f'w_{i}_{k}_{j}'
            for i in range(1, pods_num + 1)
            for k in range(pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != k and i != j and j != k
        ]
        w = m.addVars(name_w_ikj, lb = 0, ub = 1, vtype = grb.GRB.CONTINUOUS, name='w')

        # Add a variable to optimize average hop count.
        min_direct_traffic_ratio = m.addVar(lb = 0, ub = 1, vtype = grb.GRB.CONTINUOUS, name='direct_ratio')
        
        #  
        m.addConstrs(
                grb.quicksum(
                    w[f'w_{k}_{i}_{j}'] + w[f'w_{i}_{j}_{k}']
                    if k != 0 else w[f'w_{i}_{0}_{j}']
                    for k in range(0, pods_num + 1)
                    if k != i and k != j
                ) == 0
                for i in range(1, pods_num + 1)
                for j in range(1, pods_num + 1)
                if i != j and capacity[i - 1][j - 1] == 0 
            )

        # summation(w_ikj) = 1
        m.addConstrs(
            (grb.quicksum(
                w[f'w_{i}_{k}_{j}'] for k in range(0, pods_num + 1)
                if k != i and k != j
            ) == 1
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != j),
            name = 'sumOneConstrs'
        )

        # summation(T_ij*w_) <= u * capacity
        if len(traffic.shape) == 2:
            #  
            m.addConstrs(
                grb.quicksum(
                    traffic[k - 1][j - 1] * w[f'w_{k}_{i}_{j}']
                    + traffic[i - 1][k - 1] * w[f'w_{i}_{j}_{k}']
                    if k != 0 else traffic[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                    for k in range(0, pods_num + 1)
                    if k != i and k != j
                ) <= mlu * capacity[i - 1][j - 1]
                for i in range(1, pods_num + 1)
                for j in range(1, pods_num + 1)
                if i != j
            )
            # hop count constraints
            m.addConstr(
                grb.quicksum(
                    traffic[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                    for i in range(1, pods_num + 1)
                    for j in range(1, pods_num + 1)
                    if i != j
                ) >= np.sum(traffic) * min_direct_traffic_ratio
            )

        elif len(traffic.shape) == 3:
            #  
            for t in traffic:
                m.addConstrs(
                    grb.quicksum(
                        t[k - 1][j - 1] * w[f'w_{k}_{i}_{j}']
                        + t[i - 1][k - 1] * w[f'w_{i}_{j}_{k}']
                        if k != 0 else t[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                        for k in range(0, pods_num + 1)
                        if k != i and k != j
                    ) <= mlu * capacity[i - 1][j - 1]
                    for i in range(1, pods_num + 1)
                    for j in range(1, pods_num + 1)
                    if i != j
                )
                # hop count constraints
                m.addConstr(
                    grb.quicksum(
                        t[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                        for i in range(1, pods_num + 1)
                        for j in range(1, pods_num + 1)
                        if i != j
                    ) >= np.sum(t) * min_direct_traffic_ratio
                )

        m.setObjective(min_direct_traffic_ratio, grb.GRB.MAXIMIZE)
        # m.write('debug.lp')
        m.optimize()
        if m.status == grb.GRB.Status.OPTIMAL:
            # print(m.objVal)
            solution = m.getAttr('X', w)
            w_routing = {}
            for w_name in name_w_ikj:
                w_routing[w_name] = solution[w_name]
            self._w_routing = w_routing
            return w_routing
        else:
            print('No solution')


    def routing(self, config_file, traffic, *args, **kwargs):
        self._direct_ratio = 0
        super().routing(config_file, *args, **kwargs)
        rep_traffic = kwargs['representative_traffic']
        # w_routing, mlu = self.traffic_engineering(traffic)
        w_routing, mlu = self.traffic_engineering(rep_traffic)
        w_routing = self.traffic_engineering_min_ahc(rep_traffic, mlu)
        # w_routing = self.desensitized_traffic_engineering(rep_traffic, mlu)
        # w_routing, mlu = self.linear_programming_routing(traffic)
        return w_routing, mlu


    def linear_programming_routing(self, actual_traffic):
        """ 
        """
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

        lp = LinearProgramming()
        lp.add_var('mlu', 0, None)
        name_w_ikj = [
            f'w_{i}_{k}_{j}'
            for i in range(1, pods_num + 1)
            for k in range(pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != k and i != j and j != k
        ]
        for name in name_w_ikj:
            lp.add_var(name, 0, 1)
        
        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                if i != j:
                    constraint_str = f'1*w_{i}_{0}_{j}'
                    for k in range(1, pods_num + 1):
                        if k != i and k != j:
                            constraint_str += f'+ 1*w_{i}_{k}_{j}'
                    constraint_str += '== 1'
                    lp.add_constraint(constraint_str)
                    print(constraint_str)

        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                if i != j:
                    constraint_str = f'{traffic[i - 1][j - 1]} * w_{i}_{0}_{j}'
                    for k in range(1, pods_num + 1):
                        if k != i and k != j:
                            constraint_str += f'+ {traffic[k - 1][j - 1]} * w_{k}_{i}_{j} + {traffic[i - 1][k - 1]} * w_{i}_{j}_{k}'
                    constraint_str += f'+ -{capacity[i - 1][j - 1]}*mlu <= 0'
                    lp.add_constraint(constraint_str)
                    print(constraint_str)
        print(traffic)
        lp.add_objective('1*mlu')
        # print(lp._var_dict)
        # print('_A_ub: ', lp._A_ub)
        # print('_b_ub: ', lp._b_ub)
        # print('_A_eq: ', lp._A_eq)
        # print('_b_eq: ', lp._b_eq)
        # print('_c: ', lp._c)
        # print('_bounds_list: ', lp._bounds_list)
        # res = lp.solve('revised simplex')
        res = lp.solve()
        print(res)
        exit()
        

if __name__ == "__main__":
    pass
