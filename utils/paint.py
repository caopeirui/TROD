import matplotlib
import matplotlib.pyplot as plt
import random
import numpy as np
import pandas as pd
from root_constant import ROOT_PATH
# import seaborn as sns
# sns.set()
# sns.set_style("whitegrid")
# sns.set_palette("Paired")

COLORS = ['b', 'g', 'y', 'c', 'm', 'y', 'k', 'w', 'r']
MARKERS = ['.', ',', 'o', 'v', '^', '<', '>', '1', '2', '3', '4', '8', 's', 'p', 'P', '*', 'h', 'H', '+', 'x', 'X', 'D',
           'd', '|', '_']
LINE_SYTPES = ['-', '--', '-.', ':']
#    
random.seed(24)
# FMT = ['g-', 'r--', 'm-.', 'k:', '#0D0D0D', '#9400D3']
FMT = ['-', '--', '-.', ':']
# FMT = [random.choice(COLORS) + LINE_SYTPES[i % 4] for i in range(10)]

def draw_cdf_hist(**kwargs):
    """ hist CDF 
    Input:   key  value 
    """
    plt.figure()

    for k, data in kwargs.items():
        plt.hist(x = data, label = k, cumulative = True, normed = True, histtype = 'step')
    
    plt.legend()
    plt.title('CDF hist')
    # plt.show()
    return plt


def draw_cdf(**kwargs):
    """ CDF 
    Input:   key  value 
    """
    plt.figure()

    select = 0
    #  
    curve_num = len(kwargs.keys())
    for k, data in kwargs.items():
        x = sorted(data)
        y = []
        size = len(x)
        for i in range(size):
            y.append((i + 1) / size)
        plt.plot(x, y, FMT[select % len(FMT)], label = k)

        # delta = random.random() * 0.2
        # ran_index = int(random.random() * size)
        # plt.annotate(
        #     k, 
        #     xy = (x[ran_index], y[ran_index]),
        #     xytext = (x[ran_index] + delta, y[ran_index] + delta),
        #     arrowprops = dict(arrowstyle='->')
        # )
        select += 1

    plt.legend()
    plt.title('CDF')
    return plt


def draw_cdf_from_dict(data_dict):
    """ CDF 
    Input: key  value 
    """
    plt.figure()

    select = 0
    #  
    curve_num = len(data_dict)
    for k, data in data_dict.items():
        x = sorted(data)
        y = []
        size = len(x)
        for i in range(size):
            y.append((i + 1) / size)
        plt.plot(x, y, FMT[select % len(FMT)], label = k)

        # delta = random.random() * 0.2
        # ran_index = int(random.random() * size)
        # plt.annotate(
        #     k, 
        #     xy = (x[ran_index], y[ran_index]),
        #     xytext = (x[ran_index] + delta, y[ran_index] + delta),
        #     arrowprops = dict(arrowstyle='->')
        # )
        select += 1

    plt.legend()
    plt.title('CDF')
    return plt



def draw_line_graph(**kwargs):
    """ line 
    Input:   key  value 
    value    
    value      
    """
    plt.figure()
    select = 0
    for k, data in kwargs.items():
        data = np.array(data)
        if len(data.shape) == 1:
            plt.plot(data, LINE_SYTPES[select % 4], label = k)
        if len(data.shape) == 2:
            plt.plot(data[0], data[1], LINE_SYTPES[select % 4], label = k)
        select += 1
    plt.legend()
    plt.title('line graph')
    return plt

def draw_point_graph(data_dict):
    """ 
    Input: key  value 
    value    
    value      
    """
    plt.figure()
    select = 0
    for k, data in data_dict.items():
        data = np.array(data)
        if len(data.shape) == 1:
            plt.plot(data, label = k)
        if len(data.shape) == 2:
            plt.plot(data[0], data[1], markersize=10, alpha=0.5, label = k)
        select += 1
    plt.legend()
    plt.title('point graph')
    return plt


def my_scatter(**kwargs):
    plt.figure()
    select = 0
    for k, data in kwargs.items():
        data = np.array(data)
        plt.scatter(data[0], data[1], s = 120, marker = MARKERS[select % len(MARKERS)], alpha = 1, label = k)
        select += 1
    plt.legend()
    plt.title('scatter graph')
    return plt


if __name__ == "__main__":
    # df = pd.read_csv(f'{ROOT_PATH}/trigger_netbench/experiment/cpr.csv')
    # df = pd.read_csv(f'~/Downloads/tmp_exp/workload0.6.csv')
    # df = pd.read_csv(f'~/Downloads/tmp_exp/et282workload0.6.csv')
    df = pd.read_csv(f'~/Downloads/tmp_exp/et1000.csv')
    # df = pd.read_csv(f'~/Downloads/tmp_exp/workload0.6allcapacity.csv')

    data_dict = {
        'QVLB': df['QVLB'],
        'ideal': df['ideal'],
        'RWCMP': df['RWCMP']
    }

    plt = draw_cdf_from_dict(data_dict)
    plt.show()
