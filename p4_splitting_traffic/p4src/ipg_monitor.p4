#ifndef _IPGM_H_
#define _IPGM_H_

header_type monitor_metadata_t {
    fields {
	ts : 32;
	prev_ts : 32;
        m : 1;
    }
}
metadata monitor_metadata_t mon_md;

action do_get_current_ts() {
    modify_field(mon_md.ts, ig_intr_md.ingress_mac_tstamp);
    //modify_field(mon_md.ts, ig_intr_md_from_parser_aux.ingress_global_tstamp);
}

table get_current_ts {
    actions {
	do_get_current_ts;
    }
    default_action : do_get_current_ts;
    size : 1;
}

action do_mark_positive() {
    modify_field(mon_md.m, 1);
}
action do_mark_negative() {
    modify_field(mon_md.m, 0);
}

table monitor_mark {
    reads {
        ig_intr_md.ingress_port : exact;
        ipv4.srcAddr : exact;	
        ipv4.dstAddr : exact;	
	udp.srcPort : exact;
	udp.dstPort : exact;
    }
    actions {
	do_mark_positive;
	do_mark_negative;
    }
    default_action : do_mark_negative;
}

register ts_record_reg {
    width : 32;
    instance_count : 1;
}

blackbox stateful_alu ts_record_alu {   
    reg : ts_record_reg;            
    update_lo_1_value : mon_md.ts;        
                                          
    output_value : register_lo;           
    output_dst : mon_md.prev_ts;          
}                                             

action do_get_previous_ts() {
    ts_record_alu.execute_stateful_alu(0);
}

table get_previous_ts {
    actions {
	do_get_previous_ts;
    }
    default_action : do_get_previous_ts;
    size : 1;
}

action do_get_ts_diff() {
    subtract_from_field(mon_md.ts, mon_md.prev_ts); 
}

table get_ts_diff {
    actions {
	do_get_ts_diff;
    }
    default_action : do_get_ts_diff;
    size : 1;
}

register ipg_mon_reg {
    width : 32;
    instance_count : 131072;
    //instance_count : 65536;
}

blackbox stateful_alu ipg_mon_alu {
    reg : ipg_mon_reg;          
    update_lo_1_value : register_lo + 1; 
}

action do_monitor_ipg() {
    ipg_mon_alu.execute_stateful_alu(mon_md.ts);
}

table monitor_ipg {
    actions {
	do_monitor_ipg;
    }
    default_action : do_monitor_ipg;
}

control ipg_monitor {
    apply(get_current_ts);
    apply(monitor_mark);
    if (mon_md.m == 1) {
        apply(get_previous_ts);
        apply(get_ts_diff);
        apply(monitor_ipg);
    }
}

#endif
