from utils.base import DcnBase
import numpy as np
import gurobipy as grb
import math
from utils.base import DcnBase
from sklearn.cluster import KMeans

class Topology(DcnBase):
    def topology(self, config_file, *args, **kwargs):
        super().topology(config_file, *args, **kwargs)
        traffic_seq = kwargs['traffic_seq']
        # self.num_k = kwargs['num_k']  //  self._pods_num ?  
        topo = self.get_topology(traffic_seq)

        return topo


    # Computes the direct and indirect pahts between all pod pairs.
    def _derive_interpod_paths(self, nblocks):
        all_paths = [None] * nblocks
        for i in range(nblocks):
            all_paths[i] = [None] * nblocks
            for j in range(nblocks):
                if i != j:
                    all_paths[i][j] = [(i,j), ]
                    for k in range(nblocks):
                        if k != i and k != j:
                            all_paths[i][j].append((i, k, j))
        return all_paths


    def get_topology(self, traffic_seq):
        self._traffic_sequence = traffic_seq
        self._traffic_count = len(traffic_seq)
        topo = self._compute_topology()
        topo = self.to_integer_topo(topo)
        topo = self.fill_residual_links(topo)
        return topo

    def _locate_convex_set(self, nblocks):
        # First convert all the TMs into vectors
        traffic_vector_sequence = []
        vector_dimensions = nblocks * (nblocks - 1)
        for tm in self.traffic_seq:
            traffic_vector = [0] * vector_dimensions
            vector_index = 0
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        traffic_vector[vector_index] = tm[i][j]
                        vector_index += 1
            traffic_vector_sequence.append(traffic_vector)
        # Next, setup the KMC model and locate the cluster heads.
        kmeans = KMeans(n_clusters=number_of_clusters, random_state=0)
        kmeans.fit(traffic_vector_sequence)
        # substep 2 : figure out all the vectors, and which cluster they are binned into
        point_labels = kmeans.predict(traffic_vector_sequence)
        # substep 3 : for each cluster centroid, find the points that belong to this cluster, and find the head of each cluster
        representative_vectors = []
        for _ in range(number_of_clusters):
            head = np.zeros( (vector_dimensions,) )
            representative_vectors.append(head)
        for training_vector, cluster_label in zip(training_vectors, point_labels):
            representative_vectors[cluster_label] = np.maximum(training_vector, representative_vectors[cluster_label])
        # Finally, convert the representative vectors back into TMs
        representative_TMs = []
        for rep_vector in representative_vectors:
            index = 0
            rep_TM = np.zeros((nblocks, nblocks))
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        rep_TM[i][j] = rep_vector[index]
                        index += 1
            representative_TMs.append(rep_TM)
        return representative_TMs


    # Computes the fractional topology for all pod pairs
    def _compute_topology(self):
        nblocks = self.get_pods_num()
        bandwidth = self.get_bandwidth()
        # Set the diagonal entries as 0.
        for tm in self._traffic_sequence:
            for pod in range(self._pods_num):
                tm[pod][pod] = 0

        representative_tms = self._locate_convex_set()
        ## Step 1 : We want to figure out the best case MLU / scale up when there are no restrictions on the 
        ##          ranges of sensitivity
        beta_value, adj_matrix_backup, routing_weights = self._minimize_mlu_for_all_TMs(nblocks, representative_tms, all_paths)
        mlu = 1./beta_value
        sensitivity_distribution = []
        for i in range(nblocks):
            for j in range(nblocks):
                if i != j:
                    sensitivity = 0
                    if adj_matrix_backup[i][j] > 0:
                        sensitivity = max(sensitivity, routing_weights[(i,j)] / adj_matrix_backup[i][j])
                        for k in range(nblocks):
                            if k != i and k != j:
                                sensitivity = max(sensitivity, routing_weights[(k,i,j)] / adj_matrix_backup[i][j])
                                sensitivity = max(sensitivity, routing_weights[(i,j,k)] / adj_matrix_backup[i][j])
                    sensitivity_distribution.append(sensitivity)
        sensitivity_distribution = sorted(sensitivity_distribution)
        max_sensitivity = sensitivity_distribution[-1]
        ## Now we compute the optimized maximum sensitivity
        target_ahc = 2.
        max_sensitivity = self._optimize_max_sensitivity_binary_search(adj_matrix_backup, routing_weights, nblocks, representative_tms, all_paths, (1. / self.mlu_relaxation) * beta_value, target_ahc)
        minimize_multihop_soln = self._minimize_multihop(nblocks, representative_tms, (1. / self.mlu_relaxation) * beta_value, all_paths, max_sensitivity)
        if minimize_multihop_soln is not None:
            adj_matrix_solution, routing_weights, target_ahc = minimize_multihop_soln
            return adj_matrix_solution
        else:
            return adj_matrix_backup


    # Given an MLU, find the topology/routing weights such that the average hop count in the worst case
    # is minimized. This is an LP formulation that directly minimizes hop count.
    def _minimize_multihop(self, nblocks, traffic_matrices, beta_value, all_paths, max_sensitivity):
        ## using QP to reduce multihop reliance
        num_tm = len(traffic_matrices)
        model = Model("minimize multihop directly")
        model.setParam( 'OutputFlag', False )
        interpod_link_counts = [None] * nblocks
        routing_weights_var_hat = {}
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
            interpod_link_counts[i] = [None] * nblocks
            for j in range(nblocks):
                if (i != j):
                    upper_bound = min(self.aurora_network.get_num_links(i), self.aurora_network.get_num_links(j))
                    interpod_link_counts[i][j] = model.addVar(lb=0, ub=upper_bound, obj=0, vtype=GRB.CONTINUOUS, name="lc" + str(i) + ":" + str(j))
                    routing_weight_sum = LinExpr()
                    for path in all_paths[i][j]:
                        routing_weights_var_hat[path] = model.addVar(lb=0, ub=GRB.INFINITY, obj=0., vtype=GRB.CONTINUOUS, name="w_{}".format(path))
                        routing_weight_sum += routing_weights_var_hat[path]
                    model.addConstr(lhs=routing_weight_sum, sense=GRB.EQUAL, rhs=beta_value)
        ## Add radix degree constraints
        for pod in range(nblocks):
            row_constraint = LinExpr()
            col_constraint = LinExpr()
            nlinks = self.aurora_network.get_num_links(pod)
            for target_pod in range(nblocks):
                if target_pod != pod:
                    row_constraint.add(interpod_link_counts[pod][target_pod], mult=1.)
                    col_constraint.add(interpod_link_counts[target_pod][pod], mult=1.)
            model.addConstr(lhs=row_constraint, sense=GRB.LESS_EQUAL, rhs=nlinks)
            model.addConstr(lhs=col_constraint, sense=GRB.LESS_EQUAL, rhs=nlinks)
        ## Add link capacity limit for all paths constraints
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if (i != j):
                        for path in all_paths[i][j]:
                            path_len = len(path)
                            curr_node = path[0]
                            for next_node_index in range(1, path_len, 1):
                                next_node = path[next_node_index]
                                link_capacity_constraints[tm_index][curr_node][next_node] += (routing_weights_var_hat[path] * traffic_matrices[tm_index][i][j])
                                curr_node = next_node
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if (i != j):
                        capacity = min(self.aurora_network.get_link_capacity(i), self.aurora_network.get_link_capacity(j))
                        model.addConstr(lhs=link_capacity_constraints[tm_index][i][j], sense=GRB.LESS_EQUAL, rhs=interpod_link_counts[i][j] * capacity)
        ## Finally, add in the sensitivity constraints
        for i in range(nblocks):
            for j in range(nblocks):
                if i != j:
                    model.addConstr(lhs=routing_weights_var_hat[(i,j)], sense=GRB.LESS_EQUAL, rhs=max_sensitivity * interpod_link_counts[i][j] * beta_value)
                    for k in range(nblocks):
                        if k != i and k != j:
                            model.addConstr(lhs=routing_weights_var_hat[(i,j,k)], sense=GRB.LESS_EQUAL, rhs=max_sensitivity * interpod_link_counts[i][j] * beta_value)
                            model.addConstr(lhs=routing_weights_var_hat[(k,i,j)], sense=GRB.LESS_EQUAL, rhs=max_sensitivity * interpod_link_counts[i][j] * beta_value)
        ## Maximum average hop count constraint
        mlu = 1./beta_value
        min_direct_hop = model.addVar(lb=0, ub=GRB.INFINITY, obj=0, vtype=GRB.CONTINUOUS, name="min_direct_hop")
        for tm_index, tm in zip(range(num_tm), traffic_matrices):
            traffic_sum = sum([sum(x) for x in tm])
            hop_count = LinExpr()
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        hop_count += (routing_weights_var_hat[(i, j)] * (mlu * tm[i][j] / traffic_sum))
            model.addConstr(lhs=min_direct_hop, sense=GRB.LESS_EQUAL, rhs=hop_count)
        # Set up the objective function
        model.setObjective(min_direct_hop, GRB.MAXIMIZE)
        try:
            # start optimizing 
            model.optimize()
            adj_matrix = np.zeros( (nblocks, nblocks,) )
            routing_weights = {}
            for i in range(nblocks):
                for j in range(nblocks):
                    if (i != j):
                        adj_matrix[i][j] = interpod_link_counts[i][j].x
            return adj_matrix
        except GurobiError as e:
            print ("MinimizeMultihop: Error code " + str(e. errno ) + ": " + str(e))
        except AttributeError :
            print ("MinimizeMultihop: Encountered an attribute error ")
        return None


    ## could maybe try to have multiple sets of routing weights, each corresponding to a specific TM
    def _minimize_mlu_for_all_TMs_transformed(self, nblocks, traffic_matrices, all_interblock_paths):
        num_tm = len(traffic_matrices)
        model = Model("minimize mlu")
        model.setParam( 'OutputFlag', False )
        beta = model.addVar(lb=0, ub=GRB.INFINITY, obj=1., vtype=GRB.CONTINUOUS, name="beta")
        fractional_topology_var = [None] * nblocks
        routing_weights_var_hat = {}
        link_capacity_constraints = [None] * num_tm
        for m in range(num_tm):
            link_capacity_constraints[m] = [None] * nblocks
            for i in range(nblocks):
                link_capacity_constraints[m][i] = [None] * nblocks
                for j in range(nblocks):
                    if i != j:
                        link_capacity_constraints[m][i][j] = LinExpr()
        ## Setup the link constraints and all optization variables, which are the link counts and routing variables.
        for i in range(nblocks):
            fractional_topology_var[i] = [None] * nblocks
            for j in range(nblocks):
                if i != j:
                    upper_bound = min(self.aurora_network.get_num_links(i), self.aurora_network.get_num_links(j))
                    fractional_topology_var[i][j] = model.addVar(lb=0, ub=upper_bound, obj=0, vtype=GRB.CONTINUOUS, name="lc" + str(i) + ":" + str(j))
                    routing_weight_sum = LinExpr()
                    for path in all_interblock_paths[i][j]:
                        var = model.addVar(lb=0, ub=GRB.INFINITY, obj=0, vtype=GRB.CONTINUOUS, name="w_{}".format(path))
                        routing_weights_var_hat[path] = var
                        routing_weight_sum += var
                    model.addConstr(lhs=routing_weight_sum, sense=GRB.EQUAL, rhs=beta)

        ## add radix degree constraints
        for pod in range(nblocks):
            row_constraint = LinExpr()
            col_constraint = LinExpr()
            for target_pod in range(nblocks):
                if target_pod != pod:
                    row_constraint.add(fractional_topology_var[pod][target_pod], mult=1.)
                    col_constraint.add(fractional_topology_var[target_pod][pod], mult=1.)
            model.addConstr(lhs=row_constraint, sense=GRB.LESS_EQUAL, rhs=float(self.aurora_network.get_num_links(pod)))
            model.addConstr(lhs=col_constraint, sense=GRB.LESS_EQUAL, rhs=float(self.aurora_network.get_num_links(pod)))

        ## add link utilization constraints
        for tm, tm_index in zip(traffic_matrices, range(num_tm)):
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        for path in all_interblock_paths[i][j]:
                            path_len = len(path)
                            curr_node = path[0]
                            for next_node_index in range(1, path_len, 1):
                                next_node = path[next_node_index]
                                link_capacity_constraints[tm_index][curr_node][next_node] += (routing_weights_var_hat[path] * tm[i][j])
                                curr_node = next_node
        
        ## and then add link capacity limit for all paths constraints
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if (i != j):
                        capacity = min(self.aurora_network.get_link_capacity(i), self.aurora_network.get_link_capacity(j))
                        model.addConstr(lhs=link_capacity_constraints[tm_index][i][j], sense=GRB.LESS_EQUAL, rhs=fractional_topology_var[i][j] * capacity)

        # set up the objective function
        model.setObjective(beta, GRB.MAXIMIZE)
        # start optimizing
        try: 
            model.optimize()
            mlu = 1. / beta.x
            print("Predicted MLU (ToE) is : {}".format(mlu))
            adj_matrix = np.zeros((nblocks, nblocks))
            routing_weights = {}
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        adj_matrix[i][j] = fractional_topology_var[i][j].x
                        for path in all_interblock_paths[i][j]:
                            routing_weights[path] = routing_weights_var_hat[path].x * mlu
            return beta.x, adj_matrix, routing_weights # returns the max scale up factor
        except GurobiError as e:
            print ("_minimize_mlu_for_all_TMs_transformed: Error code " + str(e. errno ) + ": " + str(e))
        except AttributeError :
            print ("_minimize_mlu_for_all_TMs_transformed: Encountered an attribute error ")
        return None

    ## given a specific worst_case_mlu to preserve, ensure that the worst case sensitivity is preserved
    ## max_tm denotes the traffic matrix such that each element (i,j) is the maximum among all representative TMs
    ## here beta represents 1/mlu
    def __attempt_minimizing_max_sensitivity(self, nblocks, representative_tms, all_inter_block_paths, beta_value, max_sensitivity, target_ahc):
        num_tm = len(representative_tms)
        model = Model("Minimize Max Sensitivity MLU preserved")
        model.setParam( 'OutputFlag', False )
        fractional_topology_var = [None] * nblocks
        routing_weights_var_hat = {}
        link_capacity_constraints = [None] * num_tm
        for m in range(num_tm):
            link_capacity_constraints[m] = [None] * nblocks
            for i in range(nblocks):
                link_capacity_constraints[m][i] = [None] * nblocks
                for j in range(nblocks):
                    if i != j:
                        link_capacity_constraints[m][i][j] = LinExpr()
        ## setup the link constraints and all optization variables, 
        ## which are the link counts and routing variables
        for i in range(nblocks):
            fractional_topology_var[i] = [None] * nblocks
            for j in range(nblocks):
                if i != j:
                    upper_bound = min(self.aurora_network.get_num_links(i), self.aurora_network.get_num_links(j))
                    fractional_topology_var[i][j] = model.addVar(lb=0, ub=upper_bound, obj=0, vtype=GRB.CONTINUOUS, name="lc" + str(i) + ":" + str(j))
                    routing_weight_sum = LinExpr()
                    for path in all_inter_block_paths[i][j]:
                        var = model.addVar(lb=0, ub=GRB.INFINITY, obj=0, vtype=GRB.CONTINUOUS, name="w_{}".format(path))
                        routing_weights_var_hat[path] = var
                        routing_weight_sum += var
                    model.addConstr(lhs=routing_weight_sum, sense=GRB.EQUAL, rhs=beta_value)

        ## Add the ingress/egress radix degree constraints
        for pod in range(nblocks):
            row_constraint = LinExpr()
            col_constraint = LinExpr()
            for target_pod in range(nblocks):
                if target_pod != pod:
                    row_constraint.add(fractional_topology_var[pod][target_pod], mult=1.)
                    col_constraint.add(fractional_topology_var[target_pod][pod], mult=1.)
            model.addConstr(lhs=row_constraint, sense=GRB.LESS_EQUAL, rhs=float(self.aurora_network.get_num_links(pod)))
            model.addConstr(lhs=col_constraint, sense=GRB.LESS_EQUAL, rhs=float(self.aurora_network.get_num_links(pod)))

        ## add link utilization constraints of flows for all i j pairs
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        for path in all_inter_block_paths[i][j]:
                            path_len = len(path)
                            curr_node = path[0]
                            for next_node_index in range(1, path_len, 1):
                                next_node = path[next_node_index]
                                link_capacity_constraints[tm_index][curr_node][next_node] += (routing_weights_var_hat[path] * representative_tms[tm_index][i][j])
                                curr_node = next_node
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if (i != j):
                        capacity = min(self.aurora_network.get_link_capacity(i), self.aurora_network.get_link_capacity(j))
                        model.addConstr(lhs=link_capacity_constraints[tm_index][i][j], sense=GRB.LESS_EQUAL, rhs=fractional_topology_var[i][j] * capacity)

        ## Finally, add in the sensitivity constraints, where sensitivity is now defined as sense_ij = max(w_ij/x_ij, w_kj^i/x_ij, w_ik^j)
        for i in range(nblocks):
            for j in range(nblocks):
                if i != j:
                    model.addConstr(lhs=routing_weights_var_hat[(i,j)], sense=GRB.LESS_EQUAL, rhs=max_sensitivity * fractional_topology_var[i][j] * beta_value)
                    for k in range(nblocks):
                        if k != i and k != j:
                            model.addConstr(lhs=routing_weights_var_hat[(i,j,k)], sense=GRB.LESS_EQUAL, rhs=max_sensitivity * fractional_topology_var[i][j] * beta_value)
                            model.addConstr(lhs=routing_weights_var_hat[(k,i,j)], sense=GRB.LESS_EQUAL, rhs=max_sensitivity * fractional_topology_var[i][j] * beta_value)

        ## worst case average hop count constraint
        ## maximum average hop count constraint
        mlu = 1./beta_value
        min_direct_hop = model.addVar(lb=0, ub=GRB.INFINITY, obj=0, vtype=GRB.CONTINUOUS, name="min_direct_hop")
        for tm_index, tm in zip(range(num_tm), representative_tms):
            traffic_sum = sum([sum(x) for x in tm])
            direct_hop_count = LinExpr()
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        direct_hop_count += (routing_weights_var_hat[(i, j)] * (mlu * tm[i][j] / traffic_sum))
            model.addConstr(lhs=2 - target_ahc, sense=GRB.LESS_EQUAL, rhs=direct_hop_count)
        # Set up the objective function
        model.setObjective(0, GRB.MAXIMIZE)
        # Start optimizing
        try: 
            model.optimize()
            routing_weights = {}
            mlu = 1./beta_value
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        for path in all_inter_block_paths[i][j]:
                            routing_weights[path] = routing_weights_var_hat[path].x * mlu
            adj_matrix = np.zeros((nblocks, nblocks))
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        adj_matrix[i][j] = fractional_topology_var[i][j].x
            return adj_matrix, routing_weights # returns the max scale up factor
        except GurobiError as e:
            print ("Try Maximum Sensitivity: Error code " + str(e. errno ) + ": " + str(e))
            return None
        except AttributeError :
            print ("Try Maximum Sensitivity: Encountered an attribute error ")
            return None

    ## Performs binary search 
    def _optimize_max_sensitivity_binary_search(self, logical_topology_adj_matrix, routing_weights, nblocks, representative_tms, all_inter_block_paths, target_beta_value, target_ahc):
        sensitivity_distribution = []
        for i in range(nblocks):
            for j in range(nblocks):
                if i != j:
                    sensitivity = 0
                    if logical_topology_adj_matrix[i][j] > 0:
                        sensitivity = max(sensitivity, routing_weights[(i,j)] / logical_topology_adj_matrix[i][j])
                        for k in range(nblocks):
                            if k != i and k != j:
                                sensitivity = max(sensitivity, routing_weights[(k,i,j)] / logical_topology_adj_matrix[i][j])
                                sensitivity = max(sensitivity, routing_weights[(i,j,k)] / logical_topology_adj_matrix[i][j])
                    sensitivity_distribution.append(sensitivity)
        sensitivity_distribution = sorted(sensitivity_distribution)
        max_sensitivity = 0
        max_sensitivity_ub = sensitivity_distribution[-1]
        max_sensitivity_lb = 0
        upper_sensitivity_error_bound = max_sensitivity_ub * 0.01
        soln = None
        print("max sensitivity upper bound starts with : {}".format(max_sensitivity_ub))
        while True:
            attempted_max_sensitivity = (max_sensitivity_ub + max_sensitivity_lb) / 2.
            # print("Max sensitivity current value : {}".format(attempted_max_sensitivity))
            soln = self.__attempt_minimizing_max_sensitivity(representative_tms, all_inter_block_paths, target_beta_value, attempted_max_sensitivity, target_ahc)
            if max_sensitivity_ub - max_sensitivity_lb <= upper_sensitivity_error_bound:
                if soln is None:
                    soln = self.__attempt_minimizing_max_sensitivity(representative_tms, all_inter_block_paths, target_beta_value, max_sensitivity_ub, target_ahc)
                    assert(soln is not None)
                    max_sensitivity = max_sensitivity_ub
                else:
                    max_sensitivity = attempted_max_sensitivity
                break
            else:
                if soln is None:
                    max_sensitivity_lb = attempted_max_sensitivity
                else:
                    max_sensitivity_ub = attempted_max_sensitivity 
        print("The final max sensitivity is : {}".format(max_sensitivity))
        return max_sensitivity

    ## given a specific worst_case_mlu to preserve, ensure that the worst case sensitivity is preserved
    ## max_tm denotes the traffic matrix such that each element (i,j) is the maximum among all representative TMs
    def _try_minimum_sensitivity(self, nblocks, representative_tms, all_inter_block_paths, beta_value, max_sensitivity, min_sensitivity):
        num_tm = len(representative_tms)
        model = Model("Maximize Min Sensitivity MLU preserved")
        model.setParam( 'OutputFlag', False )
        fractional_topology_var = [None] * nblocks
        routing_weights_var_hat = {}
        link_capacity_constraints = [None] * num_tm

        for m in range(num_tm):
            link_capacity_constraints[m] = [None] * nblocks
            for i in range(nblocks):
                link_capacity_constraints[m][i] = [None] * nblocks
                for j in range(nblocks):
                    if i != j:
                        link_capacity_constraints[m][i][j] = LinExpr()
        ## setup the link constraints and all optization variables, 
        ## which are the link counts and routing variables
        for i in range(nblocks):
            fractional_topology_var[i] = [None] * nblocks
            for j in range(nblocks):
                if i != j:
                    upper_bound = min(self.aurora_network.get_num_links(i), self.aurora_network.get_num_links(j))
                    fractional_topology_var[i][j] = model.addVar(lb=0, ub=upper_bound, obj=0, vtype=GRB.CONTINUOUS, name="lc" + str(i) + ":" + str(j))
                    routing_weight_sum = LinExpr()
                    for path in all_inter_block_paths[i][j]:
                        var = model.addVar(lb=0, ub=GRB.INFINITY, obj=0, vtype=GRB.CONTINUOUS, name="w_{}".format(path))
                        routing_weights_var_hat[path] = var
                        routing_weight_sum += var
                    model.addConstr(lhs=routing_weight_sum, sense=GRB.EQUAL, rhs=beta_value)

        ## Add the ingress/egress radix degree constraints
        for pod in range(nblocks):
            row_constraint = LinExpr()
            col_constraint = LinExpr()
            for target_pod in range(nblocks):
                if target_pod != pod:
                    row_constraint.add(fractional_topology_var[pod][target_pod], mult=1.)
                    col_constraint.add(fractional_topology_var[target_pod][pod], mult=1.)
            model.addConstr(lhs=row_constraint, sense=GRB.LESS_EQUAL, rhs=float(self.aurora_network.get_num_links(pod)))
            model.addConstr(lhs=col_constraint, sense=GRB.LESS_EQUAL, rhs=float(self.aurora_network.get_num_links(pod)))

        ## add link utilization constraints of flows for all i j pairs
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        for path in all_inter_block_paths[i][j]:
                            path_len = len(path)
                            curr_node = path[0]
                            for next_node_index in range(1, path_len, 1):
                                next_node = path[next_node_index]
                                link_capacity_constraints[tm_index][curr_node][next_node] += (routing_weights_var_hat[path] * representative_tms[tm_index][i][j])
                                curr_node = next_node
        for tm_index in range(num_tm):
            for i in range(nblocks):
                for j in range(nblocks):
                    if (i != j):
                        capacity = min(self.aurora_network.get_link_capacity(i), self.aurora_network.get_link_capacity(j))
                        model.addConstr(lhs=link_capacity_constraints[tm_index][i][j], sense=GRB.LESS_EQUAL, rhs=fractional_topology_var[i][j] * capacity)

        ## Finally, add in the sensitivity constraints
        # version 2 constraining
        path_ikj = {}
        binary_ikj = {}
        large_constant = 1000 * self.aurora_network.get_num_links(0)
        for i in range(nblocks):
            for j in range(nblocks):
                if i != j:
                    model.addConstr(lhs=routing_weights_var_hat[(i,j)], sense=GRB.LESS_EQUAL, rhs=max_sensitivity * fractional_topology_var[i][j] * beta_value)
                    model.addConstr(lhs=min_sensitivity * fractional_topology_var[i][j] * beta_value , sense=GRB.LESS_EQUAL, rhs=routing_weights_var_hat[(i,j)])
                    for k in range(nblocks):
                        if k != i and k != j:
                            path_ikj[(i,k,j)] = model.addVar(lb=0, ub=GRB.INFINITY, obj=0, vtype=GRB.CONTINUOUS, name="path_{}_{}_{}".format(i, k, j))
                            binary_ikj[(i,k,j)] = model.addVar(lb=0, ub=1, obj=0, vtype=GRB.CONTINUOUS, name="binary_{}_{}_{}".format(i, k, j))
                            # first impose the binary constraints
                            model.addConstr(lhs=fractional_topology_var[k][j] - fractional_topology_var[i][k], sense=GRB.LESS_EQUAL, rhs=large_constant * binary_ikj[(i,k,j)])
                            model.addConstr(lhs=fractional_topology_var[i][k] - fractional_topology_var[k][j], sense=GRB.LESS_EQUAL, rhs=large_constant * (1 - binary_ikj[(i,k,j)]))
                            # next, impose the X = min(x1,x2) constraints for X
                            model.addConstr(lhs=path_ikj[(i,k,j)], sense=GRB.LESS_EQUAL, rhs=fractional_topology_var[i][k])
                            model.addConstr(lhs=path_ikj[(i,k,j)], sense=GRB.LESS_EQUAL, rhs=fractional_topology_var[k][j])
                            model.addConstr(lhs=path_ikj[(i,k,j)], sense=GRB.LESS_EQUAL, rhs=fractional_topology_var[i][k] + large_constant * (1 - binary_ikj[(i,k,j)]))
                            model.addConstr(lhs=path_ikj[(i,k,j)], sense=GRB.LESS_EQUAL, rhs=fractional_topology_var[k][j] - large_constant * binary_ikj[(i,k,j)])
                            # finally add the path capacity variable into the sensitivity constraint
                            model.addConstr(lhs=routing_weights_var_hat[(i,k,j)], sense=GRB.LESS_EQUAL, rhs=max_sensitivity * path_ikj[(i,k,j)] * beta_value)
                            model.addConstr(lhs=min_sensitivity * path_ikj[(i,k,j)] * beta_value, sense=GRB.LESS_EQUAL, rhs=routing_weights_var_hat[(i,k,j)])
        # set up the objective function
        model.setObjective(0, GRB.MAXIMIZE)
        # start optimizing
        try: 
            model.optimize()
            mlu = 1./beta_value
            routing_weights = {}
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        for path in all_inter_block_paths[i][j]:
                            routing_weights[path] = routing_weights_var_hat[path].x * mlu
            adj_matrix = np.zeros((nblocks, nblocks))
            for i in range(nblocks):
                for j in range(nblocks):
                    if i != j:
                        adj_matrix[i][j] = fractional_topology_var[i][j].x
            return adj_matrix, routing_weights # returns the max scale up factor
        except GurobiError as e:
            print ("Try Minimum Sensitivity: Error code " + str(e. errno ) + ": " + str(e))
            return None
        except AttributeError :
            print ("Try Minimum Sensitivity: Encountered an attribute error ")
            return None
        

if __name__ == "__main__":
    pass