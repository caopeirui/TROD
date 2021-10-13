import os
import preprocess.preprocess as pp
import preprocess.find_traffic_cluster as ftc
import pandas as pd
from root_constant import ROOT_PATH

NETBENCH_DIRECTORY = f'{ROOT_PATH}/netbench'

# NOTICE
# 4pod
POD_CONFIG = f'{ROOT_PATH}/config/fb_database_config.csv'
TRAFFIC_CSV = f'{ROOT_PATH}/traffic/fb_data/database_traffic.csv'

# 8pod
# POD_CONFIG = f'{ROOT_PATH}/config/fb_web_config.csv'
# TRAFFIC_CSV = f'{ROOT_PATH}/traffic/fb_data/web_traffic.csv'

# 9pod
# POD_CONFIG = f'{ROOT_PATH}/config/fb_hadoop_config.csv'
# TRAFFIC_CSV = f'{ROOT_PATH}/traffic/fb_data/hadoop_traffic.csv'

# hw4pod
# POD_CONFIG = f'{ROOT_PATH}/config/hw_4pod_config.csv'
# TRAFFIC_CSV = f'{ROOT_PATH}/traffic/fb_data/database_traffic.csv'


def get_scale_traffic(traffic_file, pod_conf_file):
    """  8 1000 traffic  train_traffic
         workload 
      
    Args:
        traffic_file:
        pod_conf_file:
        start:  
        quantity:  train traffic sequence 
        eva_index: evalution traffic 
    """
    ori_traffic = pp.get_ori_traffic_seq_by_csv(traffic_file)
    col_sum_max = 0
    row_sum_max = 0
    #  
    for t in ori_traffic:
        col_sum_max = max(t.sum(axis = 0).max(), col_sum_max)
        row_sum_max = max(t.sum(axis = 1).max(), row_sum_max)

    sum_max = max(col_sum_max, row_sum_max)
    pod_conf = pd.read_csv(pod_conf_file)
    capacity = pod_conf.loc[0, 'r_ingress'] * pod_conf.loc[0, 'port_bandwidth']
    scale = capacity / sum_max
    
    all_traffic = ori_traffic * scale

    #  TROD 
    train_traffic_seq = all_traffic[::80]
    return train_traffic_seq, all_traffic

#  
def generate_traffic_file(pod_num, traffic_seq, output_path):
    #  1s ns
    contents = ''
    start_time_ns = 0
    for traffic in traffic_seq:
        #  1s 100 
        for _ in range(100):
            for src in range(pod_num):
                for dst in range(pod_num):
                    #  Gbit pod bandwidth Gbit = 10^9 bit
                    #  bytes  link_bandwidth_bit_per_ns bit
                    #  1s 100    10^7 / 8 = 1250000
                    if src != dst and traffic[src][dst] * 1250000 > 0:
                        # time of entry, source id, destination id, size in terms of bytes
                        integer_byte = int(traffic[src][dst] * 1250000)
                        contents += f'{start_time_ns},{src},{dst},{integer_byte}\n'
            start_time_ns += 10000000

    with open(output_path + '/flow_arrivals.txt', 'w') as f:
        f.write(contents)


def generate_topo_file(topo, output_path):
    """
    tor/server  0   switch pod_num 
    """
    topo = topo.astype(int)
    pod_num = topo.shape[0]

    # netbench    0   
    contents = f'|V|={pod_num * 2}\n'
    contents += f'|E|={3 * topo.sum()}\n'
    contents += f'ToRs=incl_range(0,{pod_num - 1})\n'
    contents += f'Servers=incl_range(0,{pod_num - 1})\n'
    contents += f'Switches=incl_range({pod_num}, {pod_num + pod_num - 1})\n'
    
    #  
    # TODO:  link 
    for i in range(pod_num):
        for j in range(pod_num):
            if i != j:
                for _ in range(topo[i][j]):
                    contents += f'{pod_num + i} {pod_num + j}\n'
    
    # server    
    row_sum = topo.sum(axis = 1)
    col_sum = topo.sum(axis = 0)

    for i in range(pod_num):
        for _ in range(row_sum[i]):
            contents += f'{i} {pod_num + i}\n'
        for _ in range(col_sum[i]):
            contents += f'{pod_num + i} {i}\n'

    with open(output_path + '/topo.txt', 'w') as f:
        f.write(contents)


def generate_properties_file(output_path, pod_conf_file):
    #  port_bandwidth
    pod_conf = pd.read_csv(pod_conf_file)
    port_bandwidth = pod_conf.loc[0,'port_bandwidth']

    contents = '# Topology\n'
    contents += f'scenario_topology_file={output_path}/topo.txt\n\n'
    
    contents += '# Run Info\n'
    contents += 'run_folder_name=result\n'
    contents += f'run_folder_base_dir={output_path}/\n'
    # contents += 'run_time_ns=523411000\n'
    contents += 'run_time_ns=2123456789\n'
    contents += 'finish_when_first_flows_finish=2212456\n'
    # contents += 'enable_smooth_rtt=true\n'
    # contents += 'enable_record_resend=true\n'
    # contents += 'enable_log_flow_throughput=false\n'
    contents += 'seed=8278897294\n\n'
    
    contents += '# Network Device\n'
    contents += 'transport_layer=simple_dctcp\n'
    contents += 'network_device=threshold_switch\n'
    contents += 'network_device_routing=threshold_routing\n'
    contents += f'threshold_path_weights_filename={output_path}/routing.txt\n'
    contents += 'network_device_intermediary=identity\n\n'

    contents += '# Output port\n'
    contents += 'enable_log_port_queue_state=true\n'
    contents += 'output_port=ecn_tail_drop_diff_queue_size\n'
    # contents += 'output_port_max_queue_size_bytes=150000\n'
    # contents += 'output_port_ecn_threshold_k_bytes=10000\n'
    contents += 'output_port_max_queue_size_bytes=600000\n'
    contents += 'output_port_ecn_threshold_k_bytes=200000\n'
    contents += 'link_reconfig_latency_ns=0\n\n'

    contents += '# Link\n'
    contents += 'link=reconfigurable_link\n'
    contents += 'link_delay_ns=50\n'
    contents += 'server_link_delay_ns=10\n'
    contents += f'link_bandwidth_bit_per_ns={port_bandwidth}\n\n'

    contents += '# Traffic\n'
    contents += 'traffic=traffic_arrivals_file\n'
    contents += f'traffic_arrivals_filename={output_path}/flow_arrivals.txt\n'

    with open(output_path + '/test.properties', 'w') as f:
        f.write(contents)


def run_simulator(netbench_path, test_path):
    """ netbench 
    """
    cmd = f'java -jar -ea {netbench_path}/NetBench.jar {test_path}/test.properties'
    os.system(cmd)


if __name__ == "__main__":
    pass