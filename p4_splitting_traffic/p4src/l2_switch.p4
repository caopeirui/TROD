#ifndef _L2SW_H_
#define _L2SW_H_

table l2_forward {
    reads {
        ig_intr_md.ingress_port : exact;
	ipv4.diffserv : exact;
    }
    actions {
        set_egr; nop; _drop;
    }
}

#endif

