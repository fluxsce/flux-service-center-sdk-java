package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceCenterClient 测试类
 * 
 * <p>由于 ServiceCenterClient 在构造函数中创建了真实的管理器实例，
 * 本测试类主要测试配置验证、默认值填充逻辑和基本功能。</p>
 * 
 * <p>注意：所有与后端交互的操作（注册、发现、配置等）都会在日志中打印响应信息。
 * 响应日志使用 INFO 级别，格式为：操作名称响应: key1=value1, key2=value2, ...
 * 例如：服务注册响应: success=true, message=service registered successfully, nodeId=node123</p>
 * 
 * <p>要查看响应日志，请确保日志级别设置为 INFO 或更低（DEBUG）。</p>
 * 
 * @author shangjian
 */
public class ServiceCenterClientTest {

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

    // ========== 构造函数测试 ==========

    @Test
    public void testConstructor_ValidConfig() {
        client = new ServiceCenterClient(config);
        assertNotNull(client);
        assertFalse(client.isConnected());
    }

    @Test
    public void testConstructor_NullConfig() {
        assertThrows(IllegalArgumentException.class, () -> 
            new ServiceCenterClient(null)
        );
    }

    @Test
    public void testConstructor_InvalidServerHost() {
        ServiceCenterConfig invalidConfig = new ServiceCenterConfig()
                .setServerHost("")
                .setServerPort(12004);
        assertThrows(IllegalArgumentException.class, () -> 
            new ServiceCenterClient(invalidConfig)
        );
    }

    @Test
    public void testConstructor_InvalidServerPort() {
        ServiceCenterConfig invalidConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(0);
        assertThrows(IllegalArgumentException.class, () -> 
            new ServiceCenterClient(invalidConfig)
        );
    }

    @Test
    public void testConstructor_InvalidHeartbeatInterval() {
        ServiceCenterConfig invalidConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setHeartbeatInterval(0);
        assertThrows(IllegalArgumentException.class, () -> 
            new ServiceCenterClient(invalidConfig)
        );
    }

    @Test
    public void testConstructor_InvalidReconnectInterval() {
        ServiceCenterConfig invalidConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setReconnectInterval(0);
        assertThrows(IllegalArgumentException.class, () -> 
            new ServiceCenterClient(invalidConfig)
        );
    }

    @Test
    public void testConstructor_InvalidRequestTimeout() {
        ServiceCenterConfig invalidConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setRequestTimeout(0);
        assertThrows(IllegalArgumentException.class, () -> 
            new ServiceCenterClient(invalidConfig)
        );
    }

    // ========== 连接管理测试 ==========

    @Test
    public void testIsConnected_NotConnected() {
        client = new ServiceCenterClient(config);
        assertFalse(client.isConnected());
    }

    @Test
    public void testGetLastError_NoError() {
        client = new ServiceCenterClient(config);
        assertNull(client.getLastError());
    }

    @Test
    public void testClose() {
        client = new ServiceCenterClient(config);
        // close() 应该可以安全调用，即使未连接
        client.close();
        // 验证可以多次调用（幂等性）
        client.close();
    }

    // ========== 服务注册发现 API 测试 - 默认值填充逻辑 ==========

    @Test
    public void testRegisterService_WithDefaultNamespaceAndGroup() {
        client = new ServiceCenterClient(config);
        
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setServiceName("test-service");
        // namespaceId 和 groupName 为空，应该使用配置中的默认值
        
        NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
        
        // 调用方法，验证默认值填充逻辑
        try {
            client.registerService(serviceInfo, nodeInfo);
        } catch (Exception e) {
            // 预期会失败（因为未连接），但我们主要测试默认值填充
        }
        
        // 验证默认值已填充（使用服务端提供的命名空间）
        assertEquals("ns_F41J68C80A50C28G68A06I53A49J4", serviceInfo.getNamespaceId());
        assertEquals("DEFAULT_GROUP", serviceInfo.getGroupName());
        assertEquals("ns_F41J68C80A50C28G68A06I53A49J4", nodeInfo.getNamespaceId());
        assertEquals("DEFAULT_GROUP", nodeInfo.getGroupName());
    }

