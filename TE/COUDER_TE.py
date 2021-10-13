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
        res = lp.solve()
        print(res)
        exit()


    # given an MLU, find the topology/routing weights such that the average hop count in the worst case
    # is minimized. This is an LP formulation that directly minimizes hop count, rather than previously done
    # which is implicit through the use of QP to minimize non-minimal path weights
    def _minimize_multihop_direct(self, logical_topology_adj_matrix, traffic_matrices, worst_case_mlu, worst_case_critical_flow_sensivity):
        ## using QP to reduce multihop reliance
        nblocks = self.aurora_network.get_num_blocks()
        num_tm = len(traffic_matrices)

        model = Model("minimize multihop directly")
        model.setParam( 'OutputFlag', False )
        routing_weights_var = {}
        direct_hopcount = model.addVar(lb=0, ub=1, obj=1., vtype=GRB.CONTINUOUS, name="max_ahc")
        link_capacity_constraints = [None] * num_tm
        for tm_index in range(num_tm):
            link_capacity_constraints[tm_index] = [None] * nblocks
            for i in range(nblocks):
                link_capacity_constraints[tm_index][i] = [None] * nblocks
                for j in range(nblocks):
                    if i != j:
                        link_capacity_constraints[tm_index][i][j] = LinExpr()

        ## setup the link constraints and all optization variables, 
        ## which are the link counts and routing variables
        for i in range(nblocks):
            for j in range(nblocks):
                if (i != j):
                    routing_weight_sum = LinExpr()
                    for path in self.all_interblock_paths[i][j]:
                        routing_weights_var[path] = model.addVar(lb=0, ub=GRB.INFINITY, obj=0., vtype=GRB.CONTINUOUS, name="w_{}".format(path))
                        routing_weight_sum += routing_weights_var[path]
                    model.addConstr(lhs=routing_weight_sum, sense=GRB.EQUAL, rhs=1)
        
        ## add link capacity limit for all paths constraints
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if (i != j):
                        for path in self.all_interblock_paths[i][j]:
                            path_len = len(path)
                            curr_node = path[0]
                            for next_node_index in range(1, path_len, 1):
                                next_node = path[next_node_index]
                                link_capacity_constraints[tm_index][curr_node][next_node] += (routing_weights_var[path] * traffic_matrices[tm_index][i][j])
                                curr_node = next_node
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if (i != j):
                        capacity = min(self.aurora_network.get_link_capacity(i), self.aurora_network.get_link_capacity(j))
                        model.addConstr(lhs=link_capacity_constraints[tm_index][i][j], sense=GRB.LESS_EQUAL, rhs=worst_case_mlu * logical_topology_adj_matrix[i][j] * capacity)

        ## maximum average hop count constraint
        for tm_index in range(num_tm):
            traffic_matrix_ahc = LinExpr()
            traffic_sum = float(sum([sum(x) for x in traffic_matrices[tm_index]]))
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        traffic_matrix_ahc += (routing_weights_var[(i,j)] * (traffic_matrices[tm_index][i][j]/traffic_sum))
            if self.reduce_multihop:
                model.addConstr(lhs=direct_hopcount, sense=GRB.LESS_EQUAL, rhs=traffic_matrix_ahc)
            else:
                model.addConstr(lhs=traffic_matrix_ahc, sense=GRB.LESS_EQUAL, rhs=direct_hopcount)

        ## also account for the sensitivity constraints
        ## stage 3 : add the sensitivity constraints
        for i in range(nblocks):
            for j in range(nblocks):
                if i != j:
                    direct_path_capacity = logical_topology_adj_matrix[i][j]
                    model.addConstr(lhs=routing_weights_var[(i, j)], sense=GRB.LESS_EQUAL, rhs=worst_case_critical_flow_sensivity * direct_path_capacity)
                    for k in range(nblocks):
                        if k != i and k != j:
                            model.addConstr(lhs=routing_weights_var[(k, i, j)], sense=GRB.LESS_EQUAL, rhs=worst_case_critical_flow_sensivity * direct_path_capacity)
                            model.addConstr(lhs=routing_weights_var[(i, j, k)], sense=GRB.LESS_EQUAL, rhs=worst_case_critical_flow_sensivity * direct_path_capacity)

        # set up the objective function
        if self.reduce_multihop:
            model.setObjective(direct_hopcount, GRB.MAXIMIZE)
        else:
            model.setObjective(direct_hopcount, GRB.MINIMIZE)
        # start optimizing
        routing_weights = {}
        try: 
            model.optimize()
            print("ave hop count after routing is : {}".format(2. - direct_hopcount.x))
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        for path in self.all_interblock_paths[i][j]:
                            routing_weights[path] = routing_weights_var[path].x
            #print adj_matrix
        except GurobiError as e:
            print ("MinimizeMultihop Direct Robust Multi Traffic TE: Error code " + str(e. errno ) + ": " + str(e))
        except AttributeError :
            print ("MinimizeMultihop Direct Robust Multi Traffic TE: Encountered an attribute error ")
        return routing_weights


    #def _compute_mlu(self, traffic_matrices, capacity_matrix, all_paths):
    ## Currently does not involve another step that reduces hop count
    def _compute_path_weights_minimize_worstcase_mlu(self, logical_topology_adj_matrix, traffic_matrices):
        nblocks = self.aurora_network.get_num_blocks()
        num_tm = len(traffic_matrices)
        model = Model("Routing minimize MLU for all traffic matrices")
        model.setParam( 'OutputFlag', False )
        mlu = model.addVar(lb=0., ub=GRB.INFINITY, obj=0, vtype=GRB.CONTINUOUS, name="mlu")
        routing_weight_vars = {}
        link_capacity_constraints = [None] * num_tm
        for k in range(num_tm):
            link_capacity_constraints[k] = [None] * nblocks
            for i in range(nblocks):
                link_capacity_constraints[k][i] = [None] * nblocks
                for j in range(nblocks):
                    if i != j:
                        link_capacity_constraints[k][i][j] = LinExpr()
                        #routing_weight_vars[path] = model.addVar(lb=0., ub=1., obj=1., vtype=GRB.CONTINUOUS, name="w_{},{}".format(i,j))
        for i in range(nblocks):
            for j in range(nblocks):
                if i != j:
                    weight_constraint = LinExpr()
                    for path in self.all_interblock_paths[i][j]:
                        routing_weight_vars[path] = model.addVar(lb=0, ub=1., obj=1., vtype=GRB.CONTINUOUS, name="w_{}".format(path))
                        weight_constraint += routing_weight_vars[path]
                    model.addConstr(lhs=weight_constraint, sense=GRB.EQUAL, rhs=1)


        # stage 1: setup all the optimization variables that are the routing weights
        # stage 1.5 : also adds the traffic flow satisfiability constraints
        for tm, tm_index in zip(traffic_matrices, range(num_tm)):
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        for path in self.all_interblock_paths[i][j]:
                            src = path[0]
                            for path_hop in range(1, len(path), 1):
                                dst = path[path_hop]
                                link_capacity_constraints[tm_index][src][dst] += (routing_weight_vars[path] * tm[i][j])
                                src = dst

        ## stage 2 : add the link utilization constraints to the model
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        lu_constraint = link_capacity_constraints[tm_index][i][j]
                        capacity = min(self.aurora_network.get_link_capacity(i), self.aurora_network.get_link_capacity(j))
                        model.addConstr(lhs=lu_constraint, sense=GRB.LESS_EQUAL, rhs=mlu * capacity * logical_topology_adj_matrix[i][j])

        # stage 3: set the objective
        model.setObjective(mlu, GRB.MINIMIZE)
        try: 
            model.optimize()
            routing_weights = {}
            print("Predicted MLU (TE) is : {}".format(mlu.x))
            for path in routing_weight_vars.keys():
                routing_weights[path] = routing_weight_vars[path].x
            return routing_weights, mlu.x
        except GurobiError as e:
            print ("Error code " + str(e. errno ) + ": " + str(e))
            return None
        except AttributeError :
            print ("Encountered an attribute error in Worst case MLU")
            return None

    def _minimize_max_sensitivity(self, logical_topology_adj_matrix, traffic_matrices, worst_case_mlu):
        routing_weights = {}
        nblocks = self.aurora_network.get_num_blocks()
        num_tm = len(traffic_matrices)
        model = Model("Desensitize")
        model.setParam( 'OutputFlag', False )
        routing_weight_vars = {}
        link_capacity_constraints = [None] * num_tm
        beta = model.addVar(lb=0., ub=GRB.INFINITY, obj=1, vtype=GRB.CONTINUOUS, name="beta")

        for k in range(num_tm):
            link_capacity_constraints[k] = [None] * nblocks
            for i in range(nblocks):
                link_capacity_constraints[k][i] = [None] * nblocks
                for j in range(nblocks):
                    if i != j:
                        link_capacity_constraints[k][i][j] = LinExpr()
        
        for i in range(nblocks):
            for j in range(nblocks):
                if i != j:
                    weight_constraint = LinExpr()
                    for path in self.all_interblock_paths[i][j]:
                        routing_weight_vars[path] = model.addVar(lb=0., ub=1., obj=0, vtype=GRB.CONTINUOUS, name="w_{}".format(path))
                        weight_constraint += routing_weight_vars[path]
                    model.addConstr(lhs=weight_constraint, sense=GRB.EQUAL, rhs=1.)
        
        # stage 1: setup all the optimization variables that are the routing weights
        # stage 1.5 : also adds the traffic flow satisfiability constraints
        for tm, tm_index in zip(traffic_matrices, range(len(traffic_matrices))):
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        for path in self.all_interblock_paths[i][j]:
                            src = path[0]
                            for path_hop in range(1, len(path), 1):
                                dst = path[path_hop]
                                link_capacity_constraints[tm_index][src][dst] += (routing_weight_vars[path] * tm[i][j])
                                src = dst

        ## stage 2 : add the link utilization constraints
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        lu_constraint = link_capacity_constraints[tm_index][i][j]
                        capacity = min(self.aurora_network.get_link_capacity(i), self.aurora_network.get_link_capacity(j))
                        model.addConstr(lhs=lu_constraint, sense=GRB.LESS_EQUAL, rhs=worst_case_mlu * capacity * logical_topology_adj_matrix[i][j])
        
        ## stage 3 : add the sensitivity constraints
        for i in range(nblocks):
            for j in range(nblocks):
                if i != j:
                    direct_path_capacity = logical_topology_adj_matrix[i][j]
                    model.addConstr(lhs=routing_weight_vars[(i, j)], sense=GRB.LESS_EQUAL, rhs=beta * direct_path_capacity)
                    for k in range(nblocks):
                        if k != i and k != j:                       
                            model.addConstr(lhs=routing_weight_vars[(k, i, j)], sense=GRB.LESS_EQUAL, rhs=beta * direct_path_capacity)
                            model.addConstr(lhs=routing_weight_vars[(i, j, k)], sense=GRB.LESS_EQUAL, rhs=beta * direct_path_capacity)
        model.setObjective(beta, GRB.MINIMIZE)
        try: 
            model.optimize()
            print("Minimum achievable max sensivity for after TE optimization is : {}".format(beta.x))
            routing_weights = {}
            for path in routing_weight_vars.keys():
                routing_weights[path] = routing_weight_vars[path].x
            return routing_weights, beta.x
        except GurobiError as e:
            print ("Error code " + str(e. errno ) + ": " + str(e))
            return None
        except AttributeError :
            print ("Encountered an attribute error ComputeMinimalSensitivity")
            return None

    def compute_path_weights(self, logical_topology_adj_matrix, traffic_matrices):
        ## newly added for robustness
        #assert(self.numK == len(traffic_matrices))
        nblocks = self.aurora_network.get_num_blocks()
        #print(logical_topology_adj_matrix)
        _, worst_case_mlu = self._compute_path_weights_minimize_worstcase_mlu(logical_topology_adj_matrix, traffic_matrices)
        worst_case_mlu *= self.mlu_relaxation
        _, worst_case_sensivity = self._minimize_max_sensitivity(logical_topology_adj_matrix, traffic_matrices, worst_case_mlu)
        worst_case_sensivity *= self.sensitivity_relaxation
        ##routing_weights = self._minimize_all_link_sensitivity(logical_topology_adj_matrix, traffic_matrices, worst_case_mlu, worst_case_sensivity)

        ## now we need to determine what the largest sensitivity is
        #routing_weights = self._minimize_multihop_direct(logical_topology_adj_matrix, traffic_matrices, worst_case_mlu, worst_case_sensivity)
        routing_weights = self._minimize_multihop_qp(logical_topology_adj_matrix, traffic_matrices, worst_case_mlu, worst_case_sensivity)
        assert(routing_weights is not None)
        
        #routing_weights = self._minimize_all_sensitivity(logical_topology_adj_matrix, traffic_matrices, worst_case_mlu, max_tm)
        number_of_zero_indirect_weights = 0
        for path in routing_weights:
            if len(path) > 2 and routing_weights[path] == 0:
                number_of_zero_indirect_weights += 1
        print("The number of zero indirect weights : {}".format(number_of_zero_indirect_weights))
        if self.return_predicted_mlu:
            return routing_weights, worst_case_mlu
        else:
            return routing_weights


if __name__ == "__main__":
    pass
