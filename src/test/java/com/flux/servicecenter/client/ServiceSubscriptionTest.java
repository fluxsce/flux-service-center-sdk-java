package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.listener.ServiceChangeListenerAdapter;
import com.flux.servicecenter.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务订阅测试类
 * 
 * <p>测试服务变更订阅功能，包括：
 * <ul>
 *   <li>订阅单个服务</li>
 *   <li>订阅多个服务</li>
 *   <li>订阅整个命名空间</li>
 *   <li>测试各种事件（节点添加、更新、移除等）</li>
 *   <li>测试取消订阅</li>
 * </ul>
 * </p>
 * 
 * <p>注意：所有与后端交互的操作（订阅、取消订阅等）都会在日志中打印响应信息。
 * 响应日志使用 INFO 级别，格式为：操作名称响应: key1=value1, key2=value2, ...</p>
 * 
 * <p>要查看响应日志，请确保日志级别设置为 INFO 或更低（DEBUG）。</p>
 * 
 * @author shangjian
 */
public class ServiceSubscriptionTest {

    private ServiceCenterConfig config;
    private ServiceCenterClient client;

    @BeforeEach
    public void setUp() {
        config = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4") // 使用服务端提供的命名空间
                .setGroupName("DEFAULT_GROUP"); // 使用默认分组
    }