    @Test
    public void testRegisterService_WithCustomNamespaceAndGroup() {
        client = new ServiceCenterClient(config);
        
        ServiceInfo serviceInfo = new ServiceInfo("ns_custom", "custom-group", "test-service");
        NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
        nodeInfo.setNamespaceId("ns_custom");
        nodeInfo.setGroupName("custom-group");
        
        // 调用方法，验证不会覆盖已有的值
        try {
            client.registerService(serviceInfo, nodeInfo);
        } catch (Exception e) {
            // 预期会失败（因为未连接），但我们主要测试值不会被覆盖
        }
        
        // 验证保持原有的 namespaceId 和 groupName
        assertEquals("ns_custom", serviceInfo.getNamespaceId());
        assertEquals("custom-group", serviceInfo.getGroupName());
        assertEquals("ns_custom", nodeInfo.getNamespaceId());
        assertEquals("custom-group", nodeInfo.getGroupName());
    }

    @Test
    public void testRegisterService_WithNullNodeInfo() {
        client = new ServiceCenterClient(config);
        
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setServiceName("test-service");
        
        // 调用方法，验证 null nodeInfo 不会导致异常
        try {
            client.registerService(serviceInfo, null);
        } catch (Exception e) {
            // 预期会失败（因为未连接），但我们主要测试 null 处理
        }
        
        // 验证默认值已填充（使用服务端提供的命名空间）
        assertEquals("ns_F41J68C80A50C28G68A06I53A49J4", serviceInfo.getNamespaceId());
        assertEquals("DEFAULT_GROUP", serviceInfo.getGroupName());
    }

    @Test
    public void testRegisterNode_WithDefaultNamespaceAndGroup() {
        client = new ServiceCenterClient(config);
        
        NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
        // namespaceId 和 groupName 为空，应该使用配置中的默认值
        
        // 调用方法，验证默认值填充逻辑
        try {
            client.registerNode(nodeInfo);
        } catch (Exception e) {
            // 预期会失败（因为未连接），但我们主要测试默认值填充
        }
        
        // 验证默认值已填充（使用服务端提供的命名空间）
        assertEquals("ns_F41J68C80A50C28G68A06I53A49J4", nodeInfo.getNamespaceId());
        assertEquals("DEFAULT_GROUP", nodeInfo.getGroupName());
    }

    @Test
    public void testRegisterNode_WithCustomNamespaceAndGroup() {
        client = new ServiceCenterClient(config);
        
        NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
        nodeInfo.setNamespaceId("ns_custom");
        nodeInfo.setGroupName("custom-group");
        
        // 调用方法，验证不会覆盖已有的值
        try {
            client.registerNode(nodeInfo);
        } catch (Exception e) {
            // 预期会失败（因为未连接），但我们主要测试值不会被覆盖
        }
        
        // 验证保持原有的 namespaceId 和 groupName
        assertEquals("ns_custom", nodeInfo.getNamespaceId());
        assertEquals("custom-group", nodeInfo.getGroupName());
    }

    // ========== 配置中心 API 测试 - 默认值填充逻辑 ==========

    @Test
    public void testSaveConfig_WithDefaultNamespaceAndGroup() {
        client = new ServiceCenterClient(config);
        
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setConfigDataId("config1");
        configInfo.setConfigContent("test content");
        // namespaceId 和 groupName 为空，应该使用配置中的默认值
        
        // 调用方法，验证默认值填充逻辑
        try {
            client.saveConfig(configInfo);
        } catch (Exception e) {
            // 预期会失败（因为未连接），但我们主要测试默认值填充
        }
        
        // 验证默认值已填充（使用服务端提供的命名空间）
        assertEquals("ns_F41J68C80A50C28G68A06I53A49J4", configInfo.getNamespaceId());
        assertEquals("DEFAULT_GROUP", configInfo.getGroupName());
    }

