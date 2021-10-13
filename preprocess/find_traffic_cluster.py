import numpy as np
from sklearn.cluster import KMeans
from scipy.spatial import ConvexHull
from utils.base import cosine_similarity
from utils.base import DcnBase

class TrafficKMeans(DcnBase):
    """
    k-means cluster algorithm for traffic matrix.
    If model input(data matrix) is m*n dimensional matrix,
    the result of k-means cluster will be k rows, every of which represents a predicting result.
    Then every predicting traffic is reshaped to 2-dimensional matrix.
    Return results(3-dimensional) of including predicting traffic.
    """
    _k = 1

    def __init__(self, k = 1):
        self._k = k

    
    def get_rep_traffic(self):
        # return self.get_centers_traffic()
        # return self.get_convelhull_traffic()
        return self.get_cluster_max_traffic()

    def get_cluster_max_traffic(self):
        data_mat = []
        record_shape = self._traffic_sequence[0].shape
        for a_traffic in self._traffic_sequence:
            traffic_to_one_dimension = a_traffic.reshape(1, np.prod(np.shape(a_traffic)))[0]
            data_mat.append(traffic_to_one_dimension)

        km = KMeans(n_clusters = self._k, random_state=0)
        km.fit(data_mat)
        centers = km.cluster_centers_ #  
        labels = km.labels_

        size = data_mat[0].shape[0]
        rep_traffic = np.zeros((self._k, size), dtype = int)
        for i in range(len(data_mat)):
            for pos in range(size):
                rep_traffic[labels[i]][pos] = max(rep_traffic[labels[i]][pos], data_mat[i][pos])
        
        res = []
        for tm in rep_traffic:
            res.append(tm.reshape(record_shape))
        return res


    def get_convelhull_traffic(self):
        data_mat = []
        record_shape = self._traffic_sequence[0].shape
        for a_traffic in self._traffic_sequence:
            traffic_to_one_dimension = a_traffic.reshape(1, np.prod(np.shape(a_traffic)))[0]
            data_mat.append(traffic_to_one_dimension)
        # TODO: make every cluster
        # unsolvable
        hull = ConvexHull(np.array(data_mat))
        for simplex in hull.simplices:
            print(simplex)
        exit()


    def get_centers_traffic(self):
        data_mat = []
        record_shape = self._traffic_sequence[0].shape
        for a_traffic in self._traffic_sequence:
            traffic_to_one_dimension = a_traffic.reshape(1, np.prod(np.shape(a_traffic)))[0]
            data_mat.append(traffic_to_one_dimension)

        km = KMeans(n_clusters = self._k)
        km.fit(data_mat)
        centers = km.cluster_centers_ #  

        rep_traffic = []
        for center in centers:
            center_np = np.asarray(center)
            negative_values = center_np < 0.0001  # Small values could make the traffic matrix unsolvable.
            center_np[negative_values] = 0.0001 
            tmp = np.array(center_np).reshape(record_shape)
            rep_traffic.append(tmp)
        return rep_traffic

class SimilarityBasedClustering(DcnBase):

    def get_rep_traffic(self):
        data_mat = []
        record_shape = self._traffic_sequence[0].shape
        for a_traffic in self._traffic_sequence:
            traffic_to_one_dimension = a_traffic.reshape(1, np.prod(np.shape(a_traffic)))[0]
            data_mat.append(traffic_to_one_dimension)
        
        threshold = 0.95
        # We use clusters to group data vectors. Each cluster is a list, with
        # cosine similarity between any elment and the first element larger
        # than threshold.
        clusters = []
        for data in data_mat:
            # Check if there exist a cluster in clusters to join
            joined = False
            for cluster in clusters:
                if cosine_similarity(cluster[0].tolist(), data.tolist()) > threshold:
                    cluster.append(data)
                    joined = True
                    break
            if not joined:
                clusters.append([data])
        # Generate a entry-wise max data vector for each cluster
        res = []
        for cluster in clusters:
            max_data = cluster[0]
            for i in range(1, len(cluster)):
                max_data = np.maximum(max_data, cluster[i])
            res.append(max_data.reshape(record_shape))
        return res


if __name__ == "__main__":
    # ----test1
    # obj = TrafficKMeans(k = 3)

    # obj.add_a_traffic(np.random.rand(5, 4) * 100)
    # obj.add_a_traffic(np.random.rand(5, 4) * 100)
    # obj.add_a_traffic(np.random.rand(5, 4) * 100)
    # res = obj.get_rep_traffic()
    # print(res)
    # exit()
    # ----test2
    obj = SimilarityBasedClustering()
    for i in range(10):
        obj.add_a_traffic(np.array([i+1,i+1,i+1,i+1]))
    for i in range(10):
        obj.add_a_traffic(np.array([i-1,i-1,i+1,i+1]))
    res = obj.get_rep_traffic()
    print(res)
