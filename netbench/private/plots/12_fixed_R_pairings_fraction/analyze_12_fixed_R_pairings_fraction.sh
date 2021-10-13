#!/usr/bin/env bash

# A2A INCREASING FRACTION

FOLDER_IN_RESULTS=12_fixed_R_pairings_fraction
LABEL=12_fixed_R_pairings_fraction

# Combine results
python ../../../analysis/multi_combine.py all_mean_fct_ms flow_completion.statistics ../../../temp/results/${FOLDER_IN_RESULTS} actservers actservers > data_${LABEL}_mean_fct_ms.txt
python ../../../analysis/multi_combine.py less_100KB_99th_fct_ms flow_completion.statistics ../../../temp/results/${FOLDER_IN_RESULTS} actservers actservers > data_${LABEL}_less_100KB_99th_fct_ms.txt
python ../../../analysis/multi_combine.py geq_100KB_throughput_mean_Gbps flow_completion.statistics ../../../temp/results/${FOLDER_IN_RESULTS} actservers actservers > data_${LABEL}_geq_100KB_throughput_mean_Gbps.txt

# Plot
gnuplot plot_12a_fixed_R_pairings_fraction_avg_fct.plt
gnuplot plot_12b_fixed_R_pairings_fraction_99th_fct_short.plt
gnuplot plot_12c_fixed_R_pairings_fraction_throughput_large.plt
