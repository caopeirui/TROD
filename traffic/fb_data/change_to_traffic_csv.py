import traffic_snapshot_pb2
import numpy as np

def read_proto_make_traffic(file_name):
    traffic_history = traffic_snapshot_pb2.TrafficFlowTimeseries()
    with open(file_name, 'rb') as f:
        traffic_history.ParseFromString(f.read())

    res = []
    # haha = 0 
    for snapshot in traffic_history.snapshots:

        # print(snapshot.timestamp)
        # print(snapshot.time_index)
        # haha += 1
        # if haha > 2:
        #     exit()
        # continue

        flows = snapshot.flows
        node_set = set()
        for a_record in flows:
            node_set.add(a_record.src)
            node_set.add(a_record.dst)
        #   src   dst    
        #  index traffic 
        node_list = sorted(node_set)
        size = len(node_list)
        traffic = np.zeros((size, size), dtype=np.int)
        for a_record in flows:
            i = node_list.index(a_record.src)
            j = node_list.index(a_record.dst)
            if i != j: 
                traffic[i][j] = a_record.size
            #     sum_bytes  
            #  sum_bytes  
            # if i == 1 and j == 2:
            #     print(start_time, traffic[i][j])
        traffic_flatten = traffic.flatten()
        res.append(traffic_flatten)
    
    #  5 
    # new_res = []
    # for i in range(int(len(res) / 300)):
    #     new_res.append(res[i * 300])
    # res = new_res

    with open('test.csv', 'w', encoding='utf-8') as f:
        for a_traffic in res:
            a_row = ','.join([str(it) for it in a_traffic])
            f.write(a_row + '\n')

if __name__ == "__main__":
    read_proto_make_traffic('web_aggregationwindow_1.pb')