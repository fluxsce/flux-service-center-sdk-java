package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 手动测试类 - 用于交互式测试和演示
 * 
 * <p>运行此类可以手动测试客户端的各种功能</p>
 * 
 * @author shangjian
 */
public class ManualTest {
    private static final Logger logger = LoggerFactory.getLogger(ManualTest.class);
    
    private static StreamBasedServiceCenterClient client;
    private static String currentNodeId;
    private static String currentSubscriptionId;
    private static String currentWatchId;
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("  StreamBasedServiceCenterClient 手动测试");
        logger.info("========================================");
        
        // 初始化
        initClient();
        
        // 主循环
        try (Scanner scanner = new Scanner(System.in)) {
        while (true) {
            printMenu();
            System.out.print("\n请选择操作 (输入序号): ");
            
            String choice = scanner.nextLine().trim();
            
            try {
                switch (choice) {
                    case "1":
                        testConnect();
                        break;
                    case "2":
                        testRegisterNode();
                        break;
                    case "3":
                        testHeartbeat();
                        break;
                    case "4":
                        testDiscoverNodes();
                        break;
                    case "5":
                        testSubscribeService();
                        break;
                    case "6":
                        testSaveConfig();
                        break;
                    case "7":
                        testGetConfig();
                        break;
                    case "8":
                        testWatchConfig();
                        break;
                    case "9":
                        testListConfigs();
                        break;
                    case "10":
                        testUnregisterNode();
                        break;
                    case "11":
                        testUnsubscribe();
                        break;
                    case "12":
                        testUnwatch();
                        break;
                    case "13":
                        testReconnect();
                        break;
                    case "0":
                        cleanup();
                        logger.info("再见！");
                        System.exit(0);
                        break;
                    default:
                        logger.warn("无效的选择: {}", choice);
                }
            } catch (Exception e) {
                logger.error("操作失败", e);
            }
            
            System.out.println("\n按回车继续...");
            scanner.nextLine();
        }
        }
    }
    
    private static void printMenu() {
        System.out.println("\n========================================");
        System.out.println("功能菜单:");
        System.out.println("  1. 连接到服务中心");
        System.out.println("  2. 注册节点");
        System.out.println("  3. 发送心跳");
        System.out.println("  4. 服务发现");
        System.out.println("  5. 订阅服务变更");
        System.out.println("  6. 保存配置");
        System.out.println("  7. 获取配置");
        System.out.println("  8. 监听配置变更");
        System.out.println("  9. 列出配置");
        System.out.println(" 10. 注销节点");
        System.out.println(" 11. 取消服务订阅");
        System.out.println(" 12. 取消配置监听");
        System.out.println(" 13. 测试重连恢复");
        System.out.println("  0. 退出");
        System.out.println("========================================");
        
        // 显示当前状态
        System.out.println("\n当前状态:");
        System.out.println("  - 连接状态: " + (client != null && client.isConnected() ? "已连接 ✓" : "未连接 ✗"));
        System.out.println("  - 当前节点: " + (currentNodeId != null ? currentNodeId : "无"));
        System.out.println("  - 订阅数量: " + (client != null ? client.getActiveSubscriptions().size() : 0));
        System.out.println("  - 监听数量: " + (client != null ? client.getActiveWatches().size() : 0));
    }
    
    private static void initClient() {
        logger.info("初始化客户端...");
        
        ServiceCenterConfig config = new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setEnableTls(false)
                .setNamespaceId("test-namespace")
                .setGroupName("test-group")
                .setHeartbeatInterval(5000)
                .setReconnectInterval(3000)
                .setMaxReconnectAttempts(-1)  // 无限重连
                .setRequestTimeout(30000);
        
        client = new StreamBasedServiceCenterClient(config);
        logger.info("客户端初始化完成");
    }
    
    private static void testConnect() {
        logger.info("========== 连接到服务中心 ==========");
        
        try {
            client.connect();
            Thread.sleep(1000);
            
            if (client.isConnected()) {
                logger.info("✅ 连接成功！");
            } else {
                logger.error("❌ 连接失败");
            }
        } catch (Exception e) {
            logger.error("连接异常", e);
        }
    }
    
    private static void testRegisterNode() {
        logger.info("========== 注册节点 ==========");
        
        try {
            NodeInfo nodeInfo = new NodeInfo();
            nodeInfo.setNamespaceId("test-namespace");
            nodeInfo.setGroupName("test-group");
            nodeInfo.setServiceName("manual-test-service");
            nodeInfo.setIpAddress("192.168.1.100");
            nodeInfo.setPortNumber(8080);
            nodeInfo.setWeight(100.0);
            nodeInfo.setHealthyStatus("HEALTHY");
            nodeInfo.setInstanceStatus("UP");
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("version", "1.0.0");
            metadata.put("env", "test");
            nodeInfo.setMetadata(metadata);
            
            RegisterNodeResult result = client.registerNode(nodeInfo);
            
            if (result.isSuccess()) {
                currentNodeId = result.getNodeId();
                logger.info("✅ 节点注册成功！");
                logger.info("   NodeId: {}", currentNodeId);
            } else {
                logger.error("❌ 节点注册失败: {}", result.getMessage());
            }
        } catch (Exception e) {
            logger.error("注册节点异常", e);
        }
    }
    
    private static void testHeartbeat() {
        logger.info("========== 发送心跳 ==========");
        
        if (currentNodeId == null) {
            logger.warn("请先注册节点");
            return;
        }
        
        try {
            OperationResult result = client.sendHeartbeat(currentNodeId);
            
            if (result.isSuccess()) {
                logger.info("✅ 心跳发送成功！");
            } else {
                logger.error("❌ 心跳发送失败: {}", result.getMessage());
            }
        } catch (Exception e) {
            logger.error("心跳异常", e);
        }
    }
    
    private static void testDiscoverNodes() {
        logger.info("========== 服务发现 ==========");
        
        try {
            List<NodeInfo> nodes = client.discoverNodes(
                    "test-namespace",
                    "test-group",
                    "manual-test-service",
                    true
            );
            
            logger.info("发现 {} 个节点:", nodes.size());
            for (NodeInfo node : nodes) {
                logger.info("  - {} @ {}:{} (权重: {})", 
                        node.getNodeId(),
                        node.getIpAddress(),
                        node.getPortNumber(),
                        node.getWeight());
            }
        } catch (Exception e) {
            logger.error("服务发现异常", e);
        }
    }
    
    private static void testSubscribeService() {
        logger.info("========== 订阅服务变更 ==========");
        
        try {
            ServiceChangeListener listener = event -> {
                logger.info("📢 收到服务变更事件:");
                logger.info("   类型: {}", event.getEventType());
                logger.info("   服务: {}.{}.{}", 
                        event.getNamespaceId(),
                        event.getGroupName(),
                        event.getServiceName());
            };
            
            currentSubscriptionId = client.subscribeService(
                    "test-namespace",
                    "test-group",
                    "manual-test-service",
                    listener
            );
            
            logger.info("✅ 服务订阅成功！");
            logger.info("   SubscriptionId: {}", currentSubscriptionId);
        } catch (Exception e) {
            logger.error("服务订阅异常", e);
        }
    }
    
    private static void testSaveConfig() {
        logger.info("========== 保存配置 ==========");
        
        try {
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setNamespaceId("test-namespace");
            configInfo.setGroupName("test-group");
            configInfo.setConfigDataId("manual-test.yaml");
            configInfo.setConfigContent("server:\n  port: 8080\n  host: localhost");
            configInfo.setContentType("yaml");
            configInfo.setConfigDesc("手动测试配置");
            
            SaveConfigResult result = client.saveConfig(configInfo);
            
            if (result.isSuccess()) {
                logger.info("✅ 配置保存成功！");
                logger.info("   Version: {}", result.getVersion());
                logger.info("   MD5: {}", result.getContentMd5());
            } else {
                logger.error("❌ 配置保存失败: {}", result.getMessage());
            }
        } catch (Exception e) {
            logger.error("保存配置异常", e);
        }
    }
    
    private static void testGetConfig() {
        logger.info("========== 获取配置 ==========");
        
        try {
            GetConfigResult result = client.getConfig(
                    "test-namespace",
                    "test-group",
                    "manual-test.yaml"
            );
            
            if (result.isSuccess() && result.getConfig() != null) {
                ConfigInfo config = result.getConfig();
                logger.info("✅ 配置获取成功！");
                logger.info("   DataId: {}", config.getConfigDataId());
                logger.info("   Version: {}", config.getConfigVersion());
                logger.info("   Content:\n{}", config.getConfigContent());
            } else {
                logger.error("❌ 配置获取失败: {}", result.getMessage());
            }
        } catch (Exception e) {
            logger.error("获取配置异常", e);
        }
    }
    
    private static void testWatchConfig() {
        logger.info("========== 监听配置变更 ==========");
        
        try {
            ConfigChangeListener listener = event -> {
                logger.info("📢 收到配置变更事件:");
                logger.info("   类型: {}", event.getEventType());
                logger.info("   配置: {}.{}.{}", 
                        event.getNamespaceId(),
                        event.getGroupName(),
                        event.getConfigDataId());
                if (event.getConfig() != null) {
                    logger.info("   版本: {}", event.getConfig().getConfigVersion());
                }
            };
            
            currentWatchId = client.watchConfig(
                    "test-namespace",
                    "test-group",
                    "manual-test.yaml",
                    listener
            );
            
            logger.info("✅ 配置监听成功！");
            logger.info("   WatchId: {}", currentWatchId);
        } catch (Exception e) {
            logger.error("配置监听异常", e);
        }
    }
    
    private static void testListConfigs() {
        logger.info("========== 列出配置 ==========");
        
        try {
            List<ConfigInfo> configs = client.listConfigs(
                    "test-namespace",
                    "test-group",
                    null,
                    1,
                    100
            );
            
            logger.info("找到 {} 个配置:", configs.size());
            for (ConfigInfo config : configs) {
                logger.info("  - {} (version: {})", 
                        config.getConfigDataId(),
                        config.getConfigVersion());
            }
        } catch (Exception e) {
            logger.error("列出配置异常", e);
        }
    }
    
    private static void testUnregisterNode() {
        logger.info("========== 注销节点 ==========");
        
        if (currentNodeId == null) {
            logger.warn("没有已注册的节点");
            return;
        }
        
        try {
            OperationResult result = client.unregisterNode(currentNodeId);
            
            if (result.isSuccess()) {
                logger.info("✅ 节点注销成功！");
                currentNodeId = null;
            } else {
                logger.error("❌ 节点注销失败: {}", result.getMessage());
            }
        } catch (Exception e) {
            logger.error("注销节点异常", e);
        }
    }
    
    private static void testUnsubscribe() {
        logger.info("========== 取消服务订阅 ==========");
        
        if (currentSubscriptionId == null) {
            logger.warn("没有活动的订阅");
            return;
        }
        
        try {
            OperationResult result = client.unsubscribe(currentSubscriptionId);
            
            if (result.isSuccess()) {
                logger.info("✅ 取消订阅成功！");
                currentSubscriptionId = null;
            } else {
                logger.error("❌ 取消订阅失败: {}", result.getMessage());
            }
        } catch (Exception e) {
            logger.error("取消订阅异常", e);
        }
    }
    
    private static void testUnwatch() {
        logger.info("========== 取消配置监听 ==========");
        
        if (currentWatchId == null) {
            logger.warn("没有活动的监听");
            return;
        }
        
        try {
            OperationResult result = client.unwatch(currentWatchId);
            
            if (result.isSuccess()) {
                logger.info("✅ 取消监听成功！");
                currentWatchId = null;
            } else {
                logger.error("❌ 取消监听失败: {}", result.getMessage());
            }
        } catch (Exception e) {
            logger.error("取消监听异常", e);
        }
    }
    
    private static void testReconnect() {
        logger.info("========== 测试重连恢复 ==========");
        logger.info("提示：请在另一个终端重启服务端，或等待连接超时");
        logger.info("客户端将自动重连并恢复节点注册和订阅状态");
        logger.info("请观察日志输出...");
    }
    
    private static void cleanup() {
        logger.info("========== 清理资源 ==========");
        
        if (client != null) {
            try {
                client.close();
                logger.info("客户端已关闭");
            } catch (Exception e) {
                logger.error("关闭客户端失败", e);
            }
        }
    }
}

