package org.zstack.test.integration.networkservice.provider.virtualrouter.loadbalancer

import org.zstack.core.db.DatabaseFacade
import org.zstack.header.network.service.NetworkServiceType
import org.zstack.network.service.eip.EipConstant
import org.zstack.network.service.lb.LoadBalancerConstants
import org.zstack.network.service.lb.LoadBalancerListenerVO
import org.zstack.network.service.lb.LoadBalancerVO
import org.zstack.network.service.portforwarding.PortForwardingConstant
import org.zstack.network.service.virtualrouter.vyos.VyosConstants
import org.zstack.sdk.L3NetworkInventory
import org.zstack.sdk.LoadBalancerInventory
import org.zstack.sdk.LoadBalancerListenerInventory
import org.zstack.sdk.VipInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.networkservice.provider.NetworkServiceProviderTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * Created by weiwang on 06/09/2017
 */
class DeleteLbWithoutVmNicCase extends SubCase{
    DatabaseFacade dbf
    EnvSpec env

    @Override
    void setup() {
        useSpring(NetworkServiceProviderTest.springSpec)
    }

    @Override
    void environment() {
        env = env {

            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(8)
                cpu = 4
            }

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"

                image {
                    name = "image"
                    url = "http://zstack.org/download/test.qcow2"
                }

                image {
                    name = "vr"
                    url = "http://zstack.org/download/vr.qcow2"
                }
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm"
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("local")
                    attachL2Network("l2")
                }

                localPrimaryStorage {
                    name = "local"
                    url = "/local_ps"
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

                        service {
                            provider = VyosConstants.VYOS_ROUTER_PROVIDER_TYPE
                            types = [NetworkServiceType.DHCP.toString(),
                                     NetworkServiceType.DNS.toString(),
                                     NetworkServiceType.SNAT.toString(),
                                     PortForwardingConstant.PORTFORWARDING_NETWORK_SERVICE_TYPE,
                                     LoadBalancerConstants.LB_NETWORK_SERVICE_TYPE_STRING,
                                     EipConstant.EIP_NETWORK_SERVICE_TYPE]
                        }

                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }

                    l3Network {
                        name = "pubL3"

                        ip {
                            startIp = "11.168.100.10"
                            endIp = "11.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "11.168.100.1"
                        }
                    }
                }

                attachBackupStorage("sftp")

                virtualRouterOffering {
                    name = "vro"
                    memory = SizeUnit.MEGABYTE.toByte(512)
                    cpu = 2
                    useManagementL3Network("pubL3")
                    usePublicL3Network("pubL3")
                    useImage("vr")
                }
            }

            vm {
                name = "vm"
                useImage("image")
                useL3Networks("l3")
                useInstanceOffering("instanceOffering")
            }
        }
    }

    @Override
    void test() {
        dbf = bean(DatabaseFacade.class)
        env.create {
            deleteLbWithoutVmNicCase()
            deleteLbWithVmNicCase()
        }
    }

    void deleteLbWithoutVmNicCase() {
        VmInstanceInventory vm = env.inventoryByName("vm")
        L3NetworkInventory pubL3 = env.inventoryByName("pubL3")
        VipInventory vip = createVip {
            delegate.name = "test-vip"
            delegate.l3NetworkUuid = pubL3.uuid
        }
        LoadBalancerInventory lb = createLoadBalancer {
            delegate.name = "test-lb"
            delegate.vipUuid = vip.uuid
        }
        LoadBalancerListenerInventory listener = createLoadBalancerListener {
            delegate.protocol = "tcp"
            delegate.loadBalancerUuid = lb.uuid
            delegate.loadBalancerPort = 44
            delegate.instancePort = 22
            delegate.name = "test-listener"
        }
        addVmNicToLoadBalancer {
            delegate.listenerUuid = listener.uuid
            delegate.vmNicUuids = [vm.vmNics[0].uuid]
        }

        deleteVip {
            delegate.uuid = lb.vipUuid
        }
        assert dbf.count(LoadBalancerListenerVO.class) == 0
        assert dbf.count(LoadBalancerVO.class) == 0
    }

    void deleteLbWithVmNicCase() {
        L3NetworkInventory pubL3 = env.inventoryByName("pubL3")
        VipInventory vip = createVip {
            delegate.name = "test-vip"
            delegate.l3NetworkUuid = pubL3.uuid
        }
        LoadBalancerInventory lb = createLoadBalancer {
            delegate.name = "test-lb"
            delegate.vipUuid = vip.uuid
        }
        createLoadBalancerListener {
            delegate.protocol = "tcp"
            delegate.loadBalancerUuid = lb.uuid
            delegate.loadBalancerPort = 44
            delegate.instancePort = 22
            delegate.name = "test-listener"
        }

        deleteVip {
            delegate.uuid = lb.vipUuid
        }
        assert dbf.count(LoadBalancerListenerVO.class) == 0
        assert dbf.count(LoadBalancerVO.class) == 0
    }

    @Override
    void clean() {
        env.delete()
    }
}
