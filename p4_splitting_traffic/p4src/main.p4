#include "includes/headers.p4"
#include "includes/parser.p4"
#include "includes/actions.p4"
#include "includes/tofino.p4"

#include "l2_switch.p4"
#include "meters.p4"

control ingress {
    //apply(t_decide_midx);
    apply(meter_thold_split);
    apply(meter_color);
    apply(l2_forward);
}

control egress {
}

