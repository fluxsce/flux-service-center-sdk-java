package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * StreamBasedServiceCenterClient 集成测试
 * 
 * <p>使用真实的服务中心连接进行测试</p>
 * 
 * @author shangjian
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StreamBasedServiceCenterClientTest {
    private static final Logger logger = LoggerFactory.getLogger(StreamBasedServiceCenterClientTest.class);
    
    private static StreamBasedServiceCenterClient client;
    private static ServiceCenterConfig config;
    
    // 测试数据
    private static String testNodeId;
    private static String testSubscriptionId;
    private static String testWatchId;
    
    @BeforeAll
    static void setUp() {
        logger.info("========== 初始化测试环境 ==========");
        
        // 配置服务中心连接
        config = new ServiceCenterConfig()
                .setServerHost("localhost")  // 修改为你的服务中心地址
                .setServerPort(12004)        // 修改为你的服务中心端口
                .setEnableTls(false)         // 是否启用 TLS
                .setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4")
                .setGroupName("test-group")
                .setUserId("admin")
                .setPassword("123456")
                .setHeartbeatInterval(5000)
                .setReconnectInterval(3000)
                .setMaxReconnectAttempts(10)
                .setRequestTimeout(30000);
        
        // 创建客户端
        client = new StreamBasedServiceCenterClient(config);
        logger.info("客户端创建成功");
        
        // 自动连接（支持单独运行某个测试方法）
        try {
            client.connect();
            // 等待连接建立
            Thread.sleep(2000);
            logger.info("客户端已自动连接");
        } catch (Exception e) {
            logger.error("自动连接失败", e);
        }
    }
    
    @AfterAll
    static void tearDown() {
        logger.info("========== 清理测试环境 ==========");
        
        if (client != null) {
            try {
                client.close();
                logger.info("客户端已关闭");
            } catch (Exception e) {
                logger.error("关闭客户端失败", e);
            }
        }
    }
    
    // ========== 测试用例 ==========
    
    @Test
    @Order(1)
    @DisplayName("1. 测试连接到服务中心")
    void testConnect() {
        logger.info("========== 测试连接 ==========");
        
        try {
            // 如果还未连接，则连接
            if (!client.isConnected()) {
                client.connect();
                // 等待连接建立
                Thread.sleep(2000);
            }
            
            Assertions.assertTrue(client.isConnected(), "客户端应该已连接");
            Assertions.assertTrue(client.checkHealth(), "健康检查应该通过");
            
            logger.info("连接测试通过");
        } catch (Exception e) {
            logger.error("连接测试失败", e);
            Assertions.fail("连接失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(2)
    @DisplayName("2. 测试注册服务节点")
    void testRegisterNode() {
        logger.info("========== 测试注册节点 ==========");
        
        try {
            // 创建节点信息
            NodeInfo nodeInfo = new NodeInfo();
            nodeInfo.setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4");
            nodeInfo.setGroupName("test-group");
            nodeInfo.setServiceName("test-service");
            nodeInfo.setIpAddress("192.168.1.100");
            nodeInfo.setPortNumber(8080);
            nodeInfo.setWeight(100.0);
            nodeInfo.setHealthyStatus("HEALTHY");
            nodeInfo.setInstanceStatus("UP");
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("version", "1.0.0");
            metadata.put("region", "cn-hangzhou");
            nodeInfo.setMetadata(metadata);
            client.connect();
            // 注册节点
            RegisterNodeResult result = client.registerNode(nodeInfo);
            logger.info("节点注册, result: {}", result);
            Assertions.assertTrue(result.isSuccess(), "节点注册应该成功");
            Assertions.assertNotNull(result.getNodeId(), "应该返回 nodeId");
            
            testNodeId = result.getNodeId();
            logger.info("节点注册成功, nodeId: {}", testNodeId);
            
            // 验证节点已注册
            List<String> registeredNodes = client.getRegisteredNodeIds();
            Assertions.assertTrue(registeredNodes.contains(testNodeId), "节点应该在已注册列表中");
            
        } catch (Exception e) {
            logger.error("节点注册测试失败", e);
            Assertions.fail("节点注册失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("3. 测试发送心跳")
    void testHeartbeat() {
        logger.info("========== 测试心跳 ==========");
        
        try {
            testRegisterNode();
            Assertions.assertNotNull(testNodeId, "需要先注册节点");
            
            // 等待自动心跳
            logger.info("等待自动心跳...");
            Thread.sleep(6000);
            
            // 手动发送心跳
            OperationResult result = client.sendHeartbeat(testNodeId);
            Assertions.assertTrue(result.isSuccess(), "心跳发送应该成功");
            
            logger.info("心跳测试通过");
            
        } catch (Exception e) {
            logger.error("心跳测试失败", e);
            Assertions.fail("心跳测试失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("4. 测试服务发现")
    void testDiscoverNodes() {
        logger.info("========== 测试服务发现 ==========");
        
        try {
            testRegisterNode();
            // 发现节点
            List<NodeInfo> nodes = client.discoverNodes(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "test-group",
                    "test-service",
                    true  // 只返回健康节点
            );
            
            logger.info("发现 {} 个节点", nodes.size());
            
            // 应该至少有我们刚注册的节点
            Assertions.assertFalse(nodes.isEmpty(), "应该至少发现一个节点");
            
            // 打印节点信息
            for (NodeInfo node : nodes) {
                logger.info("节点: {} - {}:{}", node.getNodeId(), node.getIpAddress(), node.getPortNumber());
            }
            
            logger.info("服务发现测试通过");
            
        } catch (Exception e) {
            logger.error("服务发现测试失败", e);
            Assertions.fail("服务发现失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("5. 测试订阅服务变更")
    void testSubscribeService() {
        logger.info("========== 测试服务订阅 ==========");
        
        try {
            testRegisterNode();
            CountDownLatch latch = new CountDownLatch(1);
            
            // 创建监听器
            ServiceChangeListener listener = event -> {
                logger.info("收到服务变更事件: type={}, service={}", 
                        event.getEventType(), event.getServiceName());
                latch.countDown();
            };
            
            // 订阅服务
            testSubscriptionId = client.subscribeService(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "test-group",
                    "test-service",
                    listener
            );
            
            Assertions.assertNotNull(testSubscriptionId, "应该返回订阅 ID");
            logger.info("服务订阅成功, subscriptionId: {}", testSubscriptionId);
            
            // 验证订阅已激活
            List<String> subscriptions = client.getActiveSubscriptions();
            Assertions.assertTrue(subscriptions.contains(testSubscriptionId), 
                    "订阅应该在激活列表中");
            
            // 触发服务变更（注册一个新节点）
            logger.info("注册新节点以触发变更事件...");
            NodeInfo newNode = new NodeInfo();
            newNode.setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4");
            newNode.setGroupName("test-group");
            newNode.setServiceName("test-service");
            newNode.setIpAddress("192.168.1.101");
            newNode.setPortNumber(8081);
            newNode.setWeight(100.0);
            
            RegisterNodeResult result = client.registerNode(newNode);
            if (result.isSuccess()) {
                logger.info("新节点注册成功: {}", result.getNodeId());
            }
            
            // 等待接收事件
            boolean received = latch.await(10, TimeUnit.SECONDS);
            if (received) {
                logger.info("成功接收到服务变更事件");
            } else {
                logger.warn("未在 10 秒内接收到服务变更事件");
            }
            while (true) {
                
            }
        } catch (Exception e) {
            logger.error("服务订阅测试失败", e);
            Assertions.fail("服务订阅失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(6)
    @DisplayName("6. 测试保存配置")
    void testSaveConfig() {
        logger.info("========== 测试保存配置 ==========");
        
        try { client.connect();
            // 创建配置
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4");
            configInfo.setGroupName("test-group");
            configInfo.setConfigDataId("test-config.yaml");
            configInfo.setConfigContent("server:\n  port: 8080\n  host: localhost");
            configInfo.setContentType("yaml");
            configInfo.setConfigDesc("测试配置");
            
            // 保存配置
            SaveConfigResult result = client.saveConfig(configInfo);
            
            Assertions.assertTrue(result.isSuccess(), "配置保存应该成功");
            Assertions.assertNotNull(result.getVersion(), "应该返回版本号");
            Assertions.assertNotNull(result.getContentMd5(), "应该返回 MD5");
            
            logger.info("配置保存成功, version: {}, md5: {}", 
                    result.getVersion(), result.getContentMd5());
            
        } catch (Exception e) {
            logger.error("配置保存测试失败", e);
            Assertions.fail("配置保存失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("7. 测试获取配置")
    void testGetConfig() {
        logger.info("========== 测试获取配置 ==========");
        client.connect();
        try {
            // 获取配置
            GetConfigResult result = client.getConfig(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "test-group",
                    "test-config.yaml"
            );
            
            Assertions.assertTrue(result.isSuccess(), "获取配置应该成功");
            Assertions.assertNotNull(result.getConfig(), "应该返回配置信息");
            
            ConfigInfo config = result.getConfig();
            logger.info("获取配置成功:");
            logger.info("  - DataId: {}", config.getConfigDataId());
            logger.info("  - Version: {}", config.getConfigVersion());
            logger.info("  - Content: {}", config.getConfigContent());
            
        } catch (Exception e) {
            logger.error("获取配置测试失败", e);
            Assertions.fail("获取配置失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(8)
    @DisplayName("8. 测试监听配置变更")
    void testWatchConfig() {
        logger.info("========== 测试配置监听 ==========");
        
        try {
            CountDownLatch latch = new CountDownLatch(1);
            
            // 创建监听器
            ConfigChangeListener listener = event -> {
                logger.info("收到配置变更事件: type={}, config={}", 
                        event.getEventType(), event.getConfigDataId());
                latch.countDown();
            };
            
            // 监听配置
            testWatchId = client.watchConfig(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "test-group",
                    "test-config.yaml",
                    listener
            );
            
            Assertions.assertNotNull(testWatchId, "应该返回监听 ID");
            logger.info("配置监听成功, watchId: {}", testWatchId);
            
            // 验证监听已激活
            List<String> watches = client.getActiveWatches();
            Assertions.assertTrue(watches.contains(testWatchId), 
                    "监听应该在激活列表中");
            
            // 触发配置变更
            logger.info("更新配置以触发变更事件...");
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4");
            configInfo.setGroupName("test-group");
            configInfo.setConfigDataId("test-config.yaml");
            configInfo.setConfigContent("server:\n  port: 8081\n  host: 0.0.0.0");
            configInfo.setContentType("yaml");
            
            SaveConfigResult result = client.saveConfig(configInfo);
            if (result.isSuccess()) {
                logger.info("配置更新成功");
            }
            
            // 等待接收事件
            boolean received = latch.await(10, TimeUnit.SECONDS);
            if (received) {
                logger.info("成功接收到配置变更事件");
            } else {
                logger.warn("未在 10 秒内接收到配置变更事件");
            }
            
        } catch (Exception e) {
            logger.error("配置监听测试失败", e);
            Assertions.fail("配置监听失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(9)
    @DisplayName("9. 测试列出配置")
    void testListConfigs() {
        logger.info("========== 测试列出配置 ==========");
        
        try {
            // 列出配置
            List<ConfigInfo> configs = client.listConfigs(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "test-group",
                    null,  // searchKey
                    1,     // pageNum
                    100    // pageSize
            );
            
            logger.info("找到 {} 个配置", configs.size());
            
            // 打印配置信息
            for (ConfigInfo config : configs) {
                logger.info("配置: {} - version: {}", 
                        config.getConfigDataId(), config.getConfigVersion());
            }
            
            logger.info("列出配置测试通过");
            
        } catch (Exception e) {
            logger.error("列出配置测试失败", e);
            Assertions.fail("列出配置失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(10)
    @DisplayName("10. 测试获取配置历史")
    void testGetConfigHistory() {
        logger.info("========== 测试配置历史 ==========");
        
        try {
            // 获取配置历史
            List<ConfigHistory> history = client.getConfigHistory(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "test-group",
                    "test-config.yaml",
                    1,   // pageNum
                    10   // pageSize
            );
            
            logger.info("找到 {} 条历史记录", history.size());
            
            // 打印历史记录
            for (ConfigHistory record : history) {
                logger.info("历史: id={}, version={}, time={}", 
                        record.getConfigHistoryId(), 
                        record.getConfigVersion(),
                        record.getChangeTime());
            }
            
            logger.info("配置历史测试通过");
            
        } catch (Exception e) {
            logger.error("配置历史测试失败", e);
            Assertions.fail("配置历史失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(11)
    @DisplayName("11. 测试取消订阅和监听")
    void testUnsubscribeAndUnwatch() {
        logger.info("========== 测试取消订阅和监听 ==========");
        
        try {
            // 取消服务订阅
            if (testSubscriptionId != null) {
                OperationResult result1 = client.unsubscribe(testSubscriptionId);
                Assertions.assertTrue(result1.isSuccess(), "取消订阅应该成功");
                logger.info("取消服务订阅成功");
            }
            
            // 取消配置监听
            if (testWatchId != null) {
                OperationResult result2 = client.unwatch(testWatchId);
                Assertions.assertTrue(result2.isSuccess(), "取消监听应该成功");
                logger.info("取消配置监听成功");
            }
            
        } catch (Exception e) {
            logger.error("取消订阅/监听测试失败", e);
            Assertions.fail("取消订阅/监听失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(12)
    @DisplayName("12. 测试注销节点")
    void testUnregisterNode() {
        logger.info("========== 测试注销节点 ==========");
        
        try {
            testRegisterNode();
            Assertions.assertNotNull(testNodeId, "需要先注册节点");
            
            // 注销节点
            OperationResult result = client.unregisterNode(testNodeId);
            Assertions.assertTrue(result.isSuccess(), "节点注销应该成功");
            
            logger.info("节点注销成功");
            
            // 验证节点已注销
            List<String> registeredNodes = client.getRegisteredNodeIds();
            Assertions.assertFalse(registeredNodes.contains(testNodeId), 
                    "节点不应该在已注册列表中");
            
        } catch (Exception e) {
            logger.error("节点注销测试失败", e);
            Assertions.fail("节点注销失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(13)
    @DisplayName("13. 测试删除配置")
    void testDeleteConfig() {
        logger.info("========== 测试删除配置 ==========");
        
        try {
            // 删除配置
            OperationResult result = client.deleteConfig(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "test-group",
                    "test-config.yaml"
            );
            
            Assertions.assertTrue(result.isSuccess(), "删除配置应该成功");
            logger.info("删除配置成功");
            
        } catch (Exception e) {
            logger.error("删除配置测试失败", e);
            Assertions.fail("删除配置失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(14)
    @DisplayName("14. 压力测试 - 批量注册节点")
    void testBatchRegisterNodes() {
        logger.info("========== 批量注册节点压力测试 ==========");
        
        int nodeCount = 10;
        int successCount = 0;
        
        try {
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < nodeCount; i++) {
                NodeInfo nodeInfo = new NodeInfo();
                nodeInfo.setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4");
                nodeInfo.setGroupName("test-group");
                nodeInfo.setServiceName("batch-test-service");
                nodeInfo.setIpAddress("192.168.1." + (200 + i));
                nodeInfo.setPortNumber(8000 + i);
                nodeInfo.setWeight(100.0);
                
                try {
                    RegisterNodeResult result = client.registerNode(nodeInfo);
                    if (result.isSuccess()) {
                        successCount++;
                        // 立即注销，避免污染环境
                        client.unregisterNode(result.getNodeId());
                    }
                } catch (Exception e) {
                    logger.warn("节点 {} 注册失败", i, e);
                }
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            logger.info("批量注册测试完成:");
            logger.info("  - 总数: {}", nodeCount);
            logger.info("  - 成功: {}", successCount);
            logger.info("  - 耗时: {} ms", elapsed);
            logger.info("  - 平均: {} ms/节点", elapsed / nodeCount);
            
            Assertions.assertTrue(successCount >= nodeCount * 0.9, 
                    "成功率应该 >= 90%");
            
        } catch (Exception e) {
            logger.error("批量注册测试失败", e);
            Assertions.fail("批量注册失败: " + e.getMessage());
        }
    }
}

