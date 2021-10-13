import sys
import numpy as np
import math

import preprocess.find_traffic_cluster as ftc
from root_constant import ROOT_PATH
from utils.base import cosine_similarity
from utils.base import DcnBase


def traffic_norm(traffic_array, conf_file):
    """
      traffic array
      traffic array
    """
    conf_obj = DcnBase()
    conf_obj.load_init_config(conf_file)
    #  port bandwidth
    port_bandwidth = conf_obj.get_bandwidth()
    pod_num = conf_obj.get_pods_num()
    #  pod bandwidth  
    #    pod 
    pod_bandwidth = port_bandwidth[0][0] * conf_obj._r_egress[0]
    # -------method1  pod 
    #  pod   
    egress_traffic = traffic_array.sum(axis = 1) / pod_bandwidth
    ingress_traffic = traffic_array.sum(axis = 0) / pod_bandwidth
    egress_max = egress_traffic.max()
    ingress_max = ingress_traffic.max()
    norm_baseline = max(egress_max, ingress_max)

    del conf_obj

    return traffic_array / norm_baseline
    # --------
    
    # ------- method2
    # traffic    
    #  baseline 
    """egress_traffic = traffic_array.sum(axis = 1) / pod_bandwidth
    ingress_traffic = traffic_array.sum(axis = 0) / pod_bandwidth
    new_traffic = np.zeros((pod_num, pod_num), dtype = float)
    for i in range(pod_num):
        for j in range(pod_num):
            baseline = max(egress_traffic[i], ingress_traffic[j])
            if baseline != 0:
                new_traffic[i][j] = traffic_array[i][j] / baseline
            else:
                new_traffic[i][j] = traffic_array[i][j]

    return new_traffic"""
    # -------


def get_traffic(**kwargs):
    """
     
     
     k-means  representative_traffic
     WINDOW_SIZE  future_traffic
    """
    start_index = kwargs['start_index']
    # scale = kwargs['scale']
    kmeans_num = kwargs['kmeans_num']
    # overprovision = kwargs['overprovision']
    traffic_file = kwargs['TRAFFIC_FILE']
    conf_file = kwargs['CONF_FILE']
    window_size = kwargs['WINDOW_SIZE']

    traffic_seq = get_ori_traffic_seq_by_csv(traffic_file)
    #   WINDOW_SIZE = 4032   (5m * 4032 = 14 days)
    train_traffic = traffic_seq[start_index : start_index + window_size]
    future_traffic = traffic_seq[start_index + window_size : start_index + window_size * 2]

    #  k-means 
    clustering_algorithm = ftc.TrafficKMeans(k = kmeans_num)  
    # Use similarity based approach to find representative traffic matrices
    # clustering_algorithm = ftc.SimilarityBasedClustering()
    for a_traffic in train_traffic:
        # a_traffic = traffic_norm(a_traffic, conf_file)    # test norm
        clustering_algorithm.add_a_traffic(a_traffic)
    representative_traffic = clustering_algorithm.get_rep_traffic()
    print("Number of representative traffic: ", len(representative_traffic))


    return train_traffic, representative_traffic, future_traffic
    # normlize tarin traffic
    # new_train_traffic = []
    # for a_traffic in train_traffic:
    #     a_traffic = traffic_norm(a_traffic, conf_file)
    #     new_train_traffic.append(a_traffic)
    
    # return new_train_traffic, representative_traffic, future_traffic
    


def get_ori_traffic_seq_by_csv(file_name):
    with open(file_name, 'r') as f:
        row_list = f.read().splitlines()

    traffic_list = []
    record_total = []
    for row in row_list:
        ori = row.split(',')
        pod_num = int(math.sqrt(len(ori)))
        a_traffic = []
        for i in range(pod_num):
            a_traffic.append(ori[i * pod_num : i * pod_num + pod_num])
        a_traffic = np.array(a_traffic).astype(int)
        traffic_list.append(a_traffic)
        record_total.append(a_traffic.sum())

    #  total traffic   3    
    total_tm_mean = sum(record_total) / len(record_total)
    new_traffic_seq = []
    for tm in traffic_list:
        if tm.sum() < total_tm_mean * 3:
            new_traffic_seq.append(tm)
    ori_traffic = np.array(new_traffic_seq)
    return ori_traffic


def get_ori_traffic_seq_by_npy(file_name):
    """ 
    """
    traffic_history = np.load(file_name, allow_pickle = True)[0]
    traffic_seq = []
    for timestamp, traffic in traffic_history.items():
        # pod 0
        pod_num = traffic.shape[0]
        for i in range(pod_num):
            traffic[i][i] = 0
        traffic_seq.append(traffic / 300 * 8 / 1000000000)

    return np.array(traffic_seq)


def scale_traffic(traffic_seq, conf_file, train_traffic):
    scale_traffic_seq = []
    record_shape = train_traffic[0].shape
    max_mat = get_place_max(train_traffic).reshape(record_shape)
    busy_coe = conf_file['busy_coe']
    threshold_den = conf_file['threshold_den']
    for a_traffic in traffic_seq:
        import simulation.ToE.max_min_fairness as mmf
        obj = mmf.Topology()
        obj.load_init_config(conf_file)
        ret = obj._analyze_tm(a_traffic, busy_coe, threshold_den)
        new_traffic = a_traffic.copy()
        new_traffic[ret == 1] = max_mat[ret == 1]
        scale_traffic_seq.append(new_traffic)

    return scale_traffic_seq


def get_svd_SV(traffic_seq):
    flatten_traffic = []
    for traffic in traffic_seq:
        flatten_traffic.append(traffic.flatten())
    
    flatten_traffic = np.array(flatten_traffic)
    U, S, V = np.linalg.svd(flatten_traffic)
    result = []
    for i in range(20):
        result.append(S[i]*V[i])
    return result


def get_place_var(traffic_seq):
    flatten_traffic = []
    for traffic in traffic_seq:
        tmp = traffic.flatten()
        flatten_traffic.append(tmp)
    
    return np.var(flatten_traffic, axis = 0)

def get_place_cov(traffic_seq):
    flatten_traffic = []
    for traffic in traffic_seq:
        flatten_traffic.append(traffic.flatten())
    
    return np.cov(flatten_traffic, rowvar=False)


def get_place_mean(traffic_seq):
    flatten_traffic = []
    for traffic in traffic_seq:
        tmp = traffic.flatten()
        flatten_traffic.append(tmp)
    
    return np.mean(flatten_traffic, axis = 0)


def get_place_max(traffic_seq):
    flatten_traffic = []
    for traffic in traffic_seq:
        flatten_traffic.append(traffic.flatten())
    
    return np.max(flatten_traffic, axis = 0)


def get_place_min(traffic_seq):
    flatten_traffic = []
    for traffic in traffic_seq:
        flatten_traffic.append(traffic.flatten())
    
    return np.min(flatten_traffic, axis = 0)


def standardize(data, method = 'norm'):
    if method == 'norm':
        tmp = np.array(data)
        return (tmp - tmp.min()) / (tmp.max() - tmp.min())
    elif method == 'zscore':
        tmp = np.array(data)
        return (tmp - tmp.mean()) / (tmp.std())


if __name__ == "__main__":
    test = get_ori_traffic_seq_by_npy(f'{ROOT_PATH}/data/g_data/8pod_traffic.npy')
    start_index = 12096 + 4032 + 1910
    np.set_printoptions(precision=2, suppress=True, linewidth=200)
    for i in range(start_index, start_index + 20):
        print(test[i])
