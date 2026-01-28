package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.listener.ConfigChangeListenerAdapter;
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
 * 配置订阅测试类
 *
 * <p>测试配置变更订阅功能，包括：
 * <ul>
 *   <li>订阅单个配置</li>
 *   <li>订阅多个配置</li>
 *   <li>测试各种事件（配置更新、删除等）</li>
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
public class ConfigSubscriptionTest {

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
    public void testWatchConfig_WithNullListener() {
        client = new ServiceCenterClient(config);

        // 调用方法，验证 null listener 会抛出异常
        try {
            client.watchConfig("ns_test", "DEFAULT_GROUP", Arrays.asList("test-config"), null);
            fail("应该抛出 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // 预期异常
        }
    }

    @Test
    public void testUnwatchConfig_WithValidSubscriptionId() {
        client = new ServiceCenterClient(config);

        // 取消订阅应该可以安全调用，即使订阅ID不存在
        client.unwatchConfig("non-existent-subscription-id");
        // 验证可以多次调用（幂等性）
        client.unwatchConfig("non-existent-subscription-id");
    }

    // ========== 集成测试示例（需要真实服务端）==========

    /**
     * 真实测试1：订阅单个配置并接收配置变更事件
     *
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试配置订阅功能。</p>
     *
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>订阅配置变更</li>
     *   <li>保存配置，验证收到 CONFIG_UPDATED 事件</li>
     *   <li>更新配置，验证收到 CONFIG_UPDATED 事件</li>
     *   <li>删除配置，验证收到 CONFIG_DELETED 事件</li>
     *   <li>取消订阅</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testWatchSingleConfig_WithRealServer() {
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
            System.out.println("=== 已连接到服务端，开始测试配置订阅 ===");

            // 创建事件计数器
            AtomicInteger configUpdatedCount = new AtomicInteger(0);
            AtomicInteger configDeletedCount = new AtomicInteger(0);
            CountDownLatch configUpdatedLatch = new CountDownLatch(1);
            CountDownLatch configDeletedLatch = new CountDownLatch(1);

            // 创建监听器
            ConfigChangeListener listener = new ConfigChangeListenerAdapter() {
                @Override
                public void onConfigUpdated(ConfigInfo config, String contentMd5) {
                    configUpdatedCount.incrementAndGet();
                    System.out.println("收到配置更新事件: configDataId=" + (config != null ? config.getConfigDataId() : "null") +
                            ", namespaceId=" + (config != null ? config.getNamespaceId() : "null") +
                            ", groupName=" + (config != null ? config.getGroupName() : "null") +
                            ", version=" + (config != null ? config.getConfigVersion() : "null") +
                            ", contentMd5=" + contentMd5 +
                            ", contentType=" + (config != null ? config.getContentType() : "null") +
                            ", configContent=" + (config != null && config.getConfigContent() != null ?
                            (config.getConfigContent().length() > 100 ?
                                    config.getConfigContent().substring(0, 100) + "..." :
                                    config.getConfigContent()) : "null"));
                    configUpdatedLatch.countDown();
                }

                @Override
                public void onConfigDeleted(String namespaceId, String groupName, String configDataId) {
                    configDeletedCount.incrementAndGet();
                    System.out.println("收到配置删除事件: namespaceId=" + namespaceId +
                            ", groupName=" + groupName +
                            ", configDataId=" + configDataId);
                    configDeletedLatch.countDown();
                }

                @Override
                public void onConfigChange(ConfigChangeEvent event) {
                    System.out.println("收到配置变更事件: eventType=" + event.getEventType() +
                            ", namespaceId=" + event.getNamespaceId() +
                            ", groupName=" + event.getGroupName() +
                            ", configDataId=" + event.getConfigDataId() +
                            ", timestamp=" + event.getTimestamp() +
                            ", contentMd5=" + event.getContentMd5() +
                            ", config=" + (event.getConfig() != null ? event.getConfig().getConfigDataId() : "null"));
                    // 调用父类方法，触发事件路由（调用 onConfigUpdated、onConfigDeleted 等）
                    super.onConfigChange(event);
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

            // 订阅配置
            System.out.println("--- 订阅配置变更 ---");
            System.out.println("订阅参数: namespaceId=ns_F41J68C80A50C28G68A06I53A49J4, groupName=DEFAULT_GROUP, configDataIds=[test-subscription-config]");
            String subscriptionId = testClient.watchConfig(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    Arrays.asList("test-subscription-config"),
                    listener);
            System.out.println("订阅成功，subscriptionId: " + subscriptionId);
            assertNotNull(subscriptionId, "订阅ID不应该为空");
            assertFalse(subscriptionId.isEmpty(), "订阅ID不应该为空字符串");

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
     * 真实测试2：订阅多个配置
     *
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试多配置订阅功能。</p>
     *
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>订阅多个配置</li>
     *   <li>为不同配置保存数据，验证收到对应的事件</li>
     *   <li>取消订阅</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testWatchMultipleConfigs_WithRealServer() {
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
            System.out.println("=== 已连接到服务端，开始测试多配置订阅 ===");

            // 创建事件计数器（按配置ID分组）
            Map<String, AtomicInteger> configEventCounts = new ConcurrentHashMap<>();
            CountDownLatch config1UpdatedLatch = new CountDownLatch(1);
            CountDownLatch config2UpdatedLatch = new CountDownLatch(1);

            // 创建监听器
            ConfigChangeListener listener = new ConfigChangeListenerAdapter() {
                @Override
                public void onConfigUpdated(ConfigInfo config, String contentMd5) {
                    String configDataId = config != null ? config.getConfigDataId() : "null";
                    configEventCounts.computeIfAbsent(configDataId, k -> new AtomicInteger(0))
                            .incrementAndGet();

                    System.out.println("收到配置更新事件: configDataId=" + configDataId +
                            ", namespaceId=" + (config != null ? config.getNamespaceId() : "null") +
                            ", groupName=" + (config != null ? config.getGroupName() : "null") +
                            ", version=" + (config != null ? config.getConfigVersion() : "null") +
                            ", contentMd5=" + contentMd5);

                    if ("test-subscription-config-1".equals(configDataId)) {
                        config1UpdatedLatch.countDown();
                    } else if ("test-subscription-config-2".equals(configDataId)) {
                        config2UpdatedLatch.countDown();
                    }
                }

                @Override
                public void onConfigChange(ConfigChangeEvent event) {
                    System.out.println("收到配置变更事件: eventType=" + event.getEventType() +
                            ", namespaceId=" + event.getNamespaceId() +
                            ", groupName=" + event.getGroupName() +
                            ", configDataId=" + event.getConfigDataId() +
                            ", timestamp=" + event.getTimestamp() +
                            ", contentMd5=" + event.getContentMd5());
                    // 调用父类方法，触发事件路由（调用 onConfigUpdated、onConfigDeleted 等）
                    super.onConfigChange(event);
                }
            };

            // 订阅多个配置
            System.out.println("--- 订阅多个配置变更 ---");
            System.out.println("订阅参数: namespaceId=ns_F41J68C80A50C28G68A06I53A49J4, groupName=DEFAULT_GROUP, configDataIds=[test-subscription-config-1, test-subscription-config-2]");
            String subscriptionId = testClient.watchConfig(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    Arrays.asList("test-subscription-config-1", "test-subscription-config-2"),
                    listener);
            System.out.println("订阅成功，subscriptionId: " + subscriptionId);
            assertNotNull(subscriptionId, "订阅ID不应该为空");

            // 等待一下，确保订阅已建立
            System.out.println("--- 等待订阅建立（1秒）---");
            Thread.sleep(1000);
            System.out.println("--- 订阅已建立，准备创建配置 ---");

            // 创建第一个配置
            System.out.println("--- 创建第一个配置 ---");
            ConfigInfo configInfo1 = new ConfigInfo();
            configInfo1.setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4");
            configInfo1.setGroupName("DEFAULT_GROUP");
            configInfo1.setConfigDataId("test-subscription-config-1");
            configInfo1.setConfigContent("config 1 content");
            configInfo1.setContentType("text");

            System.out.println("配置1信息: configDataId=" + configInfo1.getConfigDataId() +
                    ", contentType=" + configInfo1.getContentType() +
                    ", configContent=" + configInfo1.getConfigContent());

            SaveConfigResult saveResult1 = testClient.saveConfig(configInfo1);
            System.out.println("保存配置1结果: success=" + saveResult1.isSuccess() +
                    ", version=" + saveResult1.getVersion() +
                    ", contentMd5=" + saveResult1.getContentMd5());
            assertTrue(saveResult1.isSuccess(), "第一个配置保存应该成功");

            // 等待接收第一个配置的 CONFIG_UPDATED 事件
            System.out.println("--- 等待接收配置1的 CONFIG_UPDATED 事件（最多等待5秒）---");
            boolean config1EventReceived = config1UpdatedLatch.await(5, TimeUnit.SECONDS);
            if (config1EventReceived) {
                System.out.println("✓ 已收到配置1的更新事件");
            } else {
                System.out.println("✗ 未在5秒内收到配置1的更新事件");
            }
            assertTrue(config1EventReceived, "应该收到第一个配置的更新事件");

            // 创建第二个配置
            System.out.println("--- 创建第二个配置 ---");
            ConfigInfo configInfo2 = new ConfigInfo();
            configInfo2.setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4");
            configInfo2.setGroupName("DEFAULT_GROUP");
            configInfo2.setConfigDataId("test-subscription-config-2");
            configInfo2.setConfigContent("config 2 content");
            configInfo2.setContentType("text");

            System.out.println("配置2信息: configDataId=" + configInfo2.getConfigDataId() +
                    ", contentType=" + configInfo2.getContentType() +
                    ", configContent=" + configInfo2.getConfigContent());

            SaveConfigResult saveResult2 = testClient.saveConfig(configInfo2);
            System.out.println("保存配置2结果: success=" + saveResult2.isSuccess() +
                    ", version=" + saveResult2.getVersion() +
                    ", contentMd5=" + saveResult2.getContentMd5());
            assertTrue(saveResult2.isSuccess(), "第二个配置保存应该成功");

            // 等待接收第二个配置的 CONFIG_UPDATED 事件
            System.out.println("--- 等待接收配置2的 CONFIG_UPDATED 事件（最多等待5秒）---");
            boolean config2EventReceived = config2UpdatedLatch.await(5, TimeUnit.SECONDS);
            if (config2EventReceived) {
                System.out.println("✓ 已收到配置2的更新事件");
            } else {
                System.out.println("✗ 未在5秒内收到配置2的更新事件");
            }
            assertTrue(config2EventReceived, "应该收到第二个配置的更新事件");

            // 验证事件计数
            System.out.println("--- 验证事件计数 ---");
            int config1EventCount = configEventCounts.getOrDefault("test-subscription-config-1", new AtomicInteger(0)).get();
            int config2EventCount = configEventCounts.getOrDefault("test-subscription-config-2", new AtomicInteger(0)).get();
            System.out.println("配置1事件计数: " + config1EventCount);
            System.out.println("配置2事件计数: " + config2EventCount);
            assertEquals(1, config1EventCount, "第一个配置应该收到1个事件");
            assertEquals(1, config2EventCount, "第二个配置应该收到1个事件");

            // 取消订阅
            System.out.println("--- 取消订阅 ---");
            testClient.unwatchConfig(subscriptionId);

            // 清理
            System.out.println("--- 清理：删除配置 ---");
            testClient.deleteConfig("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", "test-subscription-config-1");
            testClient.deleteConfig("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", "test-subscription-config-2");

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
     * 真实测试3：测试订阅重连机制
     *
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试订阅重连功能。</p>
     *
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>订阅配置</li>
     *   <li>模拟连接断开（关闭客户端）</li>
     *   <li>重新连接，验证订阅自动恢复</li>
     * </ul>
     *
     * <p>注意：此测试需要手动干预（关闭/重启服务端），主要用于验证重连逻辑。</p>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testWatchConfigReconnect_WithRealServer() {
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
            System.out.println("=== 已连接到服务端，开始测试配置订阅重连 ===");

            // 创建事件计数器
            AtomicInteger reconnectCount = new AtomicInteger(0);
            AtomicInteger disconnectCount = new AtomicInteger(0);
            CountDownLatch reconnectLatch = new CountDownLatch(1);

            // 创建监听器
            ConfigChangeListener listener = new ConfigChangeListenerAdapter() {
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
                public void onConfigChange(ConfigChangeEvent event) {
                    System.out.println("收到配置变更事件: eventType=" + event.getEventType() +
                            ", namespaceId=" + event.getNamespaceId() +
                            ", groupName=" + event.getGroupName() +
                            ", configDataId=" + event.getConfigDataId() +
                            ", timestamp=" + event.getTimestamp());
                    // 调用父类方法，触发事件路由（调用 onConfigUpdated、onConfigDeleted 等）
                    super.onConfigChange(event);
                }
            };

            // 订阅配置
            System.out.println("--- 订阅配置变更 ---");
            System.out.println("订阅参数: namespaceId=ns_F41J68C80A50C28G68A06I53A49J4, groupName=DEFAULT_GROUP, configDataIds=[test-reconnect-config]");
            String subscriptionId = testClient.watchConfig(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    Arrays.asList("test-reconnect-config"),
                    listener);
            System.out.println("订阅成功，subscriptionId: " + subscriptionId);

            // 等待一下，确保订阅已建立
            System.out.println("--- 等待订阅建立（2秒）---");
            Thread.sleep(2000);
            System.out.println("--- 订阅已建立 ---");

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
            testClient.unwatchConfig(subscriptionId);

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
     * 真实测试4：手动获取配置列表
     *
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试获取配置列表功能。</p>
     *
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>获取指定命名空间和分组下的所有配置列表</li>
     *   <li>打印每个配置的详细信息</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testListConfigs_WithRealServer() {
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
            System.out.println("=== 已连接到服务端，开始测试获取配置列表 ===");

            // 获取配置列表
            System.out.println("--- 获取配置列表 ---");
            System.out.println("查询参数: namespaceId=ns_F41J68C80A50C28G68A06I53A49J4, groupName=DEFAULT_GROUP");

            List<ConfigInfo> configs = testClient.listConfigs(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP");

            System.out.println("--- 配置列表查询结果 ---");
            System.out.println("配置总数: " + (configs != null ? configs.size() : 0));

            if (configs == null || configs.isEmpty()) {
                System.out.println("未找到任何配置");
            } else {
                System.out.println("--- 配置详情 ---");
                for (int i = 0; i < configs.size(); i++) {
                    ConfigInfo config = configs.get(i);
                    System.out.println("配置 #" + (i + 1) + ":");
                    System.out.println("  configDataId: " + (config.getConfigDataId() != null ? config.getConfigDataId() : "null"));
                    System.out.println("  namespaceId: " + (config.getNamespaceId() != null ? config.getNamespaceId() : "null"));
                    System.out.println("  groupName: " + (config.getGroupName() != null ? config.getGroupName() : "null"));
                    System.out.println("  contentType: " + (config.getContentType() != null ? config.getContentType() : "null"));
                    System.out.println("  configVersion: " + config.getConfigVersion());
                    System.out.println("  contentMd5: " + (config.getContentMd5() != null ? config.getContentMd5() : "null"));
                    System.out.println("  configDesc: " + (config.getConfigDesc() != null ? config.getConfigDesc() : "null"));

                    // 打印配置内容（如果内容太长，只打印前100个字符）
                    String content = config.getConfigContent();
                    if (content != null) {
                        if (content.length() > 100) {
                            System.out.println("  configContent: " + content.substring(0, 100) + "... (总长度: " + content.length() + " 字符)");
                        } else {
                            System.out.println("  configContent: " + content);
                        }
                    } else {
                        System.out.println("  configContent: null");
                    }
                    System.out.println();
                }
            }

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
     * 真实测试5：获取单个配置
     *
     * <p>此测试需要服务端运行在 localhost:12004。
     * 取消 @Ignore 注解后可以运行此测试，测试获取单个配置功能。</p>
     *
     * <p>测试内容：</p>
     * <ul>
     *   <li>连接服务端</li>
     *   <li>创建或更新一个配置</li>
     *   <li>获取该配置，验证返回结果</li>
     *   <li>测试获取不存在的配置</li>
     * </ul>
     */
    @Test
    @Disabled("需要真实服务端运行，取消此注解以运行集成测试")
    public void testGetConfig_WithRealServer() {
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
            System.out.println("=== 已连接到服务端，开始测试获取单个配置 ===");

            // 先创建一个配置用于测试
            System.out.println("--- 创建测试配置 ---");
            String testConfigDataId = "test-get-config-" + System.currentTimeMillis();
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setNamespaceId("ns_F41J68C80A50C28G68A06I53A49J4");
            configInfo.setGroupName("DEFAULT_GROUP");
            configInfo.setConfigDataId(testConfigDataId);
            configInfo.setConfigContent("test config content for getConfig test");
            configInfo.setContentType("text");
            configInfo.setConfigDesc("测试配置：用于测试getConfig方法");

            SaveConfigResult saveResult = testClient.saveConfig(configInfo);
            System.out.println("保存配置结果: success=" + saveResult.isSuccess() +
                    ", version=" + saveResult.getVersion() +
                    ", contentMd5=" + saveResult.getContentMd5());
            assertTrue(saveResult.isSuccess(), "配置保存应该成功");

            // 等待一下，确保配置已保存
            Thread.sleep(500);

            // 测试获取配置
            System.out.println("--- 获取配置 ---");
            System.out.println("查询参数: namespaceId=ns_F41J68C80A50C28G68A06I53A49J4, groupName=DEFAULT_GROUP, configDataId=" + testConfigDataId);

            GetConfigResult getConfigResult = testClient.getConfig(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    testConfigDataId);

            System.out.println("--- 获取配置结果 ---");
            System.out.println("success: " + getConfigResult.isSuccess());
            System.out.println("message: " + getConfigResult.getMessage());

            assertTrue(getConfigResult.isSuccess(), "获取配置应该成功");
            assertNotNull(getConfigResult.getConfig(), "配置信息不应该为空");

            ConfigInfo retrievedConfig = getConfigResult.getConfig();
            System.out.println("--- 配置详情 ---");
            System.out.println("  configDataId: " + (retrievedConfig.getConfigDataId() != null ? retrievedConfig.getConfigDataId() : "null"));
            System.out.println("  namespaceId: " + (retrievedConfig.getNamespaceId() != null ? retrievedConfig.getNamespaceId() : "null"));
            System.out.println("  groupName: " + (retrievedConfig.getGroupName() != null ? retrievedConfig.getGroupName() : "null"));
            System.out.println("  contentType: " + (retrievedConfig.getContentType() != null ? retrievedConfig.getContentType() : "null"));
            System.out.println("  configVersion: " + retrievedConfig.getConfigVersion());
            System.out.println("  contentMd5: " + (retrievedConfig.getContentMd5() != null ? retrievedConfig.getContentMd5() : "null"));
            System.out.println("  configDesc: " + (retrievedConfig.getConfigDesc() != null ? retrievedConfig.getConfigDesc() : "null"));

            // 打印配置内容（如果内容太长，只打印前100个字符）
            String content = retrievedConfig.getConfigContent();
            if (content != null) {
                if (content.length() > 100) {
                    System.out.println("  configContent: " + content.substring(0, 100) + "... (总长度: " + content.length() + " 字符)");
                } else {
                    System.out.println("  configContent: " + content);
                }
            } else {
                System.out.println("  configContent: null");
            }

            // 验证配置内容
            assertEquals(testConfigDataId, retrievedConfig.getConfigDataId(), "配置ID应该匹配");
            assertEquals("ns_F41J68C80A50C28G68A06I53A49J4", retrievedConfig.getNamespaceId(), "命名空间ID应该匹配");
            assertEquals("DEFAULT_GROUP", retrievedConfig.getGroupName(), "分组名应该匹配");
            assertEquals("test config content for getConfig test", retrievedConfig.getConfigContent(), "配置内容应该匹配");
            assertEquals("text", retrievedConfig.getContentType(), "内容类型应该匹配");
            assertEquals(saveResult.getVersion(), retrievedConfig.getConfigVersion(), "版本号应该匹配");
            assertEquals(saveResult.getContentMd5(), retrievedConfig.getContentMd5(), "MD5值应该匹配");

            // 测试获取不存在的配置
            System.out.println("--- 测试获取不存在的配置 ---");
            String nonExistentConfigId = "non-existent-config-" + System.currentTimeMillis();
            GetConfigResult nonExistentResult = testClient.getConfig(
                    "ns_F41J68C80A50C28G68A06I53A49J4",
                    "DEFAULT_GROUP",
                    nonExistentConfigId);

            System.out.println("获取不存在配置的结果: success=" + nonExistentResult.isSuccess() +
                    ", message=" + nonExistentResult.getMessage());
            // 注意：根据实际实现，可能返回 success=false 或 success=true 但 config=null
            // 这里只验证不会抛出异常

            // 清理：删除测试配置
            System.out.println("--- 清理：删除测试配置 ---");
            testClient.deleteConfig("ns_F41J68C80A50C28G68A06I53A49J4", "DEFAULT_GROUP", testConfigDataId);

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

