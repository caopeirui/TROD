#ifndef _MT_H_
#define _MT_H_



meter thold_split {
    type : bytes;
    static : meter_thold_split;
    result : ipv4.diffserv;
    pre_color : ipv4.diffserv;
    instance_count : 500;
}

action meter_action(idx) {
    execute_meter(thold_split, idx, ipv4.diffserv, ipv4.diffserv);
    //execute_meter(thold_split, idx, ipv4.diffserv);
}

table meter_thold_split {
    reads {
        ig_intr_md.ingress_port : exact;
    }
    actions {
        nop;
        meter_action;
    }
}

register meter_color_reg {
    width : 32;
    instance_count : 65536;
}

blackbox stateful_alu meter_color_alu {
    reg : meter_color_reg;
    update_lo_1_value : register_lo + 1;
}

action do_meter_color() {
    meter_color_alu.execute_stateful_alu(ipv4.diffserv);
}

table meter_color {
    actions {
	do_meter_color;
    }
    default_action : do_meter_color;
}


#endif
