import os
import sys
import importlib
import pandas as pd
import trigger_TROD_netbench.common as cm


def generate_all_files(test_path, pod_num, ToE, TE, updown_ratio, start_index):
    """ 
    Args:
        test_path:  
        ToE:  
        TE:  
        ...
    """
    train_traffic_seq, all_traffic = cm.get_scale_traffic(cm.TRAFFIC_CSV, cm.POD_CONFIG)

    evaluation_traffic = all_traffic[start_index: start_index + 3]

    #  traffic
    cm.generate_traffic_file(pod_num, evaluation_traffic, test_path)

    #  
    m_ToE = importlib.import_module(f'ToE.{ToE}')
    print(m_ToE)
    Topology = getattr(m_ToE, 'Topology')

    #  
    m_TE = importlib.import_module(f'TE.{TE}')
    print(m_TE)
    Routing = getattr(m_TE, 'Routing')

    #  
    ToE_obj = Topology()
    topology_conf = {}
    # NOTICE
    topology_conf['traffic_seq'] = all_traffic
    topology_conf['percentile'] = 80
    topology_conf['updown_ratio'] = updown_ratio    #  TROD ,  clos
    topo = ToE_obj.topology(cm.POD_CONFIG, **topology_conf)
    print(topo)
    print('Threshold matrix:')
    print(ToE_obj.get_threshold())

    #  topo 
    cm.generate_topo_file(topo, test_path)

    #  routing 
    generate_routing_file(ToE_obj.get_threshold(), test_path)

    #  properties 
    cm.generate_properties_file(test_path, cm.POD_CONFIG)


def generate_routing_file(threshold, output_path):
    res = ''
    # node_id,instruction_type,threshold_entry_id,threshold,next_hop,num_ecmp_ports,ecmp_next_hop1,...
    # node_id,instruction_type,src_id,dst_id,threshold_entry_id
    threshold = threshold.astype(int)
    pod_num = threshold.shape[0]

    threshold_entry_id = 0
    # server  
    for i in range(pod_num):
        for j in range(pod_num):
            if i != j:
                res += f'{i},threshold,{threshold_entry_id},0,{pod_num + i},1,{pod_num + i}\n'
                res += f'{i},match,{i},{j},{threshold_entry_id}\n'
                threshold_entry_id += 1

    #   switch i  
    for i in range(pod_num):
        for j in range(pod_num):
            if i != j:
                #  
                res += f'{pod_num + i},threshold,{threshold_entry_id},{threshold[i][j] * 1000000000},{pod_num + j},{pod_num - 2}'
                for k in range(pod_num):
                    if k != j and k != i:
                        res += f',{pod_num + k}'
                res += f'\n{pod_num + i},match,{i},{j},{threshold_entry_id}\n'
                threshold_entry_id += 1
                #  
                res += f'{pod_num + i},threshold,{threshold_entry_id},0,{i},1,{i}\n'
                res += f'{pod_num + i},match,{j},{i},{threshold_entry_id}\n'
                threshold_entry_id += 1

    #   switch mid  
    for mid in range(pod_num):
        for src in range(pod_num):
            for dst in range(pod_num):
                if src != dst and mid != src and mid != dst:
                    res += f'{pod_num + mid},threshold,{threshold_entry_id},0,{pod_num + dst},1,{pod_num + dst}\n'
                    res += f'{pod_num + mid},match,{src},{dst},{threshold_entry_id}\n'
                    threshold_entry_id += 1

    with open(output_path + '/routing.txt', 'w') as f:
        f.write(res)


if __name__ == "__main__":
    run_netbench = False
    if len(sys.argv) > 1:
        for argument in sys.argv[1:]:
            if argument[:2] == "-r":
                run_netbench = True

    pod_conf = pd.read_csv(cm.POD_CONFIG)
    pod_num = pod_conf.shape[0]
    # r_egress = pod_conf.loc[0,'r_egress']

    coe_list = [1]
    # start_index_list = [i * 7200 for i in range(0, 10)]
    start_index_list = [7200]
    for start_index in start_index_list:
        for coe in coe_list:
            cur_name = f"index{start_index}_coe{coe}"
            test_path = f'{os.path.dirname(os.path.abspath(__file__))}/experiment/{cur_name}'
            if not os.path.isdir(test_path):
                os.makedirs(test_path)

            generate_all_files(test_path, pod_num, 'QVLBv2_ToE', 'QVLBv2_TE', coe, start_index)

            if run_netbench == False:
                print("Only generate relative files. Add -r parameter to run NetBench.")
            else:
                cm.run_simulator(cm.NETBENCH_DIRECTORY, test_path)