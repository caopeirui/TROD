from utils.base import DcnBase
import gurobipy as grb
import numpy as np

class Topology(DcnBase):
    def topology(self, config_file, *args, **kwargs):
        super().topology(config_file, *args, **kwargs)
        self._direct_ratio = 0.93
        representative_traffic = kwargs['representative_traffic']
        topo = self.binary_search_fractional_topology(representative_traffic)
        topo = self.to_integer_topo(topo)
        topo = self.fill_residual_links(topo)
        return topo

    def binary_search_fractional_topology(self, traffic_seq):
        topo, alpha_opt = self.get_fractional_topology(traffic_seq, 1)
        lb = 0.0
        ub = 1.0
        while ub - lb > 0.01:
            topo, alpha = self.get_fractional_topology(traffic_seq, (lb + ub) / 2)
            if alpha < alpha_opt * 0.999:
                lb = (lb + ub) / 2
            else:
                ub = (lb + ub) / 2
            if alpha > 0:
                self._d_ij = topo
        return self._d_ij

    def get_fractional_topology(self, traffic_seq, ub):
        pods_num = self._pods_num
        bandwidth = self._bandwidth

        m = grb.Model('WCMP_ToE')
        m.Params.LogToConsole = 0

        alpha = m.addVar(lb = 0, vtype = grb.GRB.CONTINUOUS, name='alpha')

        topology_pods = m.addVars(pods_num, pods_num, vtype = grb.GRB.CONTINUOUS, name = 'x')

        m.addConstrs(
            topology_pods[i, j] >= 1 if i != j else topology_pods[i, j] == 0
            for j in range(pods_num)
            for i in range(pods_num)
        )

        name_w_ikj = [
            f'w_{i}_{k}_{j}'
            for i in range(1, pods_num + 1)
            for k in range(pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != k and i != j and j != k
        ]
        #  w  alpha * weight routing w
        w = m.addVars(name_w_ikj, lb = 0, vtype = grb.GRB.CONTINUOUS, name = 'w')
        
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

        # summation(w_ikj) = alpha
        m.addConstrs(
            (grb.quicksum(
                w[f'w_{i}_{k}_{j}'] for k in range(0, pods_num + 1)
                if k != i and k != j
            ) == alpha
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != j),
            name = 'sumOneConstrs'
        )

        m.addConstrs(
            (w[f'w_{i}_{0}_{j}'] <= ub * topology_pods[i - 1, j - 1]
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            if i != j),
            name = 'sensitivityConstrs1'
        )

        m.addConstrs(
            (w[f'w_{i}_{k}_{j}'] <= ub * topology_pods[i - 1, k - 1]
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            for k in range(1, pods_num + 1)
            if i != j and i != k and j != k),
            name = 'sensitivityConstrs2'
        )

        m.addConstrs(
            (w[f'w_{i}_{k}_{j}'] <= ub * topology_pods[k - 1, j - 1]
            for i in range(1, pods_num + 1)
            for j in range(1, pods_num + 1)
            for k in range(1, pods_num + 1)
            if i != j and i != k and j != k),
            name = 'sensitivityConstrs3'
        )

        for t in traffic_seq:
            m.addConstrs(
                grb.quicksum(
                    t[k - 1][j - 1] * w[f'w_{k}_{i}_{j}']
                    + t[i - 1][k - 1] * w[f'w_{i}_{j}_{k}']
                    if k != 0 else t[i - 1][j - 1] * w[f'w_{i}_{0}_{j}']
                    for k in range(0, pods_num + 1)
                    if k != i and k != j
                ) <= topology_pods[i - 1, j - 1] * bandwidth[i - 1][j - 1]
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

        m.setObjective(alpha, grb.GRB.MAXIMIZE)
        m.optimize()
        # m.write('debug.lp')
        if m.status == grb.GRB.Status.OPTIMAL:
            print(m.objVal)
            solution = m.getAttr('X', topology_pods)
            x_ij = np.zeros((self._pods_num, self._pods_num), dtype=np.float)
            for i in range(pods_num):
                for j in range(pods_num):
                    x_ij[i][j] = solution[i, j]

            # print(m.getAttr('X', w))
            # exit()
            return x_ij, m.objVal
        else:
            print('get_fractional_topology No solution')
            x_ij = np.zeros((self._pods_num, self._pods_num), dtype=np.float)
            return x_ij, 0

        return

if __name__ == "__main__":
    pass