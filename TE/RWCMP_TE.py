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

    
    def traffic_engineering(self, rep_traffic):
        if not isinstance(self._bandwidth, np.ndarray):
            bandwidth = np.array(self._bandwidth)
        else:
            bandwidth = self._bandwidth

        pods_num = self._pods_num
        capacity = self._topology_pods * bandwidth

        m = grb.Model('traffic_engineering_grb')
        m.Params.OutputFlag = 0
        mlu = m.addVar(lb = 0, vtype = grb.GRB.CONTINUOUS, name='mlu')

        w = m.addVars(pods_num + 1, lb = 0, vtype = grb.GRB.CONTINUOUS, name = 'w')
       
        # summation(w_ikj) = 1
        m.addConstr(
            grb.quicksum(
                w[i] for i in range(0, pods_num + 1)
            ) == 1,
            name = 'sumOneConstrs'
        )

        U = np.zeros((pods_num, pods_num), dtype = int)
        Z = np.zeros(pods_num, dtype = int)
        for tm in rep_traffic:
            col_sum = tm.sum(axis=0)
            row_sum = tm.sum(axis=1)
            for i in range(pods_num):
                Z[i] = max(Z[i], col_sum[i], row_sum[i])
                for j in range(pods_num):
                    if i != j:
                        U[i][j] = max(U[i][j], tm[i][j])
    
        m.addConstrs(
            w[0]*U[i][j] + w[j+1]*Z[i] + w[i+1]*Z[j] <= mlu * capacity[i][j]
            for i in range(0, pods_num)
            for j in range(0, pods_num)
            if i != j
        )

        m.addConstr(
            w[0] >= self._min_w0,
            name = 'minW0Constraint'
        )
        
        m.setObjective(mlu, grb.GRB.MINIMIZE)
        # m.write('debug.lp')
        m.optimize()
        if m.status == grb.GRB.Status.OPTIMAL:
            # print(m.objVal)
            solution = m.getAttr('X', w)
            # print(solution)
            w_routing = {}
            
            for i in range(1, pods_num + 1):
                for k in range(0, pods_num + 1):
                    for j in range(1, pods_num + 1):
                        if i != k and i != j and j != k:
                            if k == 0:
                                w_routing[f'w_{i}_0_{j}'] = solution[0] + solution[i] + solution[j]
                            else:
                                w_routing[f'w_{i}_{k}_{j}'] = solution[k]
            self._w_routing = w_routing
            return w_routing, m.objVal
        else:
            print('No solution')


    def routing_by_linprog(self, rep_traffic):
        pods_num = self._pods_num
        capacity = self._topology_pods * self._bandwidth
        lp = LinearProgramming()
        lp.add_var('mlu', 0, None)
        name_w_i = [f'w_{i}' for i in range(0, pods_num + 1)]
        for name in name_w_i:
            lp.add_var(name, 0, 1)
        
        for i in range(0, pods_num + 1):
            if i == 0:
                constraint_str = '1*w_0'
            else:
                constraint_str += f'+ 1*w_{i}'
        constraint_str += '== 1'
        lp.add_constraint(constraint_str)

        U = np.zeros((pods_num, pods_num), dtype = int)
        Z = np.zeros(pods_num, dtype = int)
        for tm in rep_traffic:
            col_sum = tm.sum(axis = 0)
            row_sum = tm.sum(axis = 1)
            for i in range(pods_num):
                Z[i] = max(Z[i], col_sum[i], row_sum[i])
                for j in range(pods_num):
                    if i != j:
                        U[i][j] = max(U[i][j], tm[i][j])
        
        for i in range(1, pods_num + 1):
            for j in range(1, pods_num + 1):
                if i != j:
                    constraint_str = f'{U[i-1][j-1]}*w_0 + {Z[i-1]}*w_{j} + {Z[j-1]}*w_{i} + {-capacity[i-1][j-1]}*mlu <= 0'
                    lp.add_constraint(constraint_str)
        
        lp.add_constraint(f'-1 * w_0 <= -{self._min_w0}')

        lp.add_objective('1*mlu')
        res = lp.solve()

        w_routing = {}
        for i in range(1, pods_num + 1):
            for k in range(0, pods_num + 1):
                for j in range(1, pods_num + 1):
                    if i != k and i != j and j != k:
                        if k == 0:
                            w_routing[f'w_{i}_0_{j}'] = res['w_0'] + res[f'w_{i}'] + res[f'w_{j}']
                        else:
                            w_routing[f'w_{i}_{k}_{j}'] = res[f'w_{k}']
        self._w_routing = w_routing
        return w_routing, res['mlu']


    def routing(self, config_file, traffic, *args, **kwargs):
        super().routing(config_file, *args, **kwargs)
        traffic_seq = kwargs['traffic_seq']
        #   _min_w0
        self._min_w0 = kwargs['min_w0'] if kwargs.get('min_w0') else 0.5
        # w_routing, mlu = self.traffic_engineering(traffic_seq)
        w_routing, mlu = self.routing_by_linprog(traffic_seq)
        return w_routing, mlu


if __name__ == "__main__":
    pass