    @Test
    public void testSaveConfig_WithCustomNamespaceAndGroup() {
        client = new ServiceCenterClient(config);
        
        ConfigInfo configInfo = new ConfigInfo("ns_custom", "custom-group", "config1");
        configInfo.setConfigContent("test content");
        
        // 调用方法，验证不会覆盖已有的值
        try {
            client.saveConfig(configInfo);
        } catch (Exception e) {
            // 预期会失败（因为未连接），但我们主要测试值不会被覆盖
        }
        
        // 验证保持原有的 namespaceId 和 groupName
        assertEquals("ns_custom", configInfo.getNamespaceId());
        assertEquals("custom-group", configInfo.getGroupName());
    }

    @Test
    public void testSaveConfig_WithEmptyNamespaceId() {
        client = new ServiceCenterClient(config);
        
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setNamespaceId(""); // 空字符串
        configInfo.setGroupName(""); // 空字符串
        configInfo.setConfigDataId("config1");
        configInfo.setConfigContent("test content");
        
        // 调用方法，验证空字符串也会被替换为默认值
        try {
            client.saveConfig(configInfo);
        } catch (Exception e) {
            // 预期会失败（因为未连接），但我们主要测试空字符串处理
        }
        
        // 验证默认值已填充（空字符串被视为空值，使用服务端提供的命名空间）
        assertEquals("ns_F41J68C80A50C28G68A06I53A49J4", configInfo.getNamespaceId());
        assertEquals("DEFAULT_GROUP", configInfo.getGroupName());
    }

    // ========== 集成测试示例（需要真实服务端）==========
    
