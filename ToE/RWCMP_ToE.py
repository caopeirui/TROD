from traffic.self_gen.generate_traffic_from_fb import constraint_random
from utils.linear_programming import LinearProgramming
from utils.base import DcnBase
import gurobipy as grb
import numpy as np

class Topology(DcnBase):
    def topology(self, config_file, *args, **kwargs):
        super().topology(config_file, *args, **kwargs)
        traffic_seq = kwargs['traffic_seq']
        self._min_w0 = kwargs['min_w0'] if kwargs.get('min_w0') else 0.5

        # topo =  self.get_fractional_topology(traffic_seq)
        topo =  self.solve_topo_by_LP(traffic_seq)
        topo = self.to_integer_topo(topo)
        topo = self.fill_residual_links(topo)
        return topo

    
    def solve_topo_by_LP(self, traffic_seq):
        pods_num = self._pods_num
        bandwidth = self._bandwidth

        lp = LinearProgramming()
        lp.add_var('alpha', 0, None)
        for i in range(pods_num):
            for j in range(pods_num):
                lp.add_var(f'x_{i}_{j}', 0, None)
        
        for i in range(pods_num + 1):
            lp.add_var(f'aw_{i}', 0, None)

        for i in range(pods_num):
            for j in range(pods_num):
                if i == j:
                    lp.add_constraint(f'1*x_{i}_{j} == 0')
                else:
                    lp.add_constraint(f'1*x_{i}_{j} >= 1')
                    #  
                    lp.add_constraint(f'x_{i}_{j} + -1*x_{j}_{i} == 0')
        

        aw_cons_str = '1*aw_0'
        for i in range(1, pods_num + 1):
            aw_cons_str += f'+ 1*aw_{i}'
        aw_cons_str += '+ -1*alpha == 0'
        lp.add_constraint(aw_cons_str)

        for i in range(0, pods_num):
            cons_str1 = f'1*x_{i}_0'
            cons_str2 = f'1*x_0_{i}'
            for j in range(1, pods_num):
                cons_str1 += f'+ 1*x_{i}_{j}'
                cons_str2 += f'+ 1*x_{j}_{i}'
            cons_str1 += f'<= {self._r_egress[i]}'
            cons_str2 += f'<= {self._r_ingress[i]}'
            lp.add_constraint(cons_str1)
            lp.add_constraint(cons_str2)

        U = np.zeros((pods_num, pods_num), dtype = int)
        Z = np.zeros(pods_num, dtype = int)
        for tm in traffic_seq:
            col_sum = tm.sum(axis=0)
            row_sum = tm.sum(axis=1)
            for i in range(pods_num):
                Z[i] = max(Z[i], col_sum[i], row_sum[i])
                for j in range(pods_num):
                    if i != j:
                        U[i][j] = max(U[i][j], tm[i][j])

        for i in range(0, pods_num):
            for j in range(0, pods_num):
                if i != j:
                    cons_str = f'{U[i][j]}*aw_0 + {Z[i]}*aw_{j+1} + {Z[j]}*aw_{i+1} + {-bandwidth[i][j]} * x_{i}_{j} <= 0'
                    lp.add_constraint(cons_str)

        lp.add_constraint(f'{self._min_w0} * alpha + -1 * aw_0 <= 0')

        lp.add_objective('-1*alpha')
        res = lp.solve()

        x_ij = np.zeros((pods_num, pods_num), dtype=np.float)
        for i in range(0, pods_num):
            for j in range(0, pods_num):
                x_ij[i][j] = res[f'x_{i}_{j}']

        self._d_ij = x_ij
        return x_ij


    def get_fractional_topology(self, traffic_seq):
        pods_num = self._pods_num
        bandwidth = self._bandwidth
        
        m = grb.Model('RWCMP_ToE')
        m.Params.LogToConsole = 0

        alpha = m.addVar(lb = 0, vtype = grb.GRB.CONTINUOUS, name='alpha')

        topology_pods = m.addVars(pods_num, pods_num, vtype = grb.GRB.CONTINUOUS, name = 'x')
        aw = m.addVars(pods_num + 1, lb = 0, vtype = grb.GRB.CONTINUOUS, name = 'aw')

        m.addConstrs(
            topology_pods[i, j] >= 1 if i != j else topology_pods[i, j] == 0
            for j in range(pods_num)
            for i in range(pods_num)
        )

        #  
        m.addConstrs(
            topology_pods[i, j] == topology_pods[j, i]
            for j in range(pods_num)
            for i in range(pods_num)
            if i != j
        )
        
        # summation(d_ij) <= r_i_(e/in)gress
        m.addConstrs(
            grb.quicksum(
                topology_pods[i, j] for j in range(pods_num)
            ) <= self._r_egress[i]
            for i in range(pods_num)
        )
        m.addConstrs(
            grb.quicksum(
                topology_pods[j, i] for j in range(pods_num)
            ) <= self._r_ingress[i]
            for i in range(pods_num)
        )
       
        # summation(w_ikj) = 1
        m.addConstr(
            grb.quicksum(
                aw[i] for i in range(0, pods_num + 1)
            ) == alpha,
            name = 'sumOneConstrs'
        )

        U = np.zeros((pods_num, pods_num), dtype = int)
        Z = np.zeros(pods_num, dtype = int)
        for tm in traffic_seq:
            col_sum = tm.sum(axis=0)
            row_sum = tm.sum(axis=1)
            for i in range(pods_num):
                Z[i] = max(Z[i], col_sum[i], row_sum[i])
                for j in range(pods_num):
                    if i != j:
                        U[i][j] = max(U[i][j], tm[i][j])

        m.addConstrs(
            aw[0]*U[i][j] + aw[j+1]*Z[i] + aw[i+1]*Z[j] <= bandwidth[i][j] * topology_pods[i, j]
            for i in range(0, pods_num)
            for j in range(0, pods_num)
            if i != j
        )
        m.addConstr(
            aw[0] >= self._min_w0 * alpha,
            name = 'minW0Constraint'
        )
        m.setObjective(alpha, grb.GRB.MAXIMIZE)
        # m.write('debug.lp')
        m.optimize()
        if m.status == grb.GRB.Status.OPTIMAL:
            # print(m.objVal)
            solution = m.getAttr('X', topology_pods)
            # print(solution)
            # exit()
            x_ij = np.zeros((pods_num, pods_num), dtype=np.float)
            for i in range(pods_num):
                for j in range(pods_num):
                    x_ij[i][j] = solution[i, j]
            self._d_ij = x_ij
            return x_ij
        else:
            print('get_fractional_topology No solution')

        return

if __name__ == "__main__":
    pass
