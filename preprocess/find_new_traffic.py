import numpy as np
import gurobipy as grb
from utils.base import DcnBase

def get_new_traffic(traffic_seq, use_max_capacity, **kwargs):
    conf_obj = DcnBase()
    conf_obj.load_init_config(kwargs['CONF_FILE'])

    pods_egress_capacity = conf_obj.get_pods_egress_capacity()
    pods_ingress_capacity = conf_obj.get_pods_ingress_capacity()

    record_shape = traffic_seq[0].shape
    U = np.ones(record_shape[0], dtype=float)   # traffic's row sum upper bound
    V = np.ones(record_shape[1], dtype=float)   # traffic's column sum upper bound
    S = np.ones(record_shape, dtype=float) * 999999999    # per-entry lower bound S_ij

    # ---method2  pod ---
    if use_max_capacity == True:
        U = pods_egress_capacity
        V = pods_ingress_capacity
    # ---method2 end---
    for traffic in traffic_seq:
        # ---method1---
        if use_max_capacity == False:
            row_sum = traffic.sum(axis=1)
            col_sum = traffic.sum(axis=0)
        # ------
        for i in range(record_shape[0]):
            # ---method1  traffic ---
            if use_max_capacity == False:
                if row_sum[i] > U[i]:
                    U[i] = row_sum[i]
                if col_sum[i] > V[i]:
                    V[i] = col_sum[i]
            # ---method1 end---

            for j in range(record_shape[1]):
                if traffic[i][j] < S[i][j]:
                    S[i][j] = traffic[i][j]

    m = grb.Model('find_virtual_r')
    m.Params.OutputFlag = 0
    virtual_r = m.addVars(record_shape[0], vtype = grb.GRB.CONTINUOUS, name = 'r_i')
    R = m.addVar(vtype = grb.GRB.CONTINUOUS, name='R')

    m.addConstrs(
         U[i] - grb.quicksum(
            S[i][j] for j in range(record_shape[1]) if i != j
        ) <= virtual_r[i] * (R - virtual_r[i])
        for i in range(record_shape[0])
    )

    m.addConstrs(
         V[j] - grb.quicksum(
            S[i][j] for i in range(record_shape[1]) if i != j
        ) <= virtual_r[j] * (R - virtual_r[j])
        for j in range(record_shape[1])
    )

    m.addConstr(
        grb.quicksum(
            virtual_r[i] for i in range(record_shape[0])
        ) == R
    )

    m.setObjective(R, grb.GRB.MINIMIZE)
    # m.write('find_virtual_r.lp')
    m.optimize()
    if m.status == grb.GRB.Status.OPTIMAL:
        solution = m.getAttr('X', virtual_r)
        virtual_r_array = np.zeros(record_shape[0], dtype=float)
        for i in range(record_shape[0]):
            virtual_r_array[i] = solution[i]
    else:
        print('find_virtual_r No solution')
        exit()

    new_traffic = np.zeros(record_shape, dtype=float)
    for i in range(record_shape[0]):
        for j in range(record_shape[1]):
            if i != j:
                new_traffic[i][j] = S[i][j] + solution[i] * solution[j]

    # np.set_printoptions(precision=2, suppress=True, linewidth=200)
    # print(new_traffic)
    return new_traffic, S, virtual_r_array
