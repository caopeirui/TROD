"""
simple l2 switch
"""

import pd_base_tests

from ptf import config
from ptf.testutils import *
from ptf.thriftutils import *

from thold_split.p4_pd_rpc.ttypes import *
from res_pd_rpc.ttypes import *
from mirror_pd_rpc.ttypes import *
from conn_mgr_pd_rpc.ttypes import *
from mc_pd_rpc.ttypes import *
from devport_mgr_pd_rpc.ttypes import *
from ptf_port import *
from pal_rpc.ttypes import *

from tm_api_rpc import *

class DzTest(pd_base_tests.ThriftInterfaceDataPlane):
    def __init__(self):
        pd_base_tests.ThriftInterfaceDataPlane.__init__(self,
                                                        ["thold_split"])
    def setUp(self):
        pd_base_tests.ThriftInterfaceDataPlane.setUp(self)

        self.sess_hdl = self.conn_mgr.client_init()
        self.dev      = 0
        self.dev_tgt  = DevTarget_t(self.dev, hex_to_i16(0xFFFF))

        self.entries={}
        self.entries["forward"] = []

        print("\nConnected to Device %d, Session %d" % (
            self.dev, self.sess_hdl))

    def setup_100g_loopback_port(self, port,
                    an = None, fec = pal_fec_type_t.BF_FEC_TYP_NONE):
        if isinstance(port, int) == False:
            port = g_port_dict[port]
        #self.setup_port(port, pal_port_speed_t.BF_SPEED_100G, an, fec)
        self.setup_port(port, pal_port_speed_t.BF_SPEED_10G, 2, fec)
        #self.pal_client.pal_port_loopback_mode_set(self.dev, port, pal_loopback_mod_t.BF_LPBK_MAC_NEAR)
        self.pal.pal_port_loopback_mode_set(self.dev, port, pal_loopback_mod_t.BF_LPBK_MAC_NEAR)

    def setup_port(self, port, speed,
                    an = None, fec = pal_fec_type_t.BF_FEC_TYP_NONE):
        if isinstance(port, int) == False:
            port = g_port_dict[port]
        self.pal.pal_port_add(self.dev, port, speed, fec)
        if an is not None:
            self.pal.pal_port_an_set(self.dev, port, an)
        else:
            self.pal.pal_port_an_set(self.dev, port, 2)
        self.pal.pal_port_enable(self.dev, port)

    def add_forward_entry(self, ingress_port, ipv4ds, egress_port):
        self.entries["forward"].append(
            self.client.l2_forward_table_add_with_set_egr(
                self.sess_hdl, self.dev_tgt,
		thold_split_l2_forward_match_spec_t(
		    ig_intr_md_ingress_port = ingress_port,
		    ipv4_diffserv = ipv4ds),
                thold_split_set_egr_action_spec_t(
                    action_egress_spec=egress_port)))

        print("Table forward: %s => set_egr(%d)" %
              (ingress_port, egress_port))
        self.conn_mgr.complete_operations(self.sess_hdl)


    def runTest(self):
	self.setup_100g_loopback_port(52)
	self.setup_100g_loopback_port(44)
	self.setup_100g_loopback_port(36)
	self.setup_100g_loopback_port(28)

	self.add_forward_entry(128, 0, 52)
	self.add_forward_entry(128, 1, 44)
	self.add_forward_entry(128, 2, 36)
	self.add_forward_entry(128, 3, 28)

	self.add_forward_entry(160, 0, 52)
	self.add_forward_entry(160, 1, 44)
	self.add_forward_entry(160, 2, 36)
	self.add_forward_entry(160, 3, 28)

  	#ppg_id = self.tm.tm_get_default_ppg(self.dev, 140)
	#self.tm.tm_set_app_pool_size(self.dev, 1, 0)
	#self.tm.tm_set_app_pool_size(self.dev, 2, 0)
	#self.tm.tm_set_app_pool_size(self.dev, 3, 0)
	##self.tm.tm_set_app_pool_size(self.dev, BF_TM_EG_APP_POOL_2, 0)

  	##self.tm.tm_set_ppg_guaranteed_min_limit(self.dev, ppg_id, 250)

    def tearDown(self):
	pass
        #try:
        #    print("Clearing table entries")
        #    for table in self.entries.keys():
        #        delete_func = "self.client." + table + "_table_delete"
        #        for entry in self.entries[table]:
        #            exec delete_func + "(self.sess_hdl, self.dev, entry)"
        #except:
        #    print("Error while cleaning up. ")
        #    print("You might need to restart the driver")
        #finally:
        #    self.conn_mgr.complete_operations(self.sess_hdl)
        #    self.conn_mgr.client_cleanup(self.sess_hdl)
        #    print("Closed Session %d" % self.sess_hdl)
        #    pd_base_tests.ThriftInterfaceDataPlane.tearDown(self)
