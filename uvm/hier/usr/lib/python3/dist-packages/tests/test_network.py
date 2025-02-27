import glob
import socket
import unittest
import pytest
import os
import sys
import re
import subprocess
import pprint
import time
import copy
import runtests
import json

from .global_functions import uvmContext
from tests.common import NGFWTestCase
import runtests.test_registry as test_registry
import runtests.remote_control as remote_control
import runtests.overrides as overrides
from . import global_functions
from . import ipaddr

import tests.test_ipsec_vpn as test_ipsec_vpn

DHCP_RELAY_ADDRESS=overrides.get("DHCP_RELAY_ADDRESS",default=test_ipsec_vpn.IPSEC_HOST_LAN_IP)

ftp_file_name = ""

default_policy_id = 1
orig_netsettings = None
test_untangle_com_ip = socket.gethostbyname(global_functions.TEST_SERVER_HOST)
run_ftp_inbound_tests = None
wan_ip = None
device_in_office = False
dyndns_resolver = "8.8.8.8"
#office_ftp_client = "10.112.56.23"
office_ftp_client = overrides.get('office_ftp_client')
if office_ftp_client is None:
    ftp_server = "10.112.56.23"
#dyndns_resolver = "resolver1.dyndnsinternetguide.com"

def get_usable_name(dyn_checkip):
    selected_name = ""
    names,filler = global_functions.get_live_account_info("dyndns")
    dyn_names = names.split(",") 
    for hostname in dyn_names:
        hostname_ip = global_functions.get_hostname_ip_address(hostname=hostname)
        if dyn_checkip != hostname_ip:
            selected_name = hostname
            break
    return selected_name
    
def create_port_forward_triple_condition( conditionType1, value1, conditionType2, value2, conditionType3, value3, destinationIP, destinationPort):
    return {
        "description": "port forward  -> " + str(destinationIP) + ":" + str(destinationPort) + " test",
        "enabled": True,
        "javaClass": "com.untangle.uvm.network.PortForwardRule",
        "conditions": {
            "javaClass": "java.util.LinkedList",
            "list": [
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.PortForwardRuleCondition",
                    "conditionType": str(conditionType1),
                    "value": str(value1)
                },
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.PortForwardRuleCondition",
                    "conditionType": str(conditionType2),
                    "value": str(value2)
                },
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.PortForwardRuleCondition",
                    "conditionType": str(conditionType3),
                    "value": str(value3)
                }
            ]
        },
        "newDestination": destinationIP,
        "newPort": destinationPort,
        "ruleId": 1
    }

def create_filter_rules( conditionType1, value1, conditionType2, value2, blocked ):
    return {
        "bypass": True,
        "description": "test rule " + str(conditionType1) + " " + str(value1) + " " + str(conditionType2) + " " + str(value2),
        "enabled": True,
        "blocked": blocked,
        "javaClass": "com.untangle.uvm.network.FilterRule",
        "conditions": {
            "javaClass": "java.util.LinkedList",
            "list": [
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.FilterRuleCondition",
                    "conditionType": str(conditionType1),
                    "value": str(value1)
                },
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.FilterRuleCondition",
                    "conditionType": str(conditionType2),
                    "value": str(value2)
                }
            ]
        },
        "ruleId": 1
    }

def create_bypass_condition_rule( conditionType, value ):
    return {
        "bypass": True,
        "description": "test bypass " + str(conditionType) + " " + str(value),
        "enabled": True,
        "javaClass": "com.untangle.uvm.network.BypassRule",
        "conditions": {
            "javaClass": "java.util.LinkedList",
            "list": [
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.BypassRuleCondition",
                    "conditionType": str(conditionType),
                    "value": str(value)
                },
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.BypassRuleCondition",
                    "conditionType": "PROTOCOL",
                    "value": "TCP,UDP"
                }
            ]
        },
        "ruleId": 1
    }

def create_qos_condition_rule( conditionType, value, priority):
    return {
        "description": "test QoS " + str(conditionType) + " " + str(value),
        "enabled": True,
        "javaClass": "com.untangle.uvm.network.QosRule",
        "conditions": {
            "javaClass": "java.util.LinkedList",
            "list": [
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.QosRuleCondition",
                    "conditionType": str(conditionType),
                    "value": str(value)
                },
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.QosRuleCondition",
                    "conditionType": "PROTOCOL",
                    "value": "TCP,UDP"
                }
            ]
        },
        "priority": priority,
        "ruleId": 3
    }

def create_single_condition_firewall_rule( conditionType, value, blocked=True, flagged=True ):
    return {
        "javaClass": "com.untangle.app.firewall.FirewallRule",
        "id": 1,
        "enabled": True,
        "description": "Single Condition: " + str(conditionType) + " = " + str(value),
        "flag": flagged,
        "block": blocked,
        "conditions": {
            "javaClass": "java.util.LinkedList",
            "list": [
                {
                    "invert": False,
                    "javaClass": "com.untangle.app.firewall.FirewallRuleCondition",
                    "conditionType": str(conditionType),
                    "value": str(value)
                    }
                ]
            }
        }

def create_route_rule( networkAddr, netmask, gateway):
    return {
        "description": "test route",
        "javaClass": "com.untangle.uvm.network.StaticRoute",
        "network": networkAddr,
        "nextHop": gateway,
        "prefix": netmask,
        "ruleId": 1,
        "toAddr": True,
        "toDev": False
        }

def create_nat_rule( name, conditionType, value, source):
    return {
        "auto": False,
        "description": name,
        "enabled": True,
        "javaClass": "com.untangle.uvm.network.NatRule",
        "conditions": {
            "javaClass": "java.util.LinkedList",
            "list": [
                {
                    "invert": False,
                    "javaClass": "com.untangle.uvm.network.NatRuleCondition",
                    "conditionType": str(conditionType),
                    "value": value
                }
            ]
        },
        "newSource": source,
        "ruleId": 1
    }

def create_dns_rule( networkAddr, name):
    return {
        "address": networkAddr,
        "javaClass": "com.untangle.uvm.network.DnsStaticEntry",
        "name": name
         }

def create_vlan_interface(interfaceId, name, physicalInterface, symInterface, sysInterface, ipV4address):
    return {
            "addressed": True,
            "bridged": False,
            "configType": "ADDRESSED",
            "dhcpEnabled": False,
            "dhcpOptions": {
                "javaClass": "java.util.LinkedList",
                "list": []
            },
            "disabled": False,
            "interfaceId": interfaceId,
            "isVlanInterface": True,
            "isWan": False,
            "javaClass": "com.untangle.uvm.network.InterfaceSettings",
            "name": name,
            "physicalDev": physicalInterface, #"eth1",
            "raEnabled": False,
            "symbolicDev": symInterface, #"eth1.1",
            "systemDev": sysInterface, #"eth1.1",
            "v4Aliases": {
                "javaClass": "java.util.LinkedList",
                "list": []
            },
            "v4ConfigType": "STATIC",
            "v4NatEgressTraffic": False,
            "v4NatIngressTraffic": False,
            "v4PPPoEPassword": "",
            "v4PPPoEUsePeerDns": False,
            "v4PPPoEUsername": "",
            "v4StaticAddress": ipV4address, #"192.168.14.1",
            "v4StaticNetmask": "255.255.255.0",
            "v4StaticPrefix": 24,
            "v6Aliases": {
                "javaClass": "java.util.LinkedList",
                "list": []
            },
            "v6ConfigType": "STATIC",
            "vlanParent": 2,
            "vlanTag": 1,
            "vrrpAliases": {
                "javaClass": "java.util.LinkedList",
                "list": []
            },
            "vrrpEnabled": False
        }

def create_alias(ipAddress,ipNetmask,ipPrefix):
    return {
            "javaClass": "com.untangle.uvm.network.InterfaceSettings$InterfaceAlias",
            "staticAddress": ipAddress,
            "staticNetmask": ipNetmask,
            "staticPrefix": ipPrefix
        }


def get_http_https_ports():
    netsettings = uvmContext.networkManager().getNetworkSettings()
    return (netsettings['httpPort'], netsettings['httpsPort'])

def set_htp_https_ports(httpPort, httpsPort):
    netsettings = uvmContext.networkManager().getNetworkSettings()
    netsettings['httpPort'] = httpPort
    netsettings['httpsPort'] = httpsPort
    uvmContext.networkManager().setNetworkSettings(netsettings)

def set_first_level_rule(newRule,ruleGroup):
    netsettings = uvmContext.networkManager().getNetworkSettings()
    netsettings[ruleGroup]['list'].insert(0,newRule)
    uvmContext.networkManager().setNetworkSettings(netsettings)

def append_firewall_rule(app, newRule):
    rules = app.getRules()
    rules["list"].append(newRule)
    app.setRules(rules)

def add_dns_rule(newRule):
    netsettings = uvmContext.networkManager().getNetworkSettings()
    netsettings['dnsSettings']['staticEntries']['list'].insert(0,newRule)
    uvmContext.networkManager().setNetworkSettings(netsettings)