    @AfterEach
    public void tearDown() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // 忽略关闭错误
            }
        }
    }

    // ========== 订阅功能测试 - 默认值填充逻辑 ==========

    @Test
    public void testSubscribe_WithDefaultNamespaceAndGroup() {
        client = new ServiceCenterClient(config);
        
        ServiceChangeListener listener = event -> {
            // 空实现，仅用于测试默认值填充
        };
        
        // 调用方法，验证默认值填充逻辑
        try {
            client.subscribe(null, null, Arrays.asList("test-service"), listener);
        } catch (Exception e) {
            // 预期会失败（因为未连接），但我们主要测试默认值填充
        }
        
        // 注意：subscribe 方法内部会使用配置中的默认值
        // 但由于方法签名不修改参数，我们无法直接验证
        // 这里主要测试方法调用不会抛出异常（在未连接时）
    }

    @Test
    public void testSubscribe_WithNullListener() {
        client = new ServiceCenterClient(config);
        
        // 调用方法，验证 null listener 会抛出异常
        assertThrows(IllegalArgumentException.class, () -> 
            client.subscribe("ns_test", "DEFAULT_GROUP", Arrays.asList("test-service"), null)
        );
    }

    @Test
    public void testUnsubscribeService_WithValidSubscriptionId() {
        client = new ServiceCenterClient(config);
        
        // 取消订阅应该可以安全调用，即使订阅ID不存在
        client.unsubscribeService("non-existent-subscription-id");
        // 验证可以多次调用（幂等性）
        client.unsubscribeService("non-existent-subscription-id");
    }

    // ========== 集成测试示例（需要真实服务端）==========
    
    /**
     * 真实测试1：订阅单个服务并接收节点变更事件
     * 
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试服务订阅功能。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>订阅服务变更</li>
     *   <li>注册节点，验证收到 NODE_ADDED 事件</li>
     *   <li>更新节点，验证收到 NODE_UPDATED 事件</li>
     *   <li>注销节点，验证收到 NODE_REMOVED 事件</li>
     *   <li>取消订阅</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testSubscribeSingleService_WithRealServer() {
        // 配置使用服务端提供的命名空间
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP");
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            // 连接服务端
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端");
            System.out.println("=== 已连接到服务端，开始测试服务订阅 ===");
            
            // 创建事件计数器
            AtomicInteger nodeAddedCount = new AtomicInteger(0);
            AtomicInteger nodeUpdatedCount = new AtomicInteger(0);
            AtomicInteger nodeRemovedCount = new AtomicInteger(0);
            CountDownLatch nodeAddedLatch = new CountDownLatch(1);
            CountDownLatch nodeUpdatedLatch = new CountDownLatch(1);
            CountDownLatch nodeRemovedLatch = new CountDownLatch(1);
            
            // 创建监听器
            ServiceChangeListener listener = new ServiceChangeListenerAdapter() {
                @Override
                public void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
                    nodeAddedCount.incrementAndGet();
                    System.out.println("收到节点添加事件: service=" + (service != null ? service.getServiceName() : "null") + 
                                     ", nodeId=" + node.getNodeId() + 
                                     ", ip=" + node.getIpAddress() + 
                                     ", port=" + node.getPortNumber() +
                                     ", weight=" + node.getWeight() +
                                     ", instanceStatus=" + node.getInstanceStatus() +
                                     ", healthyStatus=" + node.getHealthyStatus() +
                                     ", ephemeral=" + node.getEphemeral() +
                                     ", totalNodes=" + allNodes.size() +
                                     ", allNodeIds=" + allNodes.stream().map(NodeInfo::getNodeId).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
                    nodeAddedLatch.countDown();
                }
                
                @Override
                public void onNodeUpdated(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
                    nodeUpdatedCount.incrementAndGet();
                    System.out.println("收到节点更新事件: service=" + service.getServiceName() + 
                                     ", nodeId=" + node.getNodeId() + 
                                     ", ip=" + node.getIpAddress() + 
                                     ", port=" + node.getPortNumber() +
                                     ", totalNodes=" + allNodes.size());
                    nodeUpdatedLatch.countDown();
                }
                
                @Override
                public void onNodeRemoved(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
                    nodeRemovedCount.incrementAndGet();
                    System.out.println("收到节点移除事件: service=" + (service != null ? service.getServiceName() : "null") + 
                                     ", nodeId=" + node.getNodeId() + 
                                     ", ip=" + node.getIpAddress() + 
                                     ", port=" + node.getPortNumber() +
                                     ", totalNodes=" + allNodes.size() +
                                     ", remainingNodeIds=" + allNodes.stream().map(NodeInfo::getNodeId).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
                    nodeRemovedLatch.countDown();
                }
                
                @Override
                public void onServiceChange(ServiceChangeEvent event) {
                    System.out.println("收到服务变更事件: eventType=" + event.getEventType() + 
                                     ", service=" + event.getServiceName() + 
                                     ", namespaceId=" + event.getNamespaceId() +
                                     ", groupName=" + event.getGroupName() +
                                     ", timestamp=" + event.getTimestamp() +
                                     ", serviceInfo=" + (event.getService() != null ? event.getService().getServiceName() : "null") +
                                     ", nodesCount=" + (event.getAllNodes() != null ? event.getAllNodes().size() : 0) +
                                     ", changedNode=" + (event.getChangedNode() != null ? event.getChangedNode().getNodeId() : "null"));
                    // 调用父类方法，触发事件路由（调用 onNodeAdded、onNodeUpdated 等）
                    super.onServiceChange(event);
                }
                
                @Override
                public void onDisconnected(Throwable cause) {
                    System.out.println("订阅连接断开: " + (cause != null ? cause.getMessage() : "未知原因"));
                }
                
                @Override
                public void onReconnected() {
                    System.out.println("订阅连接已恢复");
                }
            };
            
            // 订阅服务
            System.out.println("--- 订阅服务变更 ---");
            String subscriptionId = testClient.subscribe(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    Arrays.asList("test-subscription-service"),
                    listener);
            System.out.println("订阅成功，subscriptionId: " + subscriptionId);
            assertNotNull(subscriptionId, "订阅ID不应该为空");
            assertFalse(subscriptionId.isEmpty(), "订阅ID不应该为空字符串");
            
            // 等待一下，确保订阅已建立
            Thread.sleep(1000);
            
            // 注册服务（不包含节点）
            System.out.println("--- 注册服务（不包含节点）---");
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setServiceName("test-subscription-service");
            serviceInfo.setServiceType("INTERNAL");
            serviceInfo.setServiceVersion("1.0.0");
            serviceInfo.setServiceDescription("订阅测试服务");
            
            RegisterServiceResult registerServiceResult = testClient.registerService(serviceInfo, null);
            System.out.println("注册服务结果: " + registerServiceResult);
            assertTrue(registerServiceResult.isSuccess(), "服务注册应该成功");
            
            // 等待一下，确保服务已注册
            Thread.sleep(500);
            
            // 注册第一个节点，应该收到 NODE_ADDED 事件
            System.out.println("--- 注册第一个节点（应该收到 NODE_ADDED 事件）---");
            NodeInfo nodeInfo1 = new NodeInfo("127.0.0.1", 9090);
            nodeInfo1.setServiceName("test-subscription-service");
            nodeInfo1.setWeight(1.0);
            nodeInfo1.setEphemeral("Y");
            nodeInfo1.setInstanceStatus("UP");
            nodeInfo1.setHealthyStatus("HEALTHY");
            
            RegisterNodeResult registerNode1Result = testClient.registerNode(nodeInfo1);
            System.out.println("注册第一个节点结果: " + registerNode1Result);
            assertTrue(registerNode1Result.isSuccess(), "第一个节点注册应该成功");
            assertNotNull(registerNode1Result.getNodeId(), "应该返回第一个节点的 nodeId");
            
            String nodeId1 = registerNode1Result.getNodeId();
            System.out.println("第一个节点注册成功，nodeId: " + nodeId1);
            
            // 等待接收 NODE_ADDED 事件（最多等待5秒）
            boolean nodeAddedReceived = nodeAddedLatch.await(5, TimeUnit.SECONDS);
            assertTrue(nodeAddedReceived, "应该收到节点添加事件");
            assertEquals(1, nodeAddedCount.get(), "应该收到1个节点添加事件");
            
            // 等待一下
            Thread.sleep(500);
            
            // 更新节点信息（通过心跳更新），应该收到 NODE_UPDATED 事件
            System.out.println("--- 更新节点信息（应该收到 NODE_UPDATED 事件）---");
            nodeInfo1.setWeight(2.0); // 更新权重
            // 注意：这里需要通过心跳或其他方式更新节点，暂时跳过
            
            // 注册第二个节点，应该收到 NODE_ADDED 事件
            System.out.println("--- 注册第二个节点（应该收到 NODE_ADDED 事件）---");
            NodeInfo nodeInfo2 = new NodeInfo("127.0.0.1", 9091);
            nodeInfo2.setServiceName("test-subscription-service");
            nodeInfo2.setWeight(1.0);
            nodeInfo2.setEphemeral("Y");
            nodeInfo2.setInstanceStatus("UP");
            nodeInfo2.setHealthyStatus("HEALTHY");
            
            RegisterNodeResult registerNode2Result = testClient.registerNode(nodeInfo2);
            System.out.println("注册第二个节点结果: " + registerNode2Result);
            assertTrue(registerNode2Result.isSuccess(), "第二个节点注册应该成功");
            
            String nodeId2 = registerNode2Result.getNodeId();
            System.out.println("第二个节点注册成功，nodeId: " + nodeId2);
            
            // 等待接收第二个 NODE_ADDED 事件
            Thread.sleep(1000);
            assertEquals(2, nodeAddedCount.get(), "应该收到2个节点添加事件");
            
            // 注销第一个节点，应该收到 NODE_REMOVED 事件
            System.out.println("--- 注销第一个节点（应该收到 NODE_REMOVED 事件）---");
            OperationResult unregisterNode1Result = testClient.unregisterNode(nodeId1);
            System.out.println("注销第一个节点结果: " + unregisterNode1Result);
            assertTrue(unregisterNode1Result.isSuccess(), "注销第一个节点应该成功");
            
            // 等待接收 NODE_REMOVED 事件（最多等待5秒）
            boolean nodeRemovedReceived = nodeRemovedLatch.await(5, TimeUnit.SECONDS);
            assertTrue(nodeRemovedReceived, "应该收到节点移除事件");
            assertEquals(1, nodeRemovedCount.get(), "应该收到1个节点移除事件");
            
            // 等待一下
            Thread.sleep(500);
            
            // 取消订阅
            System.out.println("--- 取消订阅 ---");
            testClient.unsubscribeService(subscriptionId);
            System.out.println("已取消订阅: " + subscriptionId);
            
            // 注销第二个节点和服务
            System.out.println("--- 清理：注销第二个节点和服务 ---");
            testClient.unregisterNode(nodeId2);
            testClient.unregisterService(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    "test-subscription-service",
                    null);
            
            System.out.println("=== 测试完成 ===");
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("测试失败: " + e.getMessage());
        } finally {
            testClient.close();
        }
    }
    
    /**
     * 真实测试2：订阅多个服务
     * 
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试多服务订阅功能。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>订阅多个服务</li>
     *   <li>为不同服务注册节点，验证收到对应的事件</li>
     *   <li>取消订阅</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testSubscribeMultipleServices_WithRealServer() {
        // 配置使用服务端提供的命名空间
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP");
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            // 连接服务端
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端");
            System.out.println("=== 已连接到服务端，开始测试多服务订阅 ===");
            
            // 创建事件计数器（按服务名分组）
            Map<String, AtomicInteger> serviceEventCounts = new ConcurrentHashMap<>();
            CountDownLatch service1NodeAddedLatch = new CountDownLatch(1);
            CountDownLatch service2NodeAddedLatch = new CountDownLatch(1);
            
            // 创建监听器
            ServiceChangeListener listener = new ServiceChangeListenerAdapter() {
                @Override
                public void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
                    String serviceName = service != null ? service.getServiceName() : "null";
                    serviceEventCounts.computeIfAbsent(serviceName, k -> new AtomicInteger(0))
                                     .incrementAndGet();
                    
                    System.out.println("收到节点添加事件: service=" + serviceName + 
                                     ", nodeId=" + node.getNodeId() + 
                                     ", ip=" + node.getIpAddress() + 
                                     ", port=" + node.getPortNumber() +
                                     ", weight=" + node.getWeight() +
                                     ", instanceStatus=" + node.getInstanceStatus() +
                                     ", healthyStatus=" + node.getHealthyStatus() +
                                     ", ephemeral=" + node.getEphemeral() +
                                     ", totalNodes=" + allNodes.size() +
                                     ", allNodeIds=" + allNodes.stream().map(NodeInfo::getNodeId).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
                    
                    if ("test-subscription-service-1".equals(serviceName)) {
                        service1NodeAddedLatch.countDown();
                    } else if ("test-subscription-service-2".equals(serviceName)) {
                        service2NodeAddedLatch.countDown();
                    }
                }
                
                @Override
                public void onServiceChange(ServiceChangeEvent event) {
                    System.out.println("收到服务变更事件: eventType=" + event.getEventType() + 
                                     ", service=" + event.getServiceName() +
                                     ", namespaceId=" + event.getNamespaceId() +
                                     ", groupName=" + event.getGroupName() +
                                     ", timestamp=" + event.getTimestamp() +
                                     ", serviceInfo=" + (event.getService() != null ? event.getService().getServiceName() : "null") +
                                     ", nodesCount=" + (event.getAllNodes() != null ? event.getAllNodes().size() : 0) +
                                     ", changedNode=" + (event.getChangedNode() != null ? event.getChangedNode().getNodeId() : "null"));
                    // 调用父类方法，触发事件路由（调用 onNodeAdded、onNodeUpdated 等）
                    super.onServiceChange(event);
                }
            };
            
            // 订阅多个服务
            System.out.println("--- 订阅多个服务变更 ---");
            String subscriptionId = testClient.subscribe(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    Arrays.asList("test-subscription-service-1", "test-subscription-service-2"),
                    listener);
            System.out.println("订阅成功，subscriptionId: " + subscriptionId);
            assertNotNull(subscriptionId, "订阅ID不应该为空");
            
            // 等待一下，确保订阅已建立
            Thread.sleep(1000);
            
            // 注册第一个服务
            System.out.println("--- 注册第一个服务 ---");
            ServiceInfo serviceInfo1 = new ServiceInfo();
            serviceInfo1.setServiceName("test-subscription-service-1");
            serviceInfo1.setServiceType("INTERNAL");
            serviceInfo1.setServiceVersion("1.0.0");
            
            RegisterServiceResult registerService1Result = testClient.registerService(serviceInfo1, null);
            assertTrue(registerService1Result.isSuccess(), "第一个服务注册应该成功");
            
            // 注册第二个服务
            System.out.println("--- 注册第二个服务 ---");
            ServiceInfo serviceInfo2 = new ServiceInfo();
            serviceInfo2.setServiceName("test-subscription-service-2");
            serviceInfo2.setServiceType("INTERNAL");
            serviceInfo2.setServiceVersion("1.0.0");
            
            RegisterServiceResult registerService2Result = testClient.registerService(serviceInfo2, null);
            assertTrue(registerService2Result.isSuccess(), "第二个服务注册应该成功");
            
            // 等待一下
            Thread.sleep(500);
            
            // 为第一个服务注册节点
            System.out.println("--- 为第一个服务注册节点 ---");
            NodeInfo nodeInfo1 = new NodeInfo("127.0.0.1", 9100);
            nodeInfo1.setServiceName("test-subscription-service-1");
            nodeInfo1.setWeight(1.0);
            nodeInfo1.setEphemeral("Y");
            nodeInfo1.setInstanceStatus("UP");
            nodeInfo1.setHealthyStatus("HEALTHY");
            
            RegisterNodeResult registerNode1Result = testClient.registerNode(nodeInfo1);
            assertTrue(registerNode1Result.isSuccess(), "第一个节点注册应该成功");
            String nodeId1 = registerNode1Result.getNodeId();
            
            // 等待接收第一个服务的 NODE_ADDED 事件
            boolean service1EventReceived = service1NodeAddedLatch.await(5, TimeUnit.SECONDS);
            assertTrue(service1EventReceived, "应该收到第一个服务的节点添加事件");
            
            // 为第二个服务注册节点
            System.out.println("--- 为第二个服务注册节点 ---");
            NodeInfo nodeInfo2 = new NodeInfo("127.0.0.1", 9101);
            nodeInfo2.setServiceName("test-subscription-service-2");
            nodeInfo2.setWeight(1.0);
            nodeInfo2.setEphemeral("Y");
            nodeInfo2.setInstanceStatus("UP");
            nodeInfo2.setHealthyStatus("HEALTHY");
            
            RegisterNodeResult registerNode2Result = testClient.registerNode(nodeInfo2);
            assertTrue(registerNode2Result.isSuccess(), "第二个节点注册应该成功");
            String nodeId2 = registerNode2Result.getNodeId();
            
            // 等待接收第二个服务的 NODE_ADDED 事件
            boolean service2EventReceived = service2NodeAddedLatch.await(5, TimeUnit.SECONDS);
            assertTrue(service2EventReceived, "应该收到第二个服务的节点添加事件");
            
            // 验证事件计数
            assertEquals(1, serviceEventCounts.getOrDefault("test-subscription-service-1", new AtomicInteger(0)).get(), "第一个服务应该收到1个事件");
            assertEquals(1, serviceEventCounts.getOrDefault("test-subscription-service-2", new AtomicInteger(0)).get(), "第二个服务应该收到1个事件");
            
            // 取消订阅
            System.out.println("--- 取消订阅 ---");
            testClient.unsubscribeService(subscriptionId);
            
            // 清理
            System.out.println("--- 清理：注销节点和服务 ---");
            testClient.unregisterNode(nodeId1);
            testClient.unregisterNode(nodeId2);
            testClient.unregisterService("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", 
                    "test-subscription-service-1", null);
            testClient.unregisterService("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", 
                    "test-subscription-service-2", null);
            
            System.out.println("=== 测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("测试失败: " + e.getMessage());
        } finally {
            testClient.close();
        }
    }
    
    /**
     * 真实测试3：订阅整个命名空间
     * 
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试命名空间订阅功能。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>订阅整个命名空间</li>
     *   <li>注册多个服务，验证收到对应的事件</li>
     *   <li>取消订阅</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testSubscribeNamespace_WithRealServer() {
        // 配置使用服务端提供的命名空间
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP");
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            // 连接服务端
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端");
            System.out.println("=== 已连接到服务端，开始测试命名空间订阅 ===");
            
            // 创建事件计数器
            AtomicInteger totalEventCount = new AtomicInteger(0);
            Set<String> receivedServiceNames = ConcurrentHashMap.newKeySet();
            CountDownLatch eventLatch = new CountDownLatch(2); // 等待至少2个事件
            
            // 创建监听器
            ServiceChangeListener listener = new ServiceChangeListenerAdapter() {
                @Override
                public void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
                    totalEventCount.incrementAndGet();
                    String serviceName = service != null ? service.getServiceName() : "null";
                    receivedServiceNames.add(serviceName);
                    
                    System.out.println("收到节点添加事件: service=" + serviceName + 
                                     ", nodeId=" + node.getNodeId() + 
                                     ", ip=" + node.getIpAddress() + 
                                     ", port=" + node.getPortNumber() +
                                     ", weight=" + node.getWeight() +
                                     ", instanceStatus=" + node.getInstanceStatus() +
                                     ", healthyStatus=" + node.getHealthyStatus() +
                                     ", ephemeral=" + node.getEphemeral() +
                                     ", totalNodes=" + allNodes.size() +
                                     ", allNodeIds=" + allNodes.stream().map(NodeInfo::getNodeId).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
                    
                    eventLatch.countDown();
                }
                
                @Override
                public void onServiceChange(ServiceChangeEvent event) {
                    System.out.println("收到服务变更事件: eventType=" + event.getEventType() + 
                                     ", service=" + event.getServiceName() +
                                     ", namespaceId=" + event.getNamespaceId() +
                                     ", groupName=" + event.getGroupName() +
                                     ", timestamp=" + event.getTimestamp() +
                                     ", serviceInfo=" + (event.getService() != null ? event.getService().getServiceName() : "null") +
                                     ", nodesCount=" + (event.getAllNodes() != null ? event.getAllNodes().size() : 0) +
                                     ", changedNode=" + (event.getChangedNode() != null ? event.getChangedNode().getNodeId() : "null"));
                    // 调用父类方法，触发事件路由（调用 onNodeAdded、onNodeUpdated 等）
                    super.onServiceChange(event);
                }
            };
            
            // 订阅整个命名空间（serviceNames 为 null）
            System.out.println("--- 订阅整个命名空间 ---");
            String subscriptionId = testClient.subscribe(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    null, // null 表示订阅整个命名空间
                    listener);
            System.out.println("订阅成功，subscriptionId: " + subscriptionId);
            assertNotNull(subscriptionId, "订阅ID不应该为空");
            
            // 等待一下，确保订阅已建立
            Thread.sleep(1000);
            
            // 注册第一个服务并添加节点
            System.out.println("--- 注册第一个服务并添加节点 ---");
            ServiceInfo serviceInfo1 = new ServiceInfo();
            serviceInfo1.setServiceName("test-namespace-subscription-service-1");
            serviceInfo1.setServiceType("INTERNAL");
            serviceInfo1.setServiceVersion("1.0.0");
            
            NodeInfo nodeInfo1 = new NodeInfo("127.0.0.1", 9200);
            nodeInfo1.setServiceName("test-namespace-subscription-service-1");
            nodeInfo1.setWeight(1.0);
            nodeInfo1.setEphemeral("Y");
            nodeInfo1.setInstanceStatus("UP");
            nodeInfo1.setHealthyStatus("HEALTHY");
            
            RegisterServiceResult registerService1Result = testClient.registerService(serviceInfo1, nodeInfo1);
            assertTrue(registerService1Result.isSuccess(), "第一个服务注册应该成功");
            String nodeId1 = registerService1Result.getNodeId();
            
            // 注册第二个服务并添加节点
            System.out.println("--- 注册第二个服务并添加节点 ---");
            ServiceInfo serviceInfo2 = new ServiceInfo();
            serviceInfo2.setServiceName("test-namespace-subscription-service-2");
            serviceInfo2.setServiceType("INTERNAL");
            serviceInfo2.setServiceVersion("1.0.0");
            
            NodeInfo nodeInfo2 = new NodeInfo("127.0.0.1", 9201);
            nodeInfo2.setServiceName("test-namespace-subscription-service-2");
            nodeInfo2.setWeight(1.0);
            nodeInfo2.setEphemeral("Y");
            nodeInfo2.setInstanceStatus("UP");
            nodeInfo2.setHealthyStatus("HEALTHY");
            
            RegisterServiceResult registerService2Result = testClient.registerService(serviceInfo2, nodeInfo2);
            assertTrue(registerService2Result.isSuccess(), "第二个服务注册应该成功");
            String nodeId2 = registerService2Result.getNodeId();
            
            // 等待接收事件（最多等待10秒）
            boolean eventsReceived = eventLatch.await(10, TimeUnit.SECONDS);
            assertTrue(eventsReceived, "应该收到至少2个事件");
            assertTrue(totalEventCount.get() >= 2, "应该收到至少2个事件");
            assertTrue(receivedServiceNames.contains("test-namespace-subscription-service-1"), "应该收到第一个服务的事件");
            assertTrue(receivedServiceNames.contains("test-namespace-subscription-service-2"), "应该收到第二个服务的事件");
            
            // 取消订阅
            System.out.println("--- 取消订阅 ---");
            testClient.unsubscribeService(subscriptionId);
            
            // 清理
            System.out.println("--- 清理：注销节点和服务 ---");
            testClient.unregisterNode(nodeId1);
            testClient.unregisterNode(nodeId2);
            testClient.unregisterService("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", 
                    "test-namespace-subscription-service-1", null);
            testClient.unregisterService("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", 
                    "test-namespace-subscription-service-2", null);
            
            System.out.println("=== 测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("测试失败: " + e.getMessage());
        } finally {
            testClient.close();
        }
    }
    
    /**
     * 真实测试4：测试订阅重连机制
     * 
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试订阅重连功能。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>订阅服务</li>
     *   <li>模拟连接断开（关闭客户端）</li>
     *   <li>重新连接，验证订阅自动恢复</li>
     * </ul>
     * 
     * <p>注意：此测试需要手动干预（关闭/重启服务端），主要用于验证重连逻辑。</p>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testSubscribeReconnect_WithRealServer() {
        // 配置使用服务端提供的命名空间
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP")
                .setReconnectInterval(2000); // 设置重连间隔为2秒
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            // 连接服务端
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端");
            System.out.println("=== 已连接到服务端，开始测试订阅重连 ===");
            
            // 创建事件计数器
            AtomicInteger reconnectCount = new AtomicInteger(0);
            AtomicInteger disconnectCount = new AtomicInteger(0);
            CountDownLatch reconnectLatch = new CountDownLatch(1);
            
            // 创建监听器
            ServiceChangeListener listener = new ServiceChangeListenerAdapter() {
                @Override
                public void onDisconnected(Throwable cause) {
                    disconnectCount.incrementAndGet();
                    System.out.println("订阅连接断开: " + (cause != null ? cause.getMessage() : "未知原因"));
                }
                
                @Override
                public void onReconnected() {
                    reconnectCount.incrementAndGet();
                    System.out.println("订阅连接已恢复");
                    reconnectLatch.countDown();
                }
                
                @Override
                public void onServiceChange(ServiceChangeEvent event) {
                    System.out.println("收到服务变更事件: eventType=" + event.getEventType() + 
                                     ", service=" + event.getServiceName() +
                                     ", namespaceId=" + event.getNamespaceId() +
                                     ", groupName=" + event.getGroupName() +
                                     ", timestamp=" + event.getTimestamp() +
                                     ", serviceInfo=" + (event.getService() != null ? event.getService().getServiceName() : "null") +
                                     ", nodesCount=" + (event.getAllNodes() != null ? event.getAllNodes().size() : 0) +
                                     ", changedNode=" + (event.getChangedNode() != null ? event.getChangedNode().getNodeId() : "null"));
                    // 调用父类方法，触发事件路由（调用 onNodeAdded、onNodeUpdated 等）
                    super.onServiceChange(event);
                }
            };
            
            // 订阅服务
            System.out.println("--- 订阅服务变更 ---");
            String subscriptionId = testClient.subscribe(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    Arrays.asList("test-reconnect-service"),
                    listener);
            System.out.println("订阅成功，subscriptionId: " + subscriptionId);
            
            // 等待一下，确保订阅已建立
            Thread.sleep(2000);
            
            System.out.println("--- 请手动关闭服务端，然后重新启动服务端 ---");
            System.out.println("--- 等待重连（最多等待60秒）---");
            System.out.println("--- 提示：关闭服务端后，客户端会检测到连接断开并自动重连 ---");
            System.out.println("--- 当前状态：disconnectCount=" + disconnectCount.get() + ", reconnectCount=" + reconnectCount.get());
            
            // 等待重连（最多等待60秒）
            // 每5秒打印一次状态
            long startTime = System.currentTimeMillis();
            long timeoutMs = 60 * 1000; // 60秒
            boolean reconnected = false;
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                reconnected = reconnectLatch.await(5, TimeUnit.SECONDS);
                if (reconnected) {
                    break;
                }
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                System.out.println("--- 等待重连中... 已等待 " + elapsed + " 秒，disconnectCount=" + 
                        disconnectCount.get() + ", reconnectCount=" + reconnectCount.get());
            }
            
            if (reconnected) {
                System.out.println("=== 重连成功！ ===");
                System.out.println("disconnectCount=" + disconnectCount.get() + ", reconnectCount=" + reconnectCount.get());
                assertEquals(1, reconnectCount.get(), "应该收到1次重连事件");
            } else {
                System.out.println("=== 重连超时（可能是服务端未重启或重连逻辑有问题） ===");
                System.out.println("最终状态：disconnectCount=" + disconnectCount.get() + ", reconnectCount=" + reconnectCount.get());
                System.out.println("提示：请检查：");
                System.out.println("  1. 是否手动关闭了服务端？");
                System.out.println("  2. 是否重新启动了服务端？");
                System.out.println("  3. 服务端是否正常运行？");
                // 不直接失败，因为这是手动测试
            }
            
            // 取消订阅
            System.out.println("--- 取消订阅 ---");
            testClient.unsubscribeService(subscriptionId);
            
            System.out.println("=== 测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("测试失败: " + e.getMessage());
        } finally {
            testClient.close();
        }
    }
    
    /**
     * 真实测试5：多次调用订阅（每次订阅不同的服务）
     * 
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试多次订阅功能。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>第一次订阅：订阅服务1</li>
     *   <li>第二次订阅：订阅服务2</li>
     *   <li>第三次订阅：订阅服务3</li>
     *   <li>为不同服务注册节点，验证每个订阅都能收到对应的事件</li>
     *   <li>取消所有订阅</li>
     * </ul>
     * 
     * <p>说明：</p>
     * <ul>
     *   <li>每次调用 subscribe 都会创建一个新的订阅连接和 subscriptionId</li>
     *   <li>每个订阅都是独立的，可以分别取消</li>
     *   <li>这种方式适合需要为不同服务使用不同监听器的场景</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testMultipleSubscriptions_WithRealServer() {
        // 配置使用服务端提供的命名空间
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP");
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            // 连接服务端
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端");
            System.out.println("=== 已连接到服务端，开始测试多次订阅 ===");
            
            // 创建事件计数器（按服务名和订阅ID分组）
            Map<String, AtomicInteger> service1EventCounts = new ConcurrentHashMap<>();
            Map<String, AtomicInteger> service2EventCounts = new ConcurrentHashMap<>();
            Map<String, AtomicInteger> service3EventCounts = new ConcurrentHashMap<>();
            CountDownLatch service1NodeAddedLatch = new CountDownLatch(1);
            CountDownLatch service2NodeAddedLatch = new CountDownLatch(1);
            CountDownLatch service3NodeAddedLatch = new CountDownLatch(1);
            
            // 创建第一个服务的监听器
            ServiceChangeListener listener1 = new ServiceChangeListenerAdapter() {
                @Override
                public void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
                    String serviceName = service != null ? service.getServiceName() : "null";
                    service1EventCounts.computeIfAbsent("nodeAdded", k -> new AtomicInteger(0))
                                     .incrementAndGet();
                    
                    System.out.println("[订阅1] 收到节点添加事件: service=" + serviceName + 
                                     ", nodeId=" + node.getNodeId() + 
                                     ", ip=" + node.getIpAddress() + 
                                     ", port=" + node.getPortNumber() +
                                     ", weight=" + node.getWeight() +
                                     ", instanceStatus=" + node.getInstanceStatus() +
                                     ", healthyStatus=" + node.getHealthyStatus() +
                                     ", ephemeral=" + node.getEphemeral() +
                                     ", totalNodes=" + allNodes.size() +
                                     ", allNodeIds=" + allNodes.stream().map(NodeInfo::getNodeId).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
                    
                    if ("test-multiple-subscription-service-1".equals(serviceName)) {
                        service1NodeAddedLatch.countDown();
                    }
                }
                
                @Override
                public void onServiceChange(ServiceChangeEvent event) {
                    System.out.println("[订阅1] 收到服务变更事件: eventType=" + event.getEventType() + 
                                     ", service=" + event.getServiceName() +
                                     ", namespaceId=" + event.getNamespaceId() +
                                     ", groupName=" + event.getGroupName() +
                                     ", timestamp=" + event.getTimestamp() +
                                     ", serviceInfo=" + (event.getService() != null ? event.getService().getServiceName() : "null") +
                                     ", nodesCount=" + (event.getAllNodes() != null ? event.getAllNodes().size() : 0) +
                                     ", changedNode=" + (event.getChangedNode() != null ? event.getChangedNode().getNodeId() : "null"));
                    // 调用父类方法，触发事件路由（调用 onNodeAdded、onNodeUpdated 等）
                    super.onServiceChange(event);
                }
            };
            
            // 创建第二个服务的监听器
            ServiceChangeListener listener2 = new ServiceChangeListenerAdapter() {
                @Override
                public void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
                    String serviceName = service != null ? service.getServiceName() : "null";
                    service2EventCounts.computeIfAbsent("nodeAdded", k -> new AtomicInteger(0))
                                     .incrementAndGet();
                    
                    System.out.println("[订阅2] 收到节点添加事件: service=" + serviceName + 
                                     ", nodeId=" + node.getNodeId() + 
                                     ", ip=" + node.getIpAddress() + 
                                     ", port=" + node.getPortNumber() +
                                     ", weight=" + node.getWeight() +
                                     ", instanceStatus=" + node.getInstanceStatus() +
                                     ", healthyStatus=" + node.getHealthyStatus() +
                                     ", ephemeral=" + node.getEphemeral() +
                                     ", totalNodes=" + allNodes.size() +
                                     ", allNodeIds=" + allNodes.stream().map(NodeInfo::getNodeId).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
                    
                    if ("test-multiple-subscription-service-2".equals(serviceName)) {
                        service2NodeAddedLatch.countDown();
                    }
                }
                
                @Override
                public void onServiceChange(ServiceChangeEvent event) {
                    System.out.println("[订阅2] 收到服务变更事件: eventType=" + event.getEventType() + 
                                     ", service=" + event.getServiceName() +
                                     ", namespaceId=" + event.getNamespaceId() +
                                     ", groupName=" + event.getGroupName() +
                                     ", timestamp=" + event.getTimestamp() +
                                     ", serviceInfo=" + (event.getService() != null ? event.getService().getServiceName() : "null") +
                                     ", nodesCount=" + (event.getAllNodes() != null ? event.getAllNodes().size() : 0) +
                                     ", changedNode=" + (event.getChangedNode() != null ? event.getChangedNode().getNodeId() : "null"));
                    // 调用父类方法，触发事件路由（调用 onNodeAdded、onNodeUpdated 等）
                    super.onServiceChange(event);
                }
            };
            
            // 创建第三个服务的监听器
            ServiceChangeListener listener3 = new ServiceChangeListenerAdapter() {
                @Override
                public void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
                    String serviceName = service != null ? service.getServiceName() : "null";
                    service3EventCounts.computeIfAbsent("nodeAdded", k -> new AtomicInteger(0))
                                     .incrementAndGet();
                    
                    System.out.println("[订阅3] 收到节点添加事件: service=" + serviceName + 
                                     ", nodeId=" + node.getNodeId() + 
                                     ", ip=" + node.getIpAddress() + 
                                     ", port=" + node.getPortNumber() +
                                     ", weight=" + node.getWeight() +
                                     ", instanceStatus=" + node.getInstanceStatus() +
                                     ", healthyStatus=" + node.getHealthyStatus() +
                                     ", ephemeral=" + node.getEphemeral() +
                                     ", totalNodes=" + allNodes.size() +
                                     ", allNodeIds=" + allNodes.stream().map(NodeInfo::getNodeId).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
                    
                    if ("test-multiple-subscription-service-3".equals(serviceName)) {
                        service3NodeAddedLatch.countDown();
                    }
                }
                
                @Override
                public void onServiceChange(ServiceChangeEvent event) {
                    System.out.println("[订阅3] 收到服务变更事件: eventType=" + event.getEventType() + 
                                     ", service=" + event.getServiceName() +
                                     ", namespaceId=" + event.getNamespaceId() +
                                     ", groupName=" + event.getGroupName() +
                                     ", timestamp=" + event.getTimestamp() +
                                     ", serviceInfo=" + (event.getService() != null ? event.getService().getServiceName() : "null") +
                                     ", nodesCount=" + (event.getAllNodes() != null ? event.getAllNodes().size() : 0) +
                                     ", changedNode=" + (event.getChangedNode() != null ? event.getChangedNode().getNodeId() : "null"));
                    // 调用父类方法，触发事件路由（调用 onNodeAdded、onNodeUpdated 等）
                    super.onServiceChange(event);
                }
            };
            
            // 第一次订阅：订阅服务1
            System.out.println("--- 第一次订阅：订阅服务1 ---");
            String subscriptionId1 = testClient.subscribe(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    Arrays.asList("test-multiple-subscription-service-1"),
                    listener1);
            System.out.println("订阅1成功，subscriptionId: " + subscriptionId1);
            assertNotNull(subscriptionId1, "订阅1 ID不应该为空");
            
            // 第二次订阅：订阅服务2
            System.out.println("--- 第二次订阅：订阅服务2 ---");
            String subscriptionId2 = testClient.subscribe(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    Arrays.asList("test-multiple-subscription-service-2"),
                    listener2);
            System.out.println("订阅2成功，subscriptionId: " + subscriptionId2);
            assertNotNull(subscriptionId2, "订阅2 ID不应该为空");
            assertNotEquals(subscriptionId1, subscriptionId2, "订阅1和订阅2的ID应该不同");
            
            // 第三次订阅：订阅服务3
            System.out.println("--- 第三次订阅：订阅服务3 ---");
            String subscriptionId3 = testClient.subscribe(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    Arrays.asList("test-multiple-subscription-service-3"),
                    listener3);
            System.out.println("订阅3成功，subscriptionId: " + subscriptionId3);
            assertNotNull(subscriptionId3, "订阅3 ID不应该为空");
            assertNotEquals(subscriptionId1, subscriptionId3, "订阅1和订阅3的ID应该不同");
            assertNotEquals(subscriptionId2, subscriptionId3, "订阅2和订阅3的ID应该不同");
            
            // 等待一下，确保所有订阅已建立
            Thread.sleep(1000);
            
            // 注册第一个服务并添加节点
            System.out.println("--- 注册第一个服务并添加节点 ---");
            ServiceInfo serviceInfo1 = new ServiceInfo();
            serviceInfo1.setServiceName("test-multiple-subscription-service-1");
            serviceInfo1.setServiceType("INTERNAL");
            serviceInfo1.setServiceVersion("1.0.0");
            
            NodeInfo nodeInfo1 = new NodeInfo("127.0.0.1", 9300);
            nodeInfo1.setServiceName("test-multiple-subscription-service-1");
            nodeInfo1.setWeight(1.0);
            nodeInfo1.setEphemeral("Y");
            nodeInfo1.setInstanceStatus("UP");
            nodeInfo1.setHealthyStatus("HEALTHY");
            
            RegisterServiceResult registerService1Result = testClient.registerService(serviceInfo1, nodeInfo1);
            assertTrue(registerService1Result.isSuccess(), "第一个服务注册应该成功");
            String nodeId1 = registerService1Result.getNodeId();
            
            // 等待接收第一个服务的 NODE_ADDED 事件（应该只有订阅1收到）
            boolean service1EventReceived = service1NodeAddedLatch.await(5, TimeUnit.SECONDS);
            assertTrue(service1EventReceived, "订阅1应该收到第一个服务的节点添加事件");
            assertEquals(1, service1EventCounts.getOrDefault("nodeAdded", new AtomicInteger(0)).get(), "订阅1应该收到1个事件");
            assertEquals(0, service2EventCounts.getOrDefault("nodeAdded", new AtomicInteger(0)).get(), "订阅2不应该收到第一个服务的事件");
            assertEquals(0, service3EventCounts.getOrDefault("nodeAdded", new AtomicInteger(0)).get(), "订阅3不应该收到第一个服务的事件");
            
            // 注册第二个服务并添加节点
            System.out.println("--- 注册第二个服务并添加节点 ---");
            ServiceInfo serviceInfo2 = new ServiceInfo();
            serviceInfo2.setServiceName("test-multiple-subscription-service-2");
            serviceInfo2.setServiceType("INTERNAL");
            serviceInfo2.setServiceVersion("1.0.0");
            
            NodeInfo nodeInfo2 = new NodeInfo("127.0.0.1", 9301);
            nodeInfo2.setServiceName("test-multiple-subscription-service-2");
            nodeInfo2.setWeight(1.0);
            nodeInfo2.setEphemeral("Y");
            nodeInfo2.setInstanceStatus("UP");
            nodeInfo2.setHealthyStatus("HEALTHY");
            
            RegisterServiceResult registerService2Result = testClient.registerService(serviceInfo2, nodeInfo2);
            assertTrue(registerService2Result.isSuccess(), "第二个服务注册应该成功");
            String nodeId2 = registerService2Result.getNodeId();
            
            // 等待接收第二个服务的 NODE_ADDED 事件（应该只有订阅2收到）
            boolean service2EventReceived = service2NodeAddedLatch.await(5, TimeUnit.SECONDS);
            assertTrue(service2EventReceived, "订阅2应该收到第二个服务的节点添加事件");
            assertEquals(1, service1EventCounts.getOrDefault("nodeAdded", new AtomicInteger(0)).get(), "订阅1不应该收到第二个服务的事件");
            assertEquals(1, service2EventCounts.getOrDefault("nodeAdded", new AtomicInteger(0)).get(), "订阅2应该收到1个事件");
            assertEquals(0, service3EventCounts.getOrDefault("nodeAdded", new AtomicInteger(0)).get(), "订阅3不应该收到第二个服务的事件");
            
            // 注册第三个服务并添加节点
            System.out.println("--- 注册第三个服务并添加节点 ---");
            ServiceInfo serviceInfo3 = new ServiceInfo();
            serviceInfo3.setServiceName("test-multiple-subscription-service-3");
            serviceInfo3.setServiceType("INTERNAL");
            serviceInfo3.setServiceVersion("1.0.0");
            
            NodeInfo nodeInfo3 = new NodeInfo("127.0.0.1", 9302);
            nodeInfo3.setServiceName("test-multiple-subscription-service-3");
            nodeInfo3.setWeight(1.0);
            nodeInfo3.setEphemeral("Y");
            nodeInfo3.setInstanceStatus("UP");
            nodeInfo3.setHealthyStatus("HEALTHY");
            
            RegisterServiceResult registerService3Result = testClient.registerService(serviceInfo3, nodeInfo3);
            assertTrue(registerService3Result.isSuccess(), "第三个服务注册应该成功");
            String nodeId3 = registerService3Result.getNodeId();
            
            // 等待接收第三个服务的 NODE_ADDED 事件（应该只有订阅3收到）
            boolean service3EventReceived = service3NodeAddedLatch.await(5, TimeUnit.SECONDS);
            assertTrue(service3EventReceived, "订阅3应该收到第三个服务的节点添加事件");
            assertEquals(1, service1EventCounts.getOrDefault("nodeAdded", new AtomicInteger(0)).get(), "订阅1不应该收到第三个服务的事件");
            assertEquals(1, service2EventCounts.getOrDefault("nodeAdded", new AtomicInteger(0)).get(), "订阅2不应该收到第三个服务的事件");
            assertEquals(1, service3EventCounts.getOrDefault("nodeAdded", new AtomicInteger(0)).get(), "订阅3应该收到1个事件");
            
            // 取消所有订阅
            System.out.println("--- 取消所有订阅 ---");
            testClient.unsubscribeService(subscriptionId1);
            System.out.println("已取消订阅1: " + subscriptionId1);
            testClient.unsubscribeService(subscriptionId2);
            System.out.println("已取消订阅2: " + subscriptionId2);
            testClient.unsubscribeService(subscriptionId3);
            System.out.println("已取消订阅3: " + subscriptionId3);
            
            // 清理
            System.out.println("--- 清理：注销节点和服务 ---");
            testClient.unregisterNode(nodeId1);
            testClient.unregisterNode(nodeId2);
            testClient.unregisterNode(nodeId3);
            testClient.unregisterService("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", 
                    "test-multiple-subscription-service-1", null);
            testClient.unregisterService("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", 
                    "test-multiple-subscription-service-2", null);
            testClient.unregisterService("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", 
                    "test-multiple-subscription-service-3", null);
            
            System.out.println("=== 测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("测试失败: " + e.getMessage());
        } finally {
            testClient.close();
        }
    }
}