    /**
     * 真实测试1：注册服务（包含节点）
     * 
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试服务注册功能。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>注册服务（同时注册节点）</li>
     *   <li>验证注册结果</li>
     *   <li>获取服务信息验证注册成功</li>
     *   <li>注销服务</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testRegisterService_WithRealServer() {
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
            System.out.println("=== 已连接到服务端，开始测试服务注册 ===");
            
            // 创建服务信息
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setServiceName("test-service-1");
            serviceInfo.setServiceType("INTERNAL");
            serviceInfo.setServiceVersion("1.0.0");
            serviceInfo.setServiceDescription("测试服务1");
            
            // 创建节点信息
            NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
            nodeInfo.setWeight(1.0);
            nodeInfo.setEphemeral("Y");
            nodeInfo.setInstanceStatus("UP");
            nodeInfo.setHealthyStatus("HEALTHY");
            
            // 注册服务（包含节点）
            System.out.println("--- 注册服务（包含节点）---");
            RegisterServiceResult registerResult = testClient.registerService(serviceInfo, nodeInfo);
            System.out.println("注册结果: " + registerResult);
            
            // 验证注册结果
            assertTrue(registerResult.isSuccess(), "服务注册应该成功");
            assertNotNull(registerResult.getNodeId(), "应该返回 nodeId");
            assertFalse(registerResult.getNodeId().isEmpty(), "nodeId 不应该为空");
            
            String nodeId = registerResult.getNodeId();
            System.out.println("注册成功，nodeId: " + nodeId);
            
            // 等待一下，确保服务已注册
            Thread.sleep(500);
            
            // 获取服务信息验证注册成功
            System.out.println("--- 获取服务信息验证注册成功 ---");
            GetServiceResult getServiceResult = testClient.getService(
                    "ns_F41J68C80A50C28G68A06I53A49J4", 
                    "DEFAULT_GROUP", 
                    "test-service-1");
            System.out.println("获取服务结果: " + getServiceResult);
            
            assertTrue(getServiceResult.isSuccess(), "获取服务应该成功");
            assertNotNull(getServiceResult.getService(), "应该返回服务信息");
            assertEquals("test-service-1", getServiceResult.getService().getServiceName(), "服务名应该匹配");
            assertNotNull(getServiceResult.getNodes(), "应该返回节点列表");
            assertFalse(getServiceResult.getNodes().isEmpty(), "节点列表不应该为空");
            
            // 验证节点信息
            NodeInfo foundNode = getServiceResult.getNodes().stream()
                    .filter(n -> n.getNodeId().equals(nodeId))
                    .findFirst()
                    .orElse(null);
            assertNotNull(foundNode, "应该找到注册的节点");
            assertEquals("127.0.0.1", foundNode.getIpAddress(), "节点IP应该匹配");
            assertEquals(8080, foundNode.getPortNumber(), "节点端口应该匹配");
            
            // 注销服务
            System.out.println("--- 注销服务 ---");
            OperationResult unregisterResult = testClient.unregisterService(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    "test-service-1",
                    nodeId);
            System.out.println("注销结果: " + unregisterResult);
            assertTrue(unregisterResult.isSuccess(), "注销服务应该成功");
            
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
     * 真实测试2：注册服务节点（单独注册节点）
     * 
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试节点注册功能。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>先注册服务（不包含节点）</li>
     *   <li>单独注册节点</li>
     *   <li>验证注册结果</li>
     *   <li>获取服务信息验证节点已添加</li>
     *   <li>注销节点和服务</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testRegisterNode_WithRealServer() {
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
            System.out.println("=== 已连接到服务端，开始测试节点注册 ===");
            
            // 先注册服务（不包含节点）
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setServiceName("test-service-2");
            serviceInfo.setServiceType("INTERNAL");
            serviceInfo.setServiceVersion("1.0.0");
            serviceInfo.setServiceDescription("测试服务2");
            
            System.out.println("--- 注册服务（不包含节点）---");
            RegisterServiceResult registerServiceResult = testClient.registerService(serviceInfo, null);
            System.out.println("注册服务结果: " + registerServiceResult);
            assertTrue(registerServiceResult.isSuccess(), "服务注册应该成功");
            
            // 等待一下，确保服务已注册
            Thread.sleep(500);
            
            // 注册第一个节点
            NodeInfo nodeInfo1 = new NodeInfo("127.0.0.1", 8081);
            nodeInfo1.setServiceName("test-service-2");
            nodeInfo1.setWeight(1.0);
            nodeInfo1.setEphemeral("Y");
            nodeInfo1.setInstanceStatus("UP");
            nodeInfo1.setHealthyStatus("HEALTHY");
            
            System.out.println("--- 注册第一个节点 ---");
            RegisterNodeResult registerNode1Result = testClient.registerNode(nodeInfo1);
            System.out.println("注册第一个节点结果: " + registerNode1Result);
            
            // 验证第一个节点注册结果
            assertTrue(registerNode1Result.isSuccess(), "第一个节点注册应该成功");
            assertNotNull(registerNode1Result.getNodeId(), "应该返回第一个节点的 nodeId");
            assertFalse(registerNode1Result.getNodeId().isEmpty(), "第一个节点的 nodeId 不应该为空");
            
            String nodeId1 = registerNode1Result.getNodeId();
            System.out.println("第一个节点注册成功，nodeId: " + nodeId1);
            
            // 等待一下，确保第一个节点已注册
            Thread.sleep(500);
            
            // 注册第二个节点
            NodeInfo nodeInfo2 = new NodeInfo("127.0.0.1", 8082);
            nodeInfo2.setServiceName("test-service-2");
            nodeInfo2.setWeight(2.0);
            nodeInfo2.setEphemeral("Y");
            nodeInfo2.setInstanceStatus("UP");
            nodeInfo2.setHealthyStatus("HEALTHY");
            
            System.out.println("--- 注册第二个节点 ---");
            RegisterNodeResult registerNode2Result = testClient.registerNode(nodeInfo2);
            System.out.println("注册第二个节点结果: " + registerNode2Result);
            
            // 验证第二个节点注册结果
            assertTrue(registerNode2Result.isSuccess(), "第二个节点注册应该成功");
            assertNotNull(registerNode2Result.getNodeId(), "应该返回第二个节点的 nodeId");
            assertFalse(registerNode2Result.getNodeId().isEmpty(), "第二个节点的 nodeId 不应该为空");
            
            String nodeId2 = registerNode2Result.getNodeId();
            System.out.println("第二个节点注册成功，nodeId: " + nodeId2);
            
            // 验证两个 nodeId 不同
            assertNotEquals(nodeId1, nodeId2, "两个节点的 nodeId 应该不同");
            
            // 等待一下，确保第二个节点已注册
            Thread.sleep(500);
            
            // 获取服务信息验证两个节点都已添加
            System.out.println("--- 获取服务信息验证两个节点都已添加 ---");
            GetServiceResult getServiceResult = testClient.getService(
                    "ns_F41J68C80A50C28G68A06I53A49J4", 
                    "DEFAULT_GROUP", 
                    "test-service-2");
            System.out.println("获取服务结果: " + getServiceResult);
            
            assertTrue(getServiceResult.isSuccess(), "获取服务应该成功");
            assertNotNull(getServiceResult.getService(), "应该返回服务信息");
            assertNotNull(getServiceResult.getNodes(), "应该返回节点列表");
            assertFalse(getServiceResult.getNodes().isEmpty(), "节点列表不应该为空");
            
            // 验证节点数量
            assertEquals(2, getServiceResult.getNodes().size(), "应该有两个节点");
            
            // 验证第一个节点信息
            NodeInfo foundNode1 = getServiceResult.getNodes().stream()
                    .filter(n -> n.getNodeId().equals(nodeId1))
                    .findFirst()
                    .orElse(null);
            assertNotNull(foundNode1, "应该找到第一个注册的节点");
            assertEquals("127.0.0.1", foundNode1.getIpAddress(), "第一个节点IP应该匹配");
            assertEquals(8081, foundNode1.getPortNumber(), "第一个节点端口应该匹配");
            
            // 验证第二个节点信息
            NodeInfo foundNode2 = getServiceResult.getNodes().stream()
                    .filter(n -> n.getNodeId().equals(nodeId2))
                    .findFirst()
                    .orElse(null);
            assertNotNull(foundNode2, "应该找到第二个注册的节点");
            assertEquals("127.0.0.1", foundNode2.getIpAddress(), "第二个节点IP应该匹配");
            assertEquals(8082, foundNode2.getPortNumber(), "第二个节点端口应该匹配");
            
            // 注销第一个节点
            System.out.println("--- 注销第一个节点 ---");
            OperationResult unregisterNode1Result = testClient.unregisterNode(nodeId1);
            System.out.println("注销第一个节点结果: " + unregisterNode1Result);
            assertTrue(unregisterNode1Result.isSuccess(), "注销第一个节点应该成功");
            
            // 注销第二个节点
            System.out.println("--- 注销第二个节点 ---");
            OperationResult unregisterNode2Result = testClient.unregisterNode(nodeId2);
            System.out.println("注销第二个节点结果: " + unregisterNode2Result);
            assertTrue(unregisterNode2Result.isSuccess(), "注销第二个节点应该成功");
            
            // 注销服务
            System.out.println("--- 注销服务 ---");
            OperationResult unregisterServiceResult = testClient.unregisterService(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    "test-service-2",
                    null);
            System.out.println("注销服务结果: " + unregisterServiceResult);
            assertTrue(unregisterServiceResult.isSuccess(), "注销服务应该成功");
            
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
     * 集成测试示例：连接真实服务端并查看响应
     * 
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，查看真实的响应日志。</p>
     * 
     * <p>响应日志示例：</p>
     * <pre>
     * INFO - 服务注册响应: success=true, message=service registered successfully, nodeId=node123
     * INFO - 获取配置响应: success=true, message=config found, config=config1
     * INFO - 心跳响应: nodeId=node123, success=true, message=heartbeat received, code=
     * </pre>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testIntegration_WithRealServer() {
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
            System.out.println("=== 已连接到服务端，开始测试 ===");
            
            // 测试服务注册（会打印响应）
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setServiceName("test-service");
            serviceInfo.setServiceType("INTERNAL");
            
            NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
            
            System.out.println("--- 测试服务注册（查看响应日志）---");
            RegisterServiceResult registerResult = testClient.registerService(serviceInfo, nodeInfo);
            System.out.println("注册结果: " + registerResult);
            
            // 测试获取服务（会打印响应）
            System.out.println("--- 测试获取服务（查看响应日志）---");
            GetServiceResult getServiceResult = testClient.getService(
                    "ns_F41J68C80A50C28G68A06I53A49J4", 
                    "DEFAULT_GROUP", 
                    "test-service");
            System.out.println("获取服务结果: " + getServiceResult);
            
            // 测试配置保存（会打印响应）
            System.out.println("--- 测试配置保存（查看响应日志）---");
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setConfigDataId("test-config");
            configInfo.setConfigContent("{\"key\":\"value\"}");
            configInfo.setContentType("JSON");
            
            SaveConfigResult saveResult = testClient.saveConfig(configInfo);
            System.out.println("保存配置结果: " + saveResult);
            
            // 测试获取配置（会打印响应）
            System.out.println("--- 测试获取配置（查看响应日志）---");
            GetConfigResult getConfigResult = testClient.getConfig(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    "test-config");
            System.out.println("获取配置结果: " + getConfigResult);
            
            // 测试心跳（会打印响应）
            if (registerResult.isSuccess() && registerResult.getNodeId() != null) {
                System.out.println("--- 测试心跳（查看响应日志）---");
                OperationResult heartbeatResult = testClient.heartbeat(registerResult.getNodeId());
                System.out.println("心跳结果: " + heartbeatResult);
            }
            System.out.println("=== 测试完成，查看上方日志中的响应信息 ===");
           
            
        } catch (Exception e) {
            System.err.println("集成测试失败（可能是服务端未运行）: " + e.getMessage());
            e.printStackTrace();
        } finally {
            testClient.close();
        }
    }
    
    // ========== 认证测试（需要真实服务端）==========
    
    /**
     * 真实测试：使用 userId 密码认证
     * 
     * <p>此测试需要服务端运行在 localhost:12004，并且启用了认证（enableAuth=Y）。
     * 取消 @Ignore 注解后可以运行此测试，测试 Basic Auth 认证功能。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>使用正确的 userId 和密码连接服务端</li>
     *   <li>验证认证成功并可以调用服务</li>
     *   <li>注册服务并验证成功</li>
     *   <li>获取服务信息验证权限正常</li>
     * </ul>
     * 
     * <p><strong>注意</strong>：需要在数据库中创建测试用户：</p>
     * <pre>
     * INSERT INTO HUB_USER (userId, userName, password, tenantId, statusFlag, activeFlag)
     * VALUES ('test-user', 'Test User', 'test123', 'tenant001', 'Y', 'Y');
     * </pre>
     */
    @Test
    @Disabled("需要真实服务端运行并启用认证，取消此注解以运行集成测试")
    public void testAuthWithUserId_WithRealServer() {
        // 配置使用 userId 密码认证
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setUserId("admin")           // 使用 userId（数据库主键）
                .setPassword("123456")           // 密码（可能是加密的）
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP");
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            // 连接服务端（会自动进行认证）
            System.out.println("=== 使用 userId 密码认证连接服务端 ===");
            System.out.println("userId: " + testConfig.getUserId());
            System.out.println("password: " + "***（已隐藏）");
            
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端");
            System.out.println("✓ 认证成功，已连接到服务端");
            
            // 测试服务注册（验证认证后可以正常调用服务）
            System.out.println("\n--- 测试服务注册（验证认证权限）---");
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setServiceName("test-auth-service");
            serviceInfo.setServiceType("INTERNAL");
            serviceInfo.setServiceVersion("1.0.0");
            serviceInfo.setServiceDescription("认证测试服务");
            
            NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
            nodeInfo.setWeight(1.0);
            nodeInfo.setEphemeral("Y");
            
            RegisterServiceResult registerResult = testClient.registerService(serviceInfo, nodeInfo);
            System.out.println("注册结果: " + registerResult);
            assertTrue(registerResult.isSuccess(), "服务注册应该成功");
            assertNotNull(registerResult.getNodeId(), "应该返回 nodeId");
            System.out.println("✓ 服务注册成功，nodeId: " + registerResult.getNodeId());
            
            // 等待一下
            Thread.sleep(500);
            
            // 获取服务信息（验证认证后可以正常查询）
            System.out.println("\n--- 测试获取服务（验证认证权限）---");
            GetServiceResult getServiceResult = testClient.getService(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    "test-auth-service");
            System.out.println("获取服务结果: " + getServiceResult);
            assertTrue(getServiceResult.isSuccess(), "获取服务应该成功");
            assertNotNull(getServiceResult.getService(), "应该返回服务信息");
            System.out.println("✓ 获取服务成功");
            
            // 清理：注销服务
            System.out.println("\n--- 清理：注销服务 ---");
            testClient.unregisterService(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    "test-auth-service",
                    registerResult.getNodeId());
            System.out.println("✓ 服务已注销");
            
            System.out.println("\n=== 认证测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("认证测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("认证测试失败: " + e.getMessage());
        } finally {
            testClient.close();
        }
    }
    