def find_used_ip(start_ip):
    # Find an IP that is not currently used.
    loopLimit = 20
    test_ip = ipaddr.IPAddress(start_ip)
    ip_used = True
    while (ip_used and (loopLimit > 0)):
        loopLimit -= 1
        test_ip += 1
        test_ip_result = subprocess.call(["ping","-c","1",str(test_ip)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        if test_ip_result != 0:
            ip_used = False

    if ip_used:
        # no unused IP found
        return False
    else:
        return str(test_ip)

def append_vlan(parentInterfaceID):
    netsettings = uvmContext.networkManager().getNetworkSettings()
    # find the physicalDev of the interface passed in.
    physicalDev = None
    for interface in netsettings['interfaces']['list']:
        if interface['interfaceId'] == parentInterfaceID:
            if interface['configType'] != "ADDRESSED":
                # only use if interface is addressed
                return False
            physicalDev = interface['physicalDev']
            break

    testVLANIP = find_used_ip("1.2.3.4")
    if testVLANIP:
        # no unused IP found
        return False

    # Check thast VLAN ID is not used
    loopLimit = 20
    testVLANID = 100
    vlanIdUsed = True
    while (vlanIdUsed and (loopLimit > 0)):
        testVLANID += 1
        loopLimit -= 1
        vlanIdUsed = False
        testVlanIdDev = physicalDev + "." + str(testVLANID)
        for interface in netsettings['interfaces']['list']:
            if interface['symbolicDev'] == testVlanIdDev:
                # found duplicate VLAN ID
                vlanIdUsed = True
                break

    if vlanIdUsed:
        # no unused VLAN ID found
        return False

    # if valid VLAN interface and IP is available, create a VLAN
    netsettings['interfaces']['list'].append(create_vlan_interface(physicalDev,"network_tests",testVlanIdDev,testVlanIdDev,str(testVLANIP)))
    uvmContext.networkManager().setNetworkSettings(netsettings)
    return testVLANIP

def append_aliases():
    ip_found = False
    netsettings = copy.deepcopy(orig_netsettings)
    for i in range(len(netsettings['interfaces']['list'])):
        if netsettings['interfaces']['list'][i]['configType'] == "ADDRESSED":
            if netsettings['interfaces']['list'][i]['v4ConfigType'] == "STATIC":
                test_start_ip =  netsettings['interfaces']['list'][i]['v4StaticAddress']
                ip_found = find_used_ip(test_start_ip)
                break;
            elif netsettings['interfaces']['list'][i]['v4ConfigType'] == "AUTO":
                nicDevice = str(netsettings['interfaces']['list'][i]['symbolicDev'])
                test_start_ip = global_functions.__get_ip_address(nicDevice)
                ip_found = find_used_ip(test_start_ip)
                break;

    if ip_found:
        netsettings['interfaces']['list'][i]['v4Aliases']['list'].append(create_alias(ip_found,
                                                                         netsettings['interfaces']['list'][i]['v4StaticNetmask'],
                                                                         netsettings['interfaces']['list'][i]['v4StaticPrefix']))
        uvmContext.networkManager().setNetworkSettings(netsettings)
    print(("Alias IP: " + ip_found))
    return ip_found

def nuke_first_level_rule(ruleGroup):
    netsettings = uvmContext.networkManager().getNetworkSettings()
    netsettings[ruleGroup]['list'][:] = []
    uvmContext.networkManager().setNetworkSettings(netsettings)

def nuke_dns_rules():
    netsettings = uvmContext.networkManager().getNetworkSettings()
    netsettings['dnsSettings']['staticEntries']['list'][:] = []
    uvmContext.networkManager().setNetworkSettings(netsettings)

def set_dyn_dns(login,password,hostname):
    netsettings = uvmContext.networkManager().getNetworkSettings()
    netsettings['dynamicDnsServiceEnabled'] = True
    netsettings['dynamicDnsServiceHostnames'] = hostname
    netsettings['dynamicDnsServiceName'] = "google"
    netsettings['dynamicDnsServiceUsername'] = login
    netsettings['dynamicDnsServicePassword'] = password
    uvmContext.networkManager().setNetworkSettings(netsettings)

def verify_snmp_walk():
    snmpwalkResult = remote_control.run_command("test -x /usr/bin/snmpwalk")
    if snmpwalkResult:
        raise unittest.SkipTest("Snmpwalk app needs to be installed on client")

def set_snmp_v3_settings( settings, v3Enabled, v3Username, v3AuthenticationProtocol, v3AuthenticationPassphrase, v3PrivacyProtocol, v3PrivacyPassphrase, v3Required ):
    settings['v3Enabled'] = v3Enabled
    settings['v3Username'] = v3Username
    settings['v3AuthenticationProtocol'] = v3AuthenticationProtocol
    settings['v3AuthenticationPassphrase'] = v3AuthenticationPassphrase
    settings['v3PrivacyProtocol'] = v3PrivacyProtocol
    settings['v3PrivacyPassphrase'] = v3PrivacyPassphrase
    settings['v3Required'] = v3Required

    v1v2command = "snmpwalk -v 2c -c atstest " +  global_functions.get_lan_ip() + " | grep untangle"
    v3command = "snmpwalk -v 3 " + " -u " + v3Username + " -l authNoPriv " + " -a " + v3AuthenticationProtocol + " -A " + v3AuthenticationPassphrase + " -x " + v3PrivacyProtocol
    if v3PrivacyPassphrase != "":
        v3command += " -X " + v3PrivacyPassphrase
    v3command += " " +  global_functions.get_lan_ip() + " | grep untangle"

    print(("v1v2command = " + v1v2command))
    return( v1v2command, v3command )

def try_snmp_command(command):
    result = remote_control.run_command( command )
    if (result == 1):
        # there might be a delay in snmp restarting
        time.sleep(5)
        result = remote_control.run_command( command )
    return result

def build_device_to_mac_address_map(devices=[]):
    """
    For the list of devices (e.g.,eth0, eth1)
    return a dictionary mapping of those names to the current system mac addresses
    """
    device_mac_mapping = {}
    for device_name in devices:
        mac_address_filename=f"/sys/class/net/{device_name}/address"
        if not os.path.isfile(mac_address_filename):
            # Mac address doesn't exit
            raise unittest.SkipTest(f"cannot find {mac_address_filename} for {device_name} ")
        with open(mac_address_filename) as file:
            device_mac_mapping[device_name] = file.readline().strip()

    return device_mac_mapping

@pytest.mark.network
class NetworkTests(NGFWTestCase):

    not_an_app = True

    @staticmethod
    def module_name():
        return "network"

    @classmethod
    def initial_extra_setup(cls):
        global orig_netsettings, run_ftp_inbound_tests, wan_ip, device_in_office
        if orig_netsettings == None:
            orig_netsettings = uvmContext.networkManager().getNetworkSettings()
        wan_ip = uvmContext.networkManager().getFirstWanAddress()
        print(wan_ip)
        device_in_office = global_functions.is_in_office_network(wan_ip)
        cls.ftpUserName, cls.ftpPassword = global_functions.get_live_account_info("ftp")
        
        if run_ftp_inbound_tests == None:
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.connect( ( remote_control.client_ip, 21 ))
                s.close()
                pingResult = subprocess.call(["ping","-c","1",global_functions.ftp_server],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
                if pingResult == 0:
                    run_ftp_inbound_tests = True
                else:
                    run_ftp_inbound_tests = False
            except:
                print(("Socket test failed to %s" % remote_control.client_ip))
                run_ftp_inbound_tests = False

    def test_010_client_is_online(self):
        result = remote_control.is_online()
        assert (result == 0)
        
    def test_014_add_vlan(self):
        raise unittest.SkipTest("Review changes in test")
        # Add a test static VLAN
        test_vlan_ip = append_vlan(remote_control.interface)
        if test_vlan_ip:
            result = subprocess.call(["ping","-c","1",str(test_vlan_ip)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            uvmContext.networkManager().setNetworkSettings(orig_netsettings)
            assert(result == 0)
        else:
            # no VLAN was created so skip test
            unittest.SkipTest("No VLAN or IP address available")
            
    def test_015_add_many_vlans(self):
        network_manager = uvmContext.networkManager()
        physicalDev = ""
        assigned_intfs = []
        for interface in network_manager.getNetworkSettings()['interfaces']['list']:
            assigned_intfs += [interface['interfaceId']]
            if interface['interfaceId'] == remote_control.interface and interface['configType'] in ['ADDRESSED','BRIDGED']:
                physicalDev = interface['physicalDev']
        for interface in network_manager.getNetworkSettings()['virtualInterfaces']['list']:
            assigned_intfs += [interface['interfaceId']]
        if not physicalDev:
            unittest.SkipTest("No physical device available")

        max_interfaces = 253
        netspace_manager = uvmContext.netspaceManager()
        new_ip = netspace_manager.getAvailableAddressSpace("IPv4", 1).split("/")[0]
        new_netsettings = uvmContext.networkManager().getNetworkSettings()
        for id in range(1, max_interfaces):
            # only adding if not used already
            if id not in assigned_intfs:
                testVlanIdDev = physicalDev + "." + str(id)
                # assigning negative numbers to all interfaces we add, like the ui does
                vlan_intf = create_vlan_interface(id * -1, "network_tests_" + str(id), physicalDev, testVlanIdDev, testVlanIdDev, new_ip)
                new_netsettings['interfaces']['list'].append(vlan_intf)
                new_ip = netspace_manager.getAvailableAddressSpace("IPv4", 1).split("/")[0]
        
        network_manager.setNetworkSettings(new_netsettings)
        num_interfaces  = len(network_manager.getNetworkSettings()['interfaces']       ['list'])
        num_interfaces += len(network_manager.getNetworkSettings()['virtualInterfaces']['list'])
        print(num_interfaces)
        assert(num_interfaces == max_interfaces)
        freeId = network_manager.getNextFreeInterfaceId(network_manager.getNetworkSettings())
        print(freeId)
        assert(freeId == -1)
        
        # Re-setting network settings for other tests
        network_manager.setNetworkSettings(orig_netsettings)

    def test_016_add_alias(self):
        # raise unittest.SkipTest("Review changes in test")
        # Add Alias IP
        alias_ip = append_aliases()
        if alias_ip:
            # print("alias_ip <%s>" % AliasIP)
            result = remote_control.run_command("ping -c 1 %s" % alias_ip)
            uvmContext.networkManager().setNetworkSettings(orig_netsettings)
            result2 = remote_control.run_command("ping -c 1 %s" % alias_ip)
            assert (result == 0)
            assert (result2 != 0)
        else:
            # No alias IP added so just skip
            unittest.SkipTest("No alias address available")

    # test basic port forward (tcp port 80)
    def test_020_port_forward_80(self):
        # networking might not be ready specially on boxes with DHCP WAN
        timeout = 30
        ping_result = 1
        while (timeout > 0 and ping_result != 0):
            timeout -= 1
            print("Try " + str(timeout))
            ping_result = remote_control.run_command("ping -c 1 %s" % test_untangle_com_ip)
        result = remote_control.run_command("ping -c 1 %s" % test_untangle_com_ip)
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","80","DST_ADDR","1.2.3.4","PROTOCOL","TCP",test_untangle_com_ip,80),'portForwardRules')
        result = remote_control.run_command(global_functions.build_wget_command(output_file="-", uri="http://1.2.3.4/test/testPage1.html") + " 2>&1 | grep -q text123")
        assert(result == 0)

        events = global_functions.get_events('Network','Port Forwarded Sessions',None,5)
        assert(events != None)
        found = global_functions.check_events( events.get('list'), 5,
                                               "s_server_addr", test_untangle_com_ip,
                                               "c_client_addr", remote_control.client_ip,
                                               "local_addr", remote_control.client_ip,
                                               "remote_addr", test_untangle_com_ip,
                                               "s_server_port", 80)
        assert(found)

    # test basic port forward (tcp port 443)
    def test_021_port_forward_443(self):
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","443","DST_ADDR","1.2.3.4","PROTOCOL","TCP",test_untangle_com_ip,443),'portForwardRules')
        result = remote_control.run_command(global_functions.build_wget_command(output_file="-", uri="https://1.2.3.4/test/testPage1.html") + " 2>&1 | grep -q text123")
        assert(result == 0)

    # test port forward (changing the port 80 -> 81)
    def test_022_port_forward_new_port(self):
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","81","DST_ADDR","1.2.3.4","PROTOCOL","TCP",test_untangle_com_ip,80),'portForwardRules')
        result = remote_control.run_command(global_functions.build_wget_command(output_file="-", uri="http://1.2.3.4/test/testPage1.html") + " 2>&1 | grep -q text123")
        assert(result == 0)

    # test port forward using DST_LOCAL condition
    def test_023_port_forward_dst_local(self):
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","81","DST_LOCAL","true","PROTOCOL","TCP",test_untangle_com_ip,80),'portForwardRules')
        wan_address = uvmContext.networkManager().getFirstWanAddress()
        result = remote_control.run_command(global_functions.build_wget_command(output_file="-", uri=f"http://{wan_address}:81/test/testPage1.html") + " 2>&1 | grep -q text123")
        assert(result == 0)

    # test port forward that uses the http port (move http to different port)
    def test_024_port_forward_port_80_local_http_port(self):
        orig_ports = get_http_https_ports()
        set_htp_https_ports( 8080, 4343 )
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","80","DST_LOCAL","true","PROTOCOL","TCP",test_untangle_com_ip,80),'portForwardRules')
        wan_address = uvmContext.networkManager().getFirstWanAddress()
        result = remote_control.run_command(global_functions.build_wget_command(output_file="-", uri=f"http://{wan_address}/test/testPage1.html") + " 2>&1 | grep -q text123")
        set_htp_https_ports( orig_ports[0], orig_ports[1])
        assert(result == 0)

    # test port forward that uses the https port (move https to different port)
    def test_025_port_forward_port_443_local_https_port(self):
        orig_ports = get_http_https_ports()
        set_htp_https_ports( 8080, 4343 )
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","443","DST_LOCAL","true","PROTOCOL","TCP",test_untangle_com_ip,443),'portForwardRules')
        wan_address = uvmContext.networkManager().getFirstWanAddress()
        result = remote_control.run_command(global_functions.build_wget_command(output_file="-", uri=f"https://{wan_address}/test/testPage1.html") + " 2>&1 | grep -q text123")
        set_htp_https_ports( orig_ports[0], orig_ports[1])
        assert(result == 0)

    # test hairpin port forward (back to original client)
    def test_026_port_forward_hairpin(self):
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","11234","DST_LOCAL","true","PROTOCOL","TCP",remote_control.client_ip,11234),'portForwardRules')
        remote_control.run_command("nohup netcat -l -p 11234 >/dev/null 2>&1",stdout=False,nowait=True)
        result = remote_control.run_command("echo test | netcat -q0 %s 11234" % uvmContext.networkManager().getFirstWanAddress())
        print(("result: %s" % str(result)))
        assert(result == 0)

    # test port forward to multiple ports (tcp port 80,443)
    def test_027_port_forward_multiport(self):
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","80,443","DST_ADDR","1.2.3.4","PROTOCOL","TCP",test_untangle_com_ip,None),'portForwardRules')
        result = remote_control.run_command(global_functions.build_wget_command(output_file="-", uri=f"http://1.2.3.4/test/testPage1.html") + " 2>&1 | grep -q text123")
        assert(result == 0)
        result = remote_control.run_command(global_functions.build_wget_command(output_file="-", uri=f"https://1.2.3.4/test/testPage1.html") + " 2>&1 | grep -q text123")
        assert(result == 0)

    # test a port forward from outside if possible
    def test_030_port_forward_inbound(self):
        # We will use iperf_server for this test. Test to see if we can reach it.
        iperf_avail = global_functions.verify_iperf_configuration(wan_ip)
        if (not iperf_avail):
            raise unittest.SkipTest("IperfServer test client unreachable, skipping alternate port forwarding test")
        # Also test that it can probably reach us (we're on a 10.x network)
        if not device_in_office:
            raise unittest.SkipTest("Not on office network, skipping")

        # start netcat on client
        remote_control.run_command("nohup netcat -l -p 11245 >/dev/null 2>&1",stdout=False,nowait=True)

        # port forward 11245 to client box
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","11245","DST_LOCAL","true","PROTOCOL","TCP",remote_control.client_ip,"11245"),'portForwardRules')

        # try connecting to netcat on client from "outside" box
        result = remote_control.run_command("echo test | netcat -q0 " + wan_ip + " 11245", host=global_functions.iperf_server)
        assert (result == 0)

    # test a port forward from outside if possible
    def test_040_port_forward_udp_inbound(self):
        # We will use iperf server and iperf for this test.
        # Also test that it can probably reach us (we're on a 10.x network)
        if not device_in_office:
            raise unittest.SkipTest("Not on office network, skipping")
        iperfAvail = global_functions.verify_iperf_configuration(wan_ip)
        if (not iperfAvail):
            raise unittest.SkipTest("iperf_server " + global_functions.iperf_server + " is unreachable, skipping")
        # Only if iperf is used
        # if not iperfResult:
        #     raise unittest.SkipTest("Iperf server not reachable")

        # port forward UDP 5000 to client box
        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","5000","DST_LOCAL","true","PROTOCOL","UDP",remote_control.client_ip,"5000"),'portForwardRules')

        # start netcat on client
        remote_control.run_command("rm -f /tmp/netcat.udp.recv.txt")
        remote_control.run_command("nohup netcat -l -u -p 5000 >/tmp/netcat.udp.recv.txt",stdout=False,nowait=True)

        remote_control.run_command("echo test| netcat -q0 -w1 -u " + wan_ip + " 5000",host=global_functions.iperf_server)

        result = remote_control.run_command("grep test /tmp/netcat.udp.recv.txt")

        # send UDP packets through the port forward
        # UDP_speed = global_functions.get_udp_download_speed( receiverIP=remote_control.client_ip, senderIP=global_functions.iperf_server, targetIP=wan_ip )
        # assert (UDP_speed >  0.0)

        uvmContext.networkManager().setNetworkSettings(orig_netsettings)
        assert ( result == 0 )

    # test a NAT rules
    def test_050_nat_rule(self):
        # check if more than one WAN
        index_of_wans = global_functions.get_wan_tuples()
        if (len(index_of_wans) < 2):
            raise unittest.SkipTest("Need at least two public static WANS for test_050_natRule")

        for wan in index_of_wans:
            nuke_first_level_rule("natRules")
            # Create NAT rule for port 80
            set_first_level_rule(create_nat_rule("test out " + wan[1], "DST_PORT","80",wan[1]),"natRules")
            # Determine current outgoing IP
            result = global_functions.get_public_ip_address()
            assert (result == wan[2])

        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

    # Test that bypass rules bypass apps
    def test_060_bypass_rules(self):
        app_fw = None
        if (uvmContext.appManager().isInstantiated("firewall")):
            if cls.skip_instantiated():
                pytest.skip('app %s already instantiated' % cls.module_name())
        app_fw = uvmContext.appManager().instantiate("firewall", default_policy_id)
        nuke_first_level_rule('bypassRules')
        # verify port 80 is open
        result1 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri=f"http://test.untangle.com"))
        # Block port 80 and verify it's closed
        append_firewall_rule(app_fw, create_single_condition_firewall_rule("DST_PORT","80"))
        result2 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri=f"http://test.untangle.com/"))

        # add bypass rule for the client and enable bypass logging
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['bypassRules']['list'].append( create_bypass_condition_rule("SRC_ADDR",remote_control.client_ip) )
        netsettings['logBypassedSessions'] = True
        uvmContext.networkManager().setNetworkSettings(netsettings)

        # verify the client can still get out (and that the traffic is bypassed)
        result3 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri=f"http://test.untangle.com/"))

        events = global_functions.get_events('Network','Bypassed Sessions',None,100)

        uvmContext.appManager().destroy( app_fw.getAppSettings()["id"] )
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)
        assert (result1 == 0)
        assert (result2 != 0)
        assert (result3 == 0)

        assert(events != None)
        found = global_functions.check_events( events.get('list'), 100,
                                               "s_server_addr", test_untangle_com_ip,
                                               "c_client_addr", remote_control.client_ip,
                                               "local_addr", remote_control.client_ip,
                                               "remote_addr", test_untangle_com_ip,
                                               "s_server_port", 80)
        assert(found)

    # Test FTP (outbound) in active and passive modes
    @pytest.mark.failure_in_podman
    def test_070_ftp_modes(self):
        nuke_first_level_rule('bypassRules')

        pasv_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        port_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", extra_arguments="--no-passive-ftp", user=self.ftpUserName, password=self.ftpPassword, uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        epsv_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, extra_arguments="--epsv", uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        eprt_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, extra_arguments="--eprt -P -", uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        print(("port_result: %i eprt_result: %i pasv_result: %i epsv_result: %i" % (port_result,eprt_result,pasv_result,epsv_result)))
        assert (pasv_result == 0)
        assert (port_result == 0)
        assert (epsv_result == 0)
        assert (eprt_result == 0)

    # Test FTP (outbound) in active and passive modes with a firewall block all rule (firewall should pass related sessions without special rules)
    @pytest.mark.failure_in_podman
    def test_071_ftp_modes_firewalled(self):
        app_fw = None
        if (uvmContext.appManager().isInstantiated("firewall")):
            print(("ERROR: App %s already installed" % "firewall"))
            raise Exception('app %s already instantiated' % "firewall")
        app_fw = uvmContext.appManager().instantiate("firewall", default_policy_id)

        nuke_first_level_rule('bypassRules')

        append_firewall_rule(app_fw, create_single_condition_firewall_rule("DST_PORT","21", blocked=False))
        append_firewall_rule(app_fw, create_single_condition_firewall_rule("PROTOCOL","TCP", blocked=True))

        pasv_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        port_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", extra_arguments="--no-passive-ftp", user=self.ftpUserName, password=self.ftpPassword, uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        epsv_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, extra_arguments="--epsv", uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        eprt_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, extra_arguments="--eprt -P -", uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))

        uvmContext.appManager().destroy( app_fw.getAppSettings()["id"] )
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        print(("port_result: %i eprt_result: %i pasv_result: %i epsv_result: %i" % (port_result,eprt_result,pasv_result,epsv_result)))
        assert (pasv_result == 0)
        assert (port_result == 0)
        assert (epsv_result == 0)
        assert (eprt_result == 0)

    # Test FTP (outbound) in active and passive modes with bypass
    @pytest.mark.failure_in_podman
    def test_072_ftp_modes_bypassed(self):
        set_first_level_rule(create_bypass_condition_rule("DST_PORT","21"),'bypassRules')

        pasv_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        port_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", extra_arguments="--no-passive-ftp", user=self.ftpUserName, password=self.ftpPassword, uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        epsv_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, extra_arguments="--epsv", uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        eprt_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, extra_arguments="--eprt -P -", uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))

        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        print(("port_result: %i eprt_result: %i pasv_result: %i epsv_result: %i" % (port_result,eprt_result,pasv_result,epsv_result)))
        assert (pasv_result == 0)
        assert (port_result == 0)
        assert (epsv_result == 0)
        assert (eprt_result == 0)

    # Test FTP (outbound) in active and passive modes with bypass with a block all rule in forward filter rules. It should pass RELATED session automatically
    @pytest.mark.failure_in_podman
    def test_073_ftp_modes_bypassed_filtered(self):
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['bypassRules']['list'] = [ create_bypass_condition_rule("DST_PORT","21") ]
        netsettings['filterRules']['list'] = [ create_filter_rules("DST_PORT","21","PROTOCOL","TCP",False), create_filter_rules("DST_PORT","1-65535","PROTOCOL","TCP",True) ]
        uvmContext.networkManager().setNetworkSettings(netsettings)

        pasv_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", user=self.ftpUserName, password=self.ftpPassword, uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        port_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", extra_arguments="--no-passive-ftp", user=self.ftpUserName, password=self.ftpPassword, uri=f"ftp://{global_functions.ftp_server}/{ftp_file_name}"))
        epsv_result = remote_control.run_command("curl --user "+ self.ftpUserName + ":" + self.ftpPassword + " --epsv -s -o /dev/null ftp://" + global_functions.ftp_server + "/" + ftp_file_name)
        eprt_result = remote_control.run_command("curl --user "+ self.ftpUserName + ":" + self.ftpPassword + " --eprt -P - -s -o /dev/null ftp://" + global_functions.ftp_server + "/" + ftp_file_name)

        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        print(("port_result: %i eprt_result: %i pasv_result: %i epsv_result: %i" % (port_result,eprt_result,pasv_result,epsv_result)))
        assert (pasv_result == 0)
        assert (port_result == 0)
        assert (epsv_result == 0)
        assert (eprt_result == 0)

    # Test FTP (inbound) in active and passive modes (untangle-vm should add port forwards for RELATED session)
    def test_074_ftp_modes_incoming(self):
        if not run_ftp_inbound_tests:
            raise unittest.SkipTest("remote client does not have ftp server")
        if not device_in_office:
            raise unittest.SkipTest("Not on office network, skipping")

        set_first_level_rule(create_port_forward_triple_condition("DST_PORT","21","DST_LOCAL","true","PROTOCOL","TCP",remote_control.client_ip,""),'portForwardRules')

        pasv_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri=f"ftp://{wan_ip}/{ftp_file_name}"), host=office_ftp_client)
        port_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", extra_arguments="--no-passive-ftp", uri=f"ftp://{wan_ip}/{ftp_file_name}"), host=office_ftp_client)
        epsv_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", extra_arguments="--epsv", uri=f"ftp://{wan_ip}/{ftp_file_name}"), host=office_ftp_client)
        eprt_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", extra_arguments="--eprt -P -", uri=f"ftp://{wan_ip}/{ftp_file_name}"), host=office_ftp_client)

        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        print(("port_result: %i eprt_result: %i pasv_result: %i epsv_result: %i" % (port_result,eprt_result,pasv_result,epsv_result)))
        assert (pasv_result == 0)
        assert (port_result == 0)
        assert (epsv_result == 0)
        assert (eprt_result == 0)

    # Test FTP (inbound) in active and passive modes with bypass (nf_nat_ftp should add port forwards for RELATED session, nat filters should allow RELATED)
    def test_075_ftp_modes_incoming_bypassed(self):
        if not run_ftp_inbound_tests:
            raise unittest.SkipTest("remote client does not have ftp server")
        if not device_in_office:
            raise unittest.SkipTest("Not on office network, skipping")
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['bypassRules']['list'] = [ create_bypass_condition_rule("DST_PORT","21") ]
        netsettings['portForwardRules']['list'] = [ create_port_forward_triple_condition("DST_PORT","21","DST_LOCAL","true","PROTOCOL","TCP",remote_control.client_ip,"") ]
        uvmContext.networkManager().setNetworkSettings(netsettings)

        pasv_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri=f"ftp://{wan_ip}/{ftp_file_name}"), host=office_ftp_client)
        port_result = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", extra_arguments="--no-passive-ftp", uri=f"ftp://{wan_ip}/{ftp_file_name}"), host=office_ftp_client)
        epsv_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", extra_arguments="--epsv", uri=f"ftp://{wan_ip}/{ftp_file_name}"), host=office_ftp_client)
        eprt_result = remote_control.run_command(global_functions.build_curl_command(output_file="/dev/null", extra_arguments="--eprt -P -", uri=f"ftp://{wan_ip}/{ftp_file_name}"), host=office_ftp_client)

        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        print(("port_result: %i eprt_result: %i pasv_result: %i epsv_result: %i" % (port_result,eprt_result,pasv_result,epsv_result)))
        assert (pasv_result == 0)
        assert (port_result == 0)
        assert (epsv_result == 0)
        assert (eprt_result == 0)

    # Test static route that routing test.untangle.com to 127.0.0.1 makes it unreachable
    def test_080_routes(self):
        preResult = remote_control.is_online()

        # add a route to 127.0.0.1 to blackhole that IP
        set_first_level_rule(create_route_rule(test_untangle_com_ip,32,"127.0.0.1"),'staticRoutes')

        postResult = remote_control.run_command(global_functions.build_wget_command(uri="http://test.untangle.com"))

        # restore setting before validating results
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        assert (preResult == 0)
        assert (postResult != 0)

    # Test static DNS entry
    def test_090_static_dns_entry(self):
        # Test static entries in Config -> Networking -> Advanced -> DNS
        nuke_dns_rules()
        add_dns_rule(create_dns_rule(global_functions.ftp_server,"www.foobar.com"))
        result_mod = remote_control.run_command("host -R3 -4 www.foobar.com " + wan_ip, stdout=True)
        # print("Results of www.foobar.com <%s>" % result)
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        match = re.search(r'address \d{1,3}.\d{1,3}.\d{1,3}.\d{1,3}', result_mod)
        ip_address_foobar = (match.group()).replace('address ','')
        # print("IP address of www.foobar.com <%s>" % ip_address_foobar)
        print(("Result expected:\"%s\" actual:\"%s\"" % (str(global_functions.ftp_server),str(ip_address_foobar))))
        assert(global_functions.ftp_server == ip_address_foobar)

    # Test dynamic hostname
    @pytest.mark.slow
    def test_100_dynamic_dns(self):
        if runtests.quick_tests_only:
            raise unittest.SkipTest("Skipping a time consuming test")
        netsettings = uvmContext.networkManager().getNetworkSettings()
        index_of_wans = global_functions.get_wan_tuples()
        if (len(index_of_wans) > 1):
            raise unittest.SkipTest("More than 1 WAN does not work with Dynamic DNS NGFW-5543")

        # if dynamic name is already in the ddclient cache with the same IP, dyndns is never updates
        # we need a name never used or name with cache IP different than in the cache
        outside_IP = global_functions.get_public_ip_address(base_URL=global_functions.TEST_SERVER_HOST,localcall=True)

        dyn_hostname = get_usable_name(outside_IP)
        if dyn_hostname == "":
            raise unittest.SkipTest("Skipping since all dyndns names already used")
        else:
            print(("Using name: %s" % dyn_hostname))
        dyn_DNS_user_name, dyn_DNS_password = global_functions.get_live_account_info(dyn_hostname)
        # account not found if message returned
        if dyn_DNS_user_name == "message":
            raise unittest.SkipTest("no dyn user")

        # Clear the ddclient cache and set DynDNS info
        ddclient_cache_file = "/var/cache/ddclient/ddclient.cache"
        if os.path.isfile(ddclient_cache_file):
            os.remove(ddclient_cache_file)        
        set_dyn_dns(dyn_DNS_user_name, dyn_DNS_password, dyn_hostname)

        # myip.dnsomatic.com site is sometimes offline so use test. 
        ddclient_file = "/etc/ddclient.conf"
        with open(ddclient_file) as f:
            newText=f.read().replace('myip.dnsomatic.com', 'test.untangle.com/cgi-bin/myipaddress.py')
        with open(ddclient_file, "w") as f:
            f.write(newText)        
        # subprocess.check_output("sed -i \'s/myip.dnsomatic.com/test.untangle.com/\cgi-bin\/myipaddress.py/g\' /etc/ddclient.conf", shell=True)
        subprocess.check_output("systemctl restart ddclient.service", shell=True)

        loop_counter = 80
        dyn_IP_found = False
        while loop_counter > 0 and not dyn_IP_found:
            # run force to get it to run now
            try: 
                subprocess.call(["ddclient","--force"],stdout=subprocess.PIPE,stderr=subprocess.PIPE) # force it to run faster
            except subprocess.CalledProcessError:
                print(("Unexpected error:", sys.exc_info()))
            except OSError:
                pass # executable environment not ready
            # time.sleep(10)
            loop_counter -= 1
            dynIP = global_functions.get_hostname_ip_address(hostname=dyn_hostname)
            dynIP = dynIP.decode('utf8')
            print(f"For dyn_hostname={dyn_hostname}, outside_IP={outside_IP}, current dynIP={dynIP}")
            dyn_IP_found = False
            if outside_IP == dynIP:
                dyn_IP_found = True
            else:
                time.sleep(60)

        uvmContext.networkManager().setNetworkSettings(orig_netsettings)
        assert(dyn_IP_found)
        
    # Test VRRP is active
    @pytest.mark.slow
    def test_110_vrrp(self):
        "Test that a VRRP alias is pingable"
        if runtests.quick_tests_only:
            raise unittest.SkipTest('Skipping a time consuming test')
        netsettings = uvmContext.networkManager().getNetworkSettings()
        # Find a static interface
        i=0
        interfaceNotFound = True
        for interface in netsettings['interfaces']['list']:
            if (interface['v4ConfigType'] == "STATIC" and not netsettings['interfaces']['list'][i].get('disabled')):
                interfaceNotFound = False
                break
            i += 1
        # Verify interface is found
        if interfaceNotFound:
            raise unittest.SkipTest("No static enabled interface found")
        interface = netsettings['interfaces']['list'][i]
        interfaceId = interface.get('interfaceId')
        interfaceIP = interface.get('v4StaticAddress')
        interfacePrefix = interface.get('v4StaticPrefix')
        interfaceNet = interfaceIP + "/" + str(interfacePrefix)
        print(("using interface: %i %s\n" % (interfaceId, interface.get('name'))))
        # get next IP not used

        # verify that this NIC is connected (otherwise keepalive wont claim address)
        try:
            result = subprocess.check_output("mii-tool " + interface.get('symbolicDev') + " 2>/dev/null", shell=True)
            if not "link ok" in result:
                raise unittest.SkipTest('LAN not connected')
        except:
            raise unittest.SkipTest('LAN not connected')

        ipStep = 1
        loopCounter = 10
        vrrpIP = None
        ip = ipaddr.IPAddress(interfaceIP)
        while vrrpIP == None and loopCounter:
            # get next IP and test that it is unused
            newip = ip + ipStep
            # check to see if the IP is in network range
            if newip in ipaddr.IPv4Network(interfaceNet):
                pingResult = remote_control.run_command("ping -c 1 %s" % str(newip))
                if pingResult:
                    # new IP found
                    vrrpIP = newip
            else:
                # The IP is beyond the range of the network, go backward through the IPs
                ipStep = -1
            loopCounter -= 1
            ip = newip
        if (vrrpIP == None):
            raise unittest.SkipTest("No IP found for VRRP")

        # Set VRRP values
        netsettings['interfaces']['list'][i]['vrrpAliases'] = {
            "javaClass": "java.util.LinkedList",
            "list": [{
                    "javaClass": "com.untangle.uvm.network.InterfaceSettings$InterfaceAlias",
                    "staticAddress": str(vrrpIP),
                    "staticPrefix": 24
                    }]
            }
        netsettings['interfaces']['list'][i]['vrrpEnabled'] = True
        netsettings['interfaces']['list'][i]['vrrpId'] = 2
        netsettings['interfaces']['list'][i]['vrrpPriority'] = 1
        uvmContext.networkManager().setNetworkSettings(netsettings)

        for x in range(3):
            pingResult = remote_control.run_command("ping -c 1 %s" % str(vrrpIP))
            if pingResult == 0:
                break
        isMaster = uvmContext.networkManager().isVrrpMaster(interfaceId)
        onlineResults = remote_control.is_online()

        # Return to default network state
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        assert (isMaster)
        assert (pingResult == 0)
        assert (onlineResults == 0)

    # Test MTU settings
    def test_120_mtu(self):
        mtu_set_value = '1460'
        target_device = 'eth0'
        mtu_auto_value = None
        # Get current MTU value due to bug 11599
        arg = "ip addr show dev %s" % target_device
        ipAddrShowResults = subprocess.check_output(arg, shell=True)
        ipAddrShowResults = ipAddrShowResults.decode("utf-8")
        # print("ipAddrShowResults: %s" % ipAddrShowResults)
        reValue = re.search(r'mtu\s(\S+)', ipAddrShowResults)
        mtuValue = None
        if reValue:
             mtu_auto_value = reValue.group(1)
        # print("mtu_auto_value: %s" % mtu_auto_value)
        netsettings = uvmContext.networkManager().getNetworkSettings()
        # Set eth0 to 1460
        for i in range(len(netsettings['devices']['list'])):
            if netsettings['devices']['list'][i]['deviceName'] == target_device:
                netsettings['devices']['list'][i]['mtu'] = mtu_set_value
                break
        uvmContext.networkManager().setNetworkSettings(netsettings)
        # Verify the MTU is set
        arg = "ip addr show dev %s" % target_device
        ipAddrShowResults = subprocess.check_output(arg, shell=True)
        ipAddrShowResults = ipAddrShowResults.decode("utf-8")
        # print("ipAddrShowResults: %s" % ipAddrShowResults)
        reValue = re.search(r'mtu\s(\S+)', ipAddrShowResults)
        mtuValue = None
        if reValue:
             mtuValue = reValue.group(1)
        # print("mtuValue: %s" % mtuValue)
        # manually set MTU back to original value due to bug 11599
        netsettings['devices']['list'][i]['mtu'] = mtu_auto_value
        uvmContext.networkManager().setNetworkSettings(netsettings)
        # Set MTU back to auto
        del netsettings['devices']['list'][i]['mtu']
        uvmContext.networkManager().setNetworkSettings(netsettings)
        arg = "ip addr show dev %s" % target_device
        ipAddrShowResults = subprocess.check_output(arg, shell=True)
        ipAddrShowResults = ipAddrShowResults.decode("utf-8")
        # print("ipAddrShowResults: %s" % ipAddrShowResults)
        reValue = re.search(r'mtu\s(\S+)', ipAddrShowResults)
        mtu2Value = None
        if reValue:
             mtu2Value = reValue.group(1)
        # print("mtu2Value: %s " % mtu2Value)
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)
        assert (mtuValue == mtu_set_value)
        assert (mtu2Value == mtu_auto_value)

    # SNMP, v1/v2enabled, v3 disabled
    def test_130_snmp_v1v2_only(self):
        verify_snmp_walk()
        orig_system_settings = uvmContext.systemManager().getSettings()
        system_settings = uvmContext.systemManager().getSettings()
        system_settings['snmpSettings']['enabled'] = True
        system_settings['snmpSettings']['communityString'] = "atstest"
        system_settings['snmpSettings']['sysContact'] = "qa@untangle.com"
        system_settings['snmpSettings']['sendTraps'] = True
        system_settings['snmpSettings']['trapHost'] = remote_control.client_ip
        system_settings['snmpSettings']['port'] = 161
        system_settings['snmpSettings']['v3Enabled'] = False
        uvmContext.systemManager().setSettings(system_settings)
        v2cResult = remote_control.run_command("snmpwalk -v 2c -c atstest " +  global_functions.get_lan_ip() + " | grep untangle")
        v3Result = remote_control.run_command("snmpwalk -v 3 -u testuser -l authPriv -a sha -A password -x des -X drowssap " +  global_functions.get_lan_ip() + " | grep untangle")
        uvmContext.systemManager().setSettings(orig_system_settings)
        assert( v2cResult == 0 )
        assert( v3Result == 1 )

    def test_133_snmp_v3_sha_des(self):
        verify_snmp_walk()
        orig_system_settings = uvmContext.systemManager().getSettings()
        system_settings = uvmContext.systemManager().getSettings()
        system_settings['snmpSettings']['enabled'] = True
        system_settings['snmpSettings']['communityString'] = "atstest"
        system_settings['snmpSettings']['sysContact'] = "qa@untangle.com"
        system_settings['snmpSettings']['sendTraps'] = True
        system_settings['snmpSettings']['trapHost'] = remote_control.client_ip
        system_settings['snmpSettings']['port'] = 161
        commands = set_snmp_v3_settings( system_settings['snmpSettings'], True, "testuser", "sha", "shapassword", "des", "despassword", False )
        uvmContext.systemManager().setSettings(system_settings)
        v2cResult = try_snmp_command( commands[0] )
        v3Result = try_snmp_command( commands[1] )
        uvmContext.systemManager().setSettings(orig_system_settings)
        assert( v2cResult == 0 )
        assert( v3Result == 0 )

    def test_134_snmp_v3_sha_aes(self):
        verify_snmp_walk()
        orig_system_settings = uvmContext.systemManager().getSettings()
        system_settings = uvmContext.systemManager().getSettings()
        system_settings['snmpSettings']['enabled'] = True
        system_settings['snmpSettings']['communityString'] = "atstest"
        system_settings['snmpSettings']['sysContact'] = "qa@untangle.com"
        system_settings['snmpSettings']['sendTraps'] = True
        system_settings['snmpSettings']['trapHost'] = remote_control.client_ip
        system_settings['snmpSettings']['port'] = 161
        commands = set_snmp_v3_settings( system_settings['snmpSettings'], True, "testuser", "sha", "shapassword", "aes", "aespassword", False )
        uvmContext.systemManager().setSettings(system_settings)
        v2cResult = try_snmp_command( commands[0] )
        v3Result = try_snmp_command( commands[1] )
        uvmContext.systemManager().setSettings(orig_system_settings)
        assert( v2cResult == 0 )
        assert( v3Result == 0 )

    def test_135_snmp_v3_md5_des(self):
        verify_snmp_walk()
        orig_system_settings = uvmContext.systemManager().getSettings()
        system_settings = uvmContext.systemManager().getSettings()
        system_settings['snmpSettings']['enabled'] = True
        system_settings['snmpSettings']['communityString'] = "atstest"
        system_settings['snmpSettings']['sysContact'] = "qa@untangle.com"
        system_settings['snmpSettings']['sendTraps'] = True
        system_settings['snmpSettings']['trapHost'] = remote_control.client_ip
        system_settings['snmpSettings']['port'] = 161
        commands = set_snmp_v3_settings( system_settings['snmpSettings'], True, "testuser", "md5", "md5password", "des", "despassword", False )
        uvmContext.systemManager().setSettings(system_settings)
        v2cResult = try_snmp_command( commands[0] )
        v3Result = try_snmp_command( commands[1] )
        uvmContext.systemManager().setSettings(orig_system_settings)
        assert( v2cResult == 0 )
        assert( v3Result == 0 )

    def test_136_snmp_v3_md5_aes(self):
        verify_snmp_walk()
        orig_system_settings = uvmContext.systemManager().getSettings()
        system_settings = uvmContext.systemManager().getSettings()
        system_settings['snmpSettings']['enabled'] = True
        system_settings['snmpSettings']['communityString'] = "atstest"
        system_settings['snmpSettings']['sysContact'] = "qa@untangle.com"
        system_settings['snmpSettings']['sendTraps'] = True
        system_settings['snmpSettings']['trapHost'] = remote_control.client_ip
        system_settings['snmpSettings']['port'] = 161
        commands = set_snmp_v3_settings( system_settings['snmpSettings'], True, "testuser", "md5", "md5password", "aes", "aespassword", False )
        uvmContext.systemManager().setSettings(system_settings)
        v2cResult = try_snmp_command( commands[0] )
        v3Result = try_snmp_command( commands[1] )
        uvmContext.systemManager().setSettings(orig_system_settings)
        assert( v2cResult == 0 )
        assert( v3Result == 0 )

    def test_137_snmp_v3_required(self):
        verify_snmp_walk()
        orig_system_settings = uvmContext.systemManager().getSettings()
        system_settings = uvmContext.systemManager().getSettings()
        system_settings['snmpSettings']['enabled'] = True
        system_settings['snmpSettings']['communityString'] = "atstest"
        system_settings['snmpSettings']['sysContact'] = "qa@untangle.com"
        system_settings['snmpSettings']['sendTraps'] = True
        system_settings['snmpSettings']['trapHost'] = remote_control.client_ip
        system_settings['snmpSettings']['port'] = 161
        commands = set_snmp_v3_settings( system_settings['snmpSettings'], True, "testuser", "sha", "shapassword", "aes", "aespassword", True )
        uvmContext.systemManager().setSettings(system_settings)
        v2cResult = try_snmp_command( commands[0] )
        v3Result = try_snmp_command( commands[1] )
        uvmContext.systemManager().setSettings(orig_system_settings)
        assert( v2cResult == 1 )
        assert( v3Result == 0 )

    def test_138_snmp_disabled(self):
        verify_snmp_walk()
        orig_system_settings = uvmContext.systemManager().getSettings()
        system_settings = uvmContext.systemManager().getSettings()
        system_settings['snmpSettings']['enabled'] = False
        uvmContext.systemManager().setSettings(system_settings)
        result = remote_control.run_command("snmpwalk -v 2c -c atstest " + global_functions.get_lan_ip() + " | grep untangle")
        uvmContext.systemManager().setSettings(orig_system_settings)
        assert(result == 1)

    def test_140_sessions_table(self):
        found_test_session = False
        remote_control.run_command("nohup netcat -d -4 test.untangle.com 80 >/dev/null 2>&1",stdout=False,nowait=True)
        loopLimit = 5
        while ((not found_test_session) and (loopLimit > 0)):
            loopLimit -= 1
            time.sleep(1)
            result = uvmContext.sessionMonitor().getMergedSessions()
            sessionList = result['list']
            # find session generated with netcat in session table.
            for i in range(len(sessionList)):
                # print(sessionList[i])
                # print("------------------------------")
                if (sessionList[i]['preNatClient'] == remote_control.client_ip) and \
                   (sessionList[i]['postNatServer'] == test_untangle_com_ip) and \
                   (sessionList[i]['postNatServerPort'] == 80) and \
                   (not sessionList[i]['bypassed']):
                    found_test_session = True
                    break
        remote_control.run_command("pkill netcat")
        assert(found_test_session)

    def test_141_hosts_table(self):
        found_test_session = False
        remote_control.run_command("nohup netcat -d -4 test.untangle.com 80 >/dev/null 2>&1",stdout=False,nowait=True)
        time.sleep(2) # since we launched netcat in background, give it a second to establish connection
        result = uvmContext.hostTable().getHosts()
        hostList = result['list']
        # find session generated with netcat in session table.
        for i in range(len(hostList)):
            # print(hostList[i])
            # print("------------------------------")
            if (hostList[i]['address'] == remote_control.client_ip):
                foundHost = True
                break
        remote_control.run_command("pkill netcat")
        assert(foundHost)

    # Test logging of blocked sessions via untangle-nflogd
    @pytest.mark.slow
    def test_150_filter_rules_blocked_event_log(self):
        if runtests.quick_tests_only:
            raise unittest.SkipTest('Skipping a time consuming test')
        # verify port 80 is open
        result1 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com"))

        # Add a block rule for port 80 and enabled blocked session logging
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['filterRules']['list'] = [ create_filter_rules("DST_PORT","80","PROTOCOL","TCP",True) ]
        netsettings['logBlockedSessions'] = True
        uvmContext.networkManager().setNetworkSettings(netsettings)

        for i in range(0, 10):
            # make the request again which should now be blocked and logged
            result2 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com/"))

            # grab all of the blocked events for checking later
            events = global_functions.get_events('Network','Blocked Sessions',None,100)
            found = global_functions.check_events( events.get('list'), 100,
                                                   "s_server_addr", test_untangle_com_ip,
                                                   "c_client_addr", remote_control.client_ip,
                                                   "local_addr", remote_control.client_ip,
                                                   "remote_addr", test_untangle_com_ip,
                                                   "s_server_port", 80)
            if found:
                break

            # give the NetFilterLogger time to receive and write the event
            # This is necessary because we have no way to "flush" events
            # Sleep 10 before trying again
            time.sleep(10)

        # put the network settings back the way we found them
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        # make sure all of our tests were successful
        assert (result1 == 0)
        assert (result2 != 0)

        assert(events != None)
        assert(found)

    # Test that filter rule's SRC_ADDR condition supports commas
    def test_151_filter_rules_blocked_src_comma(self):
        # verify port 80 is open
        result1 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com"))

        # Add a block rule for port 80 and enabled blocked session logging
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['filterRules']['list'] = [ create_filter_rules("SRC_ADDR",remote_control.client_ip+",1.2.3.4","PROTOCOL","TCP",True) ]
        uvmContext.networkManager().setNetworkSettings(netsettings)

        # make the request again which should now be blocked
        result2 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com/"))

        # put the network settings back the way we found them
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        assert (result1 == 0)
        assert (result2 != 0)

    # Test that filter rule's SRC_ADDR condition supports commas and many many entries
    # This is because iptables only supports so many entries so the rules must be broken apart
    def test_152_filter_rules_blocked_src_comma_many(self):
        # verify port 80 is open
        result1 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com"))

        str = ""
        for i in range(0,20):
            str += "1.2.3.4,"
        str += remote_control.client_ip

        # Add a block rule for port 80 and enabled blocked session logging
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['filterRules']['list'] = [ create_filter_rules("SRC_ADDR",str,"PROTOCOL","TCP",True) ]
        uvmContext.networkManager().setNetworkSettings(netsettings)

        # make the request again which should now be blocked
        result2 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com/"))

        # put the network settings back the way we found them
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        assert (result1 == 0)
        assert (result2 != 0)

    # Test that filter rule's SRC_MAC condition supports commas
    def test_153_filter_rules_blocked_src_mac_comma(self):
        remote_control.run_command("nohup netcat -d -4 test.untangle.com 80 >/dev/null 2>&1",stdout=False,nowait=True)
        time.sleep(2) # since we launched netcat in background, give it a second to establish connection
        host_list = uvmContext.hostTable().getHosts()['list']
        # find session generated with netcat in session table.
        for i in range(len(host_list)):
            # print(host_list[i])
            # print("------------------------------")
            if (host_list[i]['address'] == remote_control.client_ip):
                found_host = host_list[i]
                break
        remote_control.run_command("pkill netcat")
        assert(found_host != None)
        if found_host.get('macAddress') == None:
            raise unittest.SkipTest('Skipping because we dont know the MAC')
        
        print((found_host.get('macAddress')))
        # verify port 80 is open
        result1 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com"))

        # Add a block rule for port 80 and enabled blocked session logging
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['filterRules']['list'] = [ create_filter_rules("SRC_MAC",found_host.get('macAddress')+",22:22:22:22:22:22","PROTOCOL","TCP",True) ]
        uvmContext.networkManager().setNetworkSettings(netsettings)

        # make the request again which should now be blocked
        result2 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com/"))

        # put the network settings back the way we found them
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        assert (result1 == 0)
        assert (result2 != 0)

    # Test that filter rule's CLIENT_TAGGED condition supports commas
    def test_154_filter_rules_blocked_client_tagged(self):
        # verify port 80 is open
        result1 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com"))

        global_functions.host_tags_add("foobar")
        
        # Add a block rule for port 80 and enabled blocked session logging
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['filterRules']['list'] = [ create_filter_rules("CLIENT_TAGGED","foobar","PROTOCOL","TCP",True) ]
        uvmContext.networkManager().setNetworkSettings(netsettings)

        # make the request again which should now be blocked
        result2 = remote_control.run_command(global_functions.build_wget_command(output_file="/dev/null", uri="http://test.untangle.com/"))

        global_functions.host_tags_clear()
        
        # put the network settings back the way we found them
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        assert (result1 == 0)
        assert (result2 != 0)
        
    # Test UDP traceroute
    def test_160_traceroute_udp(self):
        traceroute_exists = remote_control.run_command("test -x /usr/sbin/traceroute")
        if traceroute_exists != 0:
            raise unittest.SkipTest("Traceroute app needs to be installed on client")
        result = remote_control.run_command("/usr/sbin/traceroute test.untangle.com", stdout=True)
        # 3 occurances of ms per line so check for at least two lines of ms times.
        assert(result.count('ms') > 4) 

    # UPnP - Disabled
    def test_170_upnp_disabled(self):
        upnpc_exists = remote_control.run_command("test -x /usr/bin/upnpc")
        if upnpc_exists != 0:
            raise unittest.SkipTest("Upnpc app needs to be installed on client")
        if global_functions.is_bridged(wan_ip):
            raise unittest.SkipTest("Unable to disable upnp on bridged configurations")
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['upnpSettings']['upnpEnabled'] = False
        uvmContext.networkManager().setNetworkSettings(netsettings)
        result = remote_control.run_command("/usr/bin/upnpc -i -a %s 5559 5559 tcp >/dev/null 2>&1" % (remote_control.client_ip),stdout=False)
        assert(result != 0)

    # UPnP - Enabled
    def test_171_upnp_enabled_defaults(self):
        upnpc_exists = remote_control.run_command("test -x /usr/bin/upnpc")
        if upnpc_exists != 0:
            raise unittest.SkipTest("Upnpc app needs to be installed on client")
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['upnpSettings']['upnpEnabled'] = True
        uvmContext.networkManager().setNetworkSettings(netsettings)
        result = remote_control.run_command("/usr/bin/upnpc -i -a %s 5559 5559 tcp >/dev/null 2>&1" % (remote_control.client_ip),stdout=False)
        assert(result == 0)

    # UPnP - Secure mode enabled
    def test_172_upnp_secure_mode_enabled(self):
        upnpc_exists = remote_control.run_command("test -x /usr/bin/upnpc")
        if upnpc_exists != 0:
            raise unittest.SkipTest("Upnpc app needs to be installed on client")
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['upnpSettings']['upnpEnabled'] = True
        netsettings['upnpSettings']['secureMode'] = True
        uvmContext.networkManager().setNetworkSettings(netsettings)
        result1 = remote_control.run_command("/usr/bin/upnpc -i -a %s 5559 5559 tcp >/dev/null 2>&1" % (remote_control.client_ip),stdout=False)
        result2 = remote_control.run_command("/usr/bin/upnpc -i -a %s 5558 5558 tcp 2>&1 | grep ConflictInMappingEntry" % ("1.2.3.4"),stdout=False)
        assert(result1 == 0)
        assert(result2 == 0)

    # UPnP - Secure mode disabled
    def test_173_upnp_secure_mode_disabled(self):
        upnpc_exists = remote_control.run_command("test -x /usr/bin/upnpc")
        if upnpc_exists != 0:
            raise unittest.SkipTest("Upnpc app needs to be installed on client")
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['upnpSettings']['upnpEnabled'] = True
        netsettings['upnpSettings']['secureMode'] = False
        uvmContext.networkManager().setNetworkSettings(netsettings)
        result1 = remote_control.run_command("/usr/bin/upnpc -i -a %s 5559 5559 tcp >/dev/null 2>&1" % (remote_control.client_ip),stdout=False)
        result2 = remote_control.run_command("/usr/bin/upnpc -i -a %s 5558 5558 tcp 2>&1 | grep ConflictInMappingEntry" % ("1.2.3.4"),stdout=False)
        assert(result1 == 0)
        assert(result2 == 1)

    # UPnP - Enabled, Deny rule
    def test_174_upnp_rules_deny_all(self):
        upnpc_exists = remote_control.run_command("test -x /usr/bin/upnpc")
        if upnpc_exists != 0:
            raise unittest.SkipTest("Upnpc app needs to be installed on client")
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['upnpSettings']['upnpEnabled'] = True
        netsettings['upnpSettings']['upnpRules'] = {
            "javaClass": "java.util.LinkedList",
            "list": [{
                    "allow": False,
                    "conditions": {
                        "javaClass": "java.util.LinkedList",
                        "list": [
                            {
                                "conditionType": "SRC_ADDR",
                                "invert": False,
                                "javaClass": "com.untangle.uvm.network.UpnpRuleCondition",
                                "value": "0.0.0.0/0"
                            },
                            {
                                "conditionType": "DST_PORT",
                                "invert": False,
                                "javaClass": "com.untangle.uvm.network.UpnpRuleCondition",
                                "value": "0-65535"
                            },
                            {
                                "conditionType": "SRC_PORT",
                                "invert": False,
                                "javaClass": "com.untangle.uvm.network.UpnpRuleCondition",
                                "value": "0-65535"
                            }
                        ]
                    },
                    "description": "Deny all",
                    "enabled": True,
                    "javaClass": "com.untangle.uvm.network.UpnpRule",
                    "ruleId": 2
                }                
            ]
        }
        uvmContext.networkManager().setNetworkSettings(netsettings)
        result = remote_control.run_command("/usr/bin/upnpc -i -a %s 5559 5559 tcp 2>&1 | grep failed" % (remote_control.client_ip),stdout=False)
        assert(result == 0)

    def test_180_netflow_enable_disable(self):
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['netflowSettings']['enabled'] = True
        netsettings['netflowSettings']['host'] = global_functions.LIST_SYSLOG_SERVER
        netsettings['netflowSettings']['port'] = 9555
        netsettings['netflowSettings']['version'] = "9"
        uvmContext.networkManager().setNetworkSettings(netsettings)
        # check if netflow is running
        result1 = subprocess.call("ps aux | grep softflowd | grep -v grep >/dev/null 2>&1", shell=True)

        # check if netflow stops running if disabled.
        netsettings['netflowSettings']['enabled'] = False
        uvmContext.networkManager().setNetworkSettings(netsettings)
        result2 = subprocess.call("ps aux | grep softflowd | grep -v grep >/dev/null 2>&1", shell=True)

        uvmContext.networkManager().setNetworkSettings(orig_netsettings)

        assert(result1 == 0)
        assert(result2 == 1)

    def test_190_qos_statistics(self):
        # Check of the QoS Statistics script is returning the proper string.        
        # Enable QoS if not enabled
        netsettings = uvmContext.networkManager().getNetworkSettings()
        netsettings['qosSettings']['qosEnabled'] = True
        i = 0
        for interface in netsettings['interfaces']['list']:
            if interface['isWan']:
                netsettings['interfaces']['list'][i]['downloadBandwidthKbps']=10000
                netsettings['interfaces']['list'][i]['uploadBandwidthKbps']=10000
            i += 1
        netsettings['bypassRules']['list'] = []
        netsettings['qosSettings']['qosRules']['list'] = []
        uvmContext.networkManager().setNetworkSettings(netsettings)
        
        found = False
        timeout = 60
        qos_data = []
        remote_control.is_online() # generate some traffic.
        while not found and timeout > 0:
            timeout -= 1
            time.sleep(1)
            qos_output_obj = subprocess.run("/usr/share/untangle/bin/qos-status.py", capture_output=True)
            if qos_output_obj.returncode != 0 or qos_output_obj.stdout is None :
                timeout -= 1
                time.sleep(1)
                continue
            qos_query_output = qos_output_obj.stdout
            qos_output_decode = qos_query_output.decode("utf-8")
            # The JSON returned is using single quotes which is not JSON spec RFC7159 
            qos_output_decode = qos_output_decode.replace("\'", "\"")
            qos_data = json.loads(qos_output_decode)
            if qos_data:
                found = True
            else:
                global_functions.get_download_speed()
        assert(qos_data[0]["priority"] != '')

    def test_201_wireless_current_country_code_channels(self):
        """
        Verify we can get channels for the current country code
        """
        wireless_interfaces_found = False
        wireless_system_dev = None
        netsettings = uvmContext.networkManager().getNetworkSettings()
        for interface in netsettings['interfaces']['list']:
            if interface['isWirelessInterface'] is True:
                wireless_interfaces_found = True
                wireless_system_dev = interface["systemDev"]
                break

        if wireless_interfaces_found is False:
            raise unittest.SkipTest("missing wireless interfaces")

        channels = uvmContext.networkManager().getWirelessChannels(wireless_system_dev,"")
        assert(len(channels) > 0)

    def test_202_wireless_test_country_code_channels(self):
        """
        Verify we can get channels for the new target country code
        """
        wireless_interfaces_found = False
        wireless_system_dev = None
        original_wireless_country_code = None
        netsettings = uvmContext.networkManager().getNetworkSettings()
        for interface in netsettings['interfaces']['list']:
            if interface['isWirelessInterface'] is True:
                wireless_interfaces_found = True
                original_wireless_country_code = interface['wirelessCountryCode']
                interface['wirelessCountryCode'] = ""
                wireless_system_dev = interface["systemDev"]
                break

        if wireless_interfaces_found is False:
            raise unittest.SkipTest("missing wireless interfaces")

        if uvmContext.networkManager().isWirelessRegulatoryCompliant(wireless_system_dev) is False:
            raise unittest.SkipTest("cannot change country code on non-compliant driver")

        # Get current channels
        original_channels = uvmContext.networkManager().getWirelessChannels(wireless_system_dev,"")
        assert(len(original_channels) > 0)

        current_driver_regulatory_country_code = uvmContext.networkManager().getWirelessRegulatoryCountryCode(wireless_system_dev)

        # Want a different regulatory country code to verify the country counts are not the same
        if original_wireless_country_code == "US" or current_driver_regulatory_country_code == "US":
            test_country_code = "JP"
        else:
            test_country_code = "US"
        test_channels = uvmContext.networkManager().getWirelessChannels(wireless_system_dev,test_country_code)
        assert(len(test_channels) > 0)

        print(f"expecting: original_channels={len(original_channels)} != test_channels={len(test_channels)}")

        assert(len(test_channels) != len(original_channels))

    def test_203_wireless_valid_regulatory_country_codes(self):
        """
        Verify we have a non-zero list of regulatory country codes
        If our list is zero, we are likely missing a crda file like /usr/lib/crda/regulatory.bin.
        """
        wireless_interfaces_found = False
        wireless_system_dev = None
        netsettings = uvmContext.networkManager().getNetworkSettings()
        for interface in netsettings['interfaces']['list']:
            if interface['isWirelessInterface'] is True:
                wireless_interfaces_found = True
                wireless_system_dev = interface["systemDev"]
                break

        if wireless_interfaces_found is False:
            raise unittest.SkipTest("missing wireless interfaces")

        countries = uvmContext.networkManager().getWirelessValidRegulatoryCountryCodes(wireless_system_dev)
        print(countries)
        assert(len(countries) > 0)

    def test_350_lan_dhcp_server(self):
        """
        Verify DHCP server on LAN interface
        """
        lan_interface = None
        netsettings = uvmContext.networkManager().getNetworkSettings()
        for interface in netsettings['interfaces']['list']:
            if interface.get("configType") != 'ADDRESSED':
                continue
            if interface.get("isWan"):
                continue
            lan_interface = interface
            break

        if lan_interface is None:
            raise unittest.SkipTest("missing LAN interface")

        lan_address = lan_interface["v4StaticAddress"]
        lan_subnet = lan_interface["v4StaticNetmask"]

        # Default DHCP settings
        lan_interface["dhcpType"] = "SERVER"
        dhcp_range_start = ".".join(lan_address.split(".")[0:3]) + ".98"
        dhcp_range_end = ".".join(lan_address.split(".")[0:3]) + ".99"
        lan_interface["dhcpRangeStart"] = dhcp_range_start
        lan_interface["dhcpRangeEnd"] = dhcp_range_end
        lan_interface["dhcpDnsOverride"] = ""
        lan_interface["dhcpGatewayOverride"] = ""
        lan_interface["dhcpNetmaskOverride"] = ""
        lan_interface["dhcpPrefixOverride"] = None
        lan_interface["dhcpOptions"]["list"] = []

        uvmContext.networkManager().setNetworkSettings(netsettings)

        dhcp_results = global_functions.get_dhcp_client_results()
        if dhcp_results["no_privileges"]:
            raise unittest.SkipTest("client does not have privileges")

        print(dhcp_results)
        assert dhcp_results["dhcp"]["ip_offered"] == dhcp_range_start or dhcp_results["dhcp"]["ip_offered"] == dhcp_range_end, "offered IP address in range"
        assert dhcp_results["dhcp"]["subnet_mask"] == lan_subnet, "subnet mask"
        assert dhcp_results["dhcp"]["router"] == lan_address, "gateway address is lan address"
        assert dhcp_results["dhcp"]["domain_name_server"] == lan_address, "dns address is lan address"

    def test_351_lan_dhcp_server_override_and_options(self):
        """
        Verify DHCP server on LAN interface
        """
        lan_interface = None
        netsettings = uvmContext.networkManager().getNetworkSettings()
        for interface in netsettings['interfaces']['list']:
            if interface.get("configType") != 'ADDRESSED':
                continue
            if interface.get("isWan"):
                continue
            lan_interface = interface
            break

        if lan_interface is None:
            raise unittest.SkipTest("missing LAN interface")

        lan_address = lan_interface["v4StaticAddress"]
        lan_subnet = lan_interface["v4StaticNetmask"]

        # Override and some options
        lan_interface["dhcpType"] = "SERVER"
        dhcp_range_start = ".".join(lan_address.split(".")[0:3]) + ".98"
        dhcp_range_end = ".".join(lan_address.split(".")[0:3]) + ".99"
        lan_dns = ".".join(lan_address.split(".")[0:3]) + ".10"
        lan_gateway = ".".join(lan_address.split(".")[0:3]) + ".11"
        lan_prefix = 25
        lan_subnet = "255.255.255.128"
        time_server = "1.2.3.4"
        lan_interface["dhcpRangeStart"] = dhcp_range_start
        lan_interface["dhcpRangeEnd"] = dhcp_range_end
        lan_interface["dhcpDnsOverride"] = lan_dns
        lan_interface["dhcpGatewayOverride"] = lan_gateway
        lan_interface["dhcpNetmaskOverride"] = lan_subnet
        lan_interface["dhcpPrefixOverride"] = lan_prefix
        lan_interface["dhcpOptions"]["list"] = [{
            "description": "time server",
            "enabled": True,
            "javaClass": "com.untangle.uvm.network.DhcpOption",
            "value": f"4,{time_server}"
        }]

        uvmContext.networkManager().setNetworkSettings(netsettings)

        dhcp_results = global_functions.get_dhcp_client_results()
        if dhcp_results["no_privileges"]:
            raise unittest.SkipTest("client does not have privileges")
        print(dhcp_results)

        assert dhcp_results["dhcp"]["ip_offered"] == dhcp_range_start or dhcp_results["dhcp"]["ip_offered"] == dhcp_range_end, "offered IP address in range"
        assert dhcp_results["dhcp"]["subnet_mask"] == lan_subnet, "subnet mask"
        assert dhcp_results["dhcp"]["router"] == lan_gateway, "gateway address overridden"
        assert dhcp_results["dhcp"]["domain_name_server"] == lan_dns, "dns address overridden"
        assert dhcp_results["dhcp"]["time_server"] == time_server, "option for time server"

    def test_352_lan_dhcp_relay(self):
        """
        Verify DHCP dhcp on LAN interface
        """
        lan_interface = None
        netsettings = uvmContext.networkManager().getNetworkSettings()
        for interface in netsettings['interfaces']['list']:
            if interface.get("configType") != 'ADDRESSED':
                continue
            if interface.get("isWan"):
                continue
            lan_interface = interface
            break

        if lan_interface is None:
            raise unittest.SkipTest("missing LAN interface")

        # Establish ipsec tunnel to remote DHCP server
        app_ipsec = NetworkTests.get_app("ipsec-vpn")
        ipsec_app_settings = app_ipsec.getSettings()
        ipsec_app_settings["tunnels"]["list"].append(test_ipsec_vpn.build_ipsec_tunnel())
        app_ipsec.setSettings(ipsec_app_settings)

        ping_result = 1
        timeout = 10
        while (ping_result and timeout > 0):
            timeout -= 1
            time.sleep(1)
            ping_result = subprocess.call(["ping","-c","1",test_ipsec_vpn.IPSEC_HOST_LAN_IP],stdout=subprocess.PIPE,stderr=subprocess.PIPE)

        if ping_result:
            assert False, "unable to establish ipsec connection"

        # Default DHCP settings
        lan_interface["dhcpType"] = "RELAY"
        lan_interface["dhcpRelayAddress"] = DHCP_RELAY_ADDRESS

        uvmContext.networkManager().setNetworkSettings(netsettings)

        dhcp_results = global_functions.get_dhcp_client_results()
        if dhcp_results["no_privileges"]:
            raise unittest.SkipTest("client does not have privileges")

        print(dhcp_results)

        # Not so easy to verify results, but being defined is enough
        assert "ip_offered" in dhcp_results["dhcp"], "offered IP address in range"
        assert "subnet_mask" in dhcp_results["dhcp"], "offered subnet mask"
        assert "router" in dhcp_results["dhcp"], "offered gateway"
        assert "domain_name_server" in dhcp_results["dhcp"], "offered DNS address"
        # Identifier tends to be firist IP address which is typically WAN but could be LAN depending on order, so try both
        assert dhcp_results["dhcp"]["server_identifier"] == DHCP_RELAY_ADDRESS or dhcp_results["dhcp"]["server_identifier"] == test_ipsec_vpn.IPSEC_HOST, "received from relay host"

    def test_353_lan_dhcp_disabled(self):
        """
        Verify DHCP server/relay disabled on LAN interface
        """
        lan_interface = None
        netsettings = uvmContext.networkManager().getNetworkSettings()
        for interface in netsettings['interfaces']['list']:
            if interface.get("configType") != 'ADDRESSED':
                continue
            if interface.get("isWan"):
                continue
            lan_interface = interface
            break

        if lan_interface is None:
            raise unittest.SkipTest("missing LAN interface")

        lan_interface["dhcpType"] = "DISABLED"

        uvmContext.networkManager().setNetworkSettings(netsettings)

        dhcp_results = global_functions.get_dhcp_client_results()
        if dhcp_results["no_privileges"]:
            raise unittest.SkipTest("client does not have privileges")

        print(dhcp_results)
        assert len(dhcp_results["dhcp"].keys()) == 0, "empty dhcp results"

    def test_400_nic_remapping(self):
        """
        Remap nics
        """
        # Get list of candidate devices to use for swapping
        device_candidates = []
        netsettings = uvmContext.networkManager().getNetworkSettings()
        for interface in netsettings['interfaces']['list']:
            if interface.get("isVirtualInterface") or \
                interface.get("isVlanInterface") or \
                interface.get("isWirelessInterface"):
                # Don't consider virtual, vlan, or wireless interfaces
                continue
            device_candidates.append(interface.get("physicalDev"))

        if len(device_candidates) < 4:
            raise unittest.SkipTest("not enough devices to safely swap")

        print(device_candidates[-2:])
        # Build current device/mac mapping
        original_device_mac_mapping = build_device_to_mac_address_map(device_candidates[-2:])
        print(f"original_device_mac_mapping={original_device_mac_mapping}")

        # Set settings forces sync-settngs call
        uvmContext.networkManager().setNetworkSettings(netsettings)

        # What do we have in systemd network directory?
        found_device_links = []
        for filename in glob.glob(f"/etc/systemd/network/*"):
            device = filename[filename.rfind("/")+1:]
            device = device[:device.find(".")]
            found_device_links.append(device)
        assert len(set(found_device_links).intersection(device_candidates)) == len(device_candidates), "found same number of link files matching devices"
        assert len(set(found_device_links).difference(device_candidates)) == 0, "no extra link files found"

        # Simulate a system reboot:
        # 1. Swap last two devices with interface mapper
        command=f"/usr/share/untangle/bin/interface-mapping.sh -r {device_candidates[-1]}={device_candidates[-2]}"
        print(command)
        print(subprocess.call(command, shell=True))

        # 2. Gather this modified system mapping, simulating reboot with kernel picking different mac addresses
        modified_device_mac_mapping = build_device_to_mac_address_map(device_candidates[-2:])
        print(f"modified_device_mac_mapping={modified_device_mac_mapping}")

        # 3. Restart networking
        command="ifdown -a -v --exclude=lo && ifup -a -v --exclude=lo && /usr/bin/systemctl-wait"
        print(command)
        print(subprocess.call(command, shell=True, stderr=subprocess.STDOUT, stdout=subprocess.DEVNULL))

        # Verify original mapping is preserved
        new_device_mac_mapping = build_device_to_mac_address_map(device_candidates[-2:])
        print(f"     new_device_mac_mapping={new_device_mac_mapping}")
        for device in original_device_mac_mapping.keys():
            assert new_device_mac_mapping[device] == original_device_mac_mapping[device], f"{device}: orig and new mac address same"

    @classmethod
    def final_extra_tear_down(cls):
        # Restore original settings to return to initial settings
        # print("orig_netsettings <%s>" % orig_netsettings)
        uvmContext.networkManager().setNetworkSettings(orig_netsettings)


test_registry.register_module("network", NetworkTests)