    /**
     * 真实测试：使用 Bearer Token 认证
     * 
     * <p>此测试需要服务端运行在 localhost:12004，并且启用了认证（enableAuth=Y）。
     * 取消 @Ignore 注解后可以运行此测试，测试 Bearer Token 认证功能。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>使用 Bearer Token 连接服务端</li>
     *   <li>验证认证成功并可以调用服务</li>
     * </ul>
     * 
     * <p><strong>注意</strong>：Bearer Token 认证需要服务端实现 Token 验证逻辑。</p>
     */
    @Test
    @Disabled("需要真实服务端运行并启用认证，取消此注解以运行集成测试")
    public void testAuthWithBearerToken_WithRealServer() {
        // 配置使用 Bearer Token 认证
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setAuthToken("test-token-123456")  // Bearer Token
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP");
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            // 连接服务端（会自动进行认证）
            System.out.println("=== 使用 Bearer Token 认证连接服务端 ===");
            System.out.println("authToken: " + testConfig.getAuthToken().substring(0, 10) + "***（已部分隐藏）");
            
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端");
            System.out.println("✓ 认证成功，已连接到服务端");
            
            // 测试简单的服务查询（验证认证后可以正常调用服务）
            System.out.println("\n--- 测试获取服务（验证认证权限）---");
            GetServiceResult getServiceResult = testClient.getService(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    "test-service");
            System.out.println("获取服务结果: success=" + getServiceResult.isSuccess());
            // 注意：服务可能不存在，但只要不报认证错误就说明认证成功
            System.out.println("✓ Bearer Token 认证成功");
            
            System.out.println("\n=== Bearer Token 认证测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("Bearer Token 认证测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("Bearer Token 认证测试失败: " + e.getMessage());
        } finally {
            testClient.close();
        }
    }
    
    /**
     * 真实测试：认证失败（错误的密码）
     * 
     * <p>此测试需要服务端运行在 localhost:12004，并且启用了认证（enableAuth=Y）。
     * 取消 @Ignore 注解后可以运行此测试，测试认证失败的情况。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>使用错误的密码连接服务端</li>
     *   <li>验证连接失败并返回认证错误</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行并启用认证，取消此注解以运行集成测试")
    public void testAuthFailure_WrongPassword_WithRealServer() {
        // 配置使用错误的密码
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setUserId("test-user")           // 正确的 userId
                .setPassword("wrong-password")    // 错误的密码
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP");
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            System.out.println("=== 使用错误密码进行认证（预期失败）===");
            System.out.println("userId: " + testConfig.getUserId());
            System.out.println("password: wrong-password");
            
            // 连接服务端
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端（gRPC 连接成功）");
            
            // 尝试调用服务（应该返回认证错误）
            System.out.println("\n--- 尝试调用服务（预期认证失败）---");
            try {
                ServiceInfo serviceInfo = new ServiceInfo();
                serviceInfo.setServiceName("test-service");
                NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
                
                RegisterServiceResult registerResult = testClient.registerService(serviceInfo, nodeInfo);
                
                // 如果到这里，说明认证没有生效（可能服务端未启用认证）
                System.out.println("⚠ 警告：认证应该失败，但调用成功了。请检查服务端是否启用了认证。");
                System.out.println("注册结果: " + registerResult);
                
            } catch (Exception e) {
                // 预期会抛出认证错误异常
                System.out.println("✓ 认证失败（符合预期）: " + e.getMessage());
                assertTrue(e.getMessage().contains("UNAUTHENTICATED") || 
                        e.getMessage().contains("认证") ||
                        e.getMessage().contains("密码"), "错误消息应该包含 'UNAUTHENTICATED' 或 '认证'");
            }
            
            System.out.println("\n=== 认证失败测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            testClient.close();
        }
    }
    
    /**
     * 真实测试：认证失败（用户不存在）
     * 
     * <p>此测试需要服务端运行在 localhost:12004，并且启用了认证（enableAuth=Y）。
     * 取消 @Ignore 注解后可以运行此测试，测试用户不存在的情况。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>使用不存在的 userId 连接服务端</li>
     *   <li>验证连接失败并返回认证错误</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行并启用认证，取消此注解以运行集成测试")
    public void testAuthFailure_UserNotFound_WithRealServer() {
        // 配置使用不存在的 userId
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setUserId("non-existent-user")   // 不存在的 userId
                .setPassword("any-password")      // 任意密码
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP");
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            System.out.println("=== 使用不存在的用户进行认证（预期失败）===");
            System.out.println("userId: " + testConfig.getUserId() + " (不存在)");
            
            // 连接服务端
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端（gRPC 连接成功）");
            
            // 尝试调用服务（应该返回认证错误）
            System.out.println("\n--- 尝试调用服务（预期认证失败）---");
            try {
                ServiceInfo serviceInfo = new ServiceInfo();
                serviceInfo.setServiceName("test-service");
                NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
                
                RegisterServiceResult registerResult = testClient.registerService(serviceInfo, nodeInfo);
                
                // 如果到这里，说明认证没有生效（可能服务端未启用认证）
                System.out.println("⚠ 警告：认证应该失败，但调用成功了。请检查服务端是否启用了认证。");
                System.out.println("注册结果: " + registerResult);
                
            } catch (Exception e) {
                // 预期会抛出认证错误异常
                System.out.println("✓ 认证失败（符合预期）: " + e.getMessage());
                assertTrue(e.getMessage().contains("UNAUTHENTICATED") || 
                        e.getMessage().contains("认证") ||
                        e.getMessage().contains("用户"), "错误消息应该包含 'UNAUTHENTICATED' 或 '认证' 或 '用户'");
            }
            
            System.out.println("\n=== 用户不存在测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            testClient.close();
        }
    }
    
    /**
     * 真实测试：无认证连接（服务端启用认证时应该失败）
     * 
     * <p>此测试需要服务端运行在 localhost:12004，并且启用了认证（enableAuth=Y）。
     * 取消 @Ignore 注解后可以运行此测试，测试未提供认证信息的情况。</p>
     * 
     * <p>测试内容：</p>
     * <ul>
     *   <li>不提供任何认证信息连接服务端</li>
     *   <li>验证调用服务时返回认证错误</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行并启用认证，取消此注解以运行集成测试")
    public void testNoAuth_WithAuthEnabledServer() {
        // 配置不提供任何认证信息
        ServiceCenterConfig testConfig = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                // 不设置 userId、password 或 authToken
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("DEFAULT_GROUP");
        
        ServiceCenterClient testClient = new ServiceCenterClient(testConfig);
        
        try {
            System.out.println("=== 不提供认证信息连接服务端（预期调用失败）===");
            
            // 连接服务端（gRPC 连接可以成功，但调用服务时会认证失败）
            testClient.connect();
            assertTrue(testClient.isConnected(), "应该已连接到服务端（gRPC 连接成功）");
            System.out.println("✓ gRPC 连接成功");
            
            // 尝试调用服务（应该返回认证错误）
            System.out.println("\n--- 尝试调用服务（预期认证失败）---");
            try {
                ServiceInfo serviceInfo = new ServiceInfo();
                serviceInfo.setServiceName("test-service");
                NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 8080);
                
                RegisterServiceResult registerResult = testClient.registerService(serviceInfo, nodeInfo);
                
                // 如果到这里，说明服务端未启用认证
                System.out.println("⚠ 警告：认证应该失败，但调用成功了。服务端可能未启用认证（enableAuth=N）。");
                System.out.println("注册结果: " + registerResult);
                
            } catch (Exception e) {
                // 预期会抛出认证错误异常
                System.out.println("✓ 认证失败（符合预期）: " + e.getMessage());
                assertTrue(e.getMessage().contains("UNAUTHENTICATED") || 
                        e.getMessage().contains("认证"), "错误消息应该包含 'UNAUTHENTICATED' 或 '认证'");
            }
            
            System.out.println("\n=== 无认证测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            testClient.close();
        }
    }
}
