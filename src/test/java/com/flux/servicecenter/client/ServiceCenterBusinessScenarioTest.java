package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.ConfigChangeEvent;
import com.flux.servicecenter.model.ConfigHistory;
import com.flux.servicecenter.model.ConfigInfo;
import com.flux.servicecenter.model.GetConfigResult;
import com.flux.servicecenter.model.GetServiceResult;
import com.flux.servicecenter.model.NodeInfo;
import com.flux.servicecenter.model.OperationResult;
import com.flux.servicecenter.model.RegisterNodeResult;
import com.flux.servicecenter.model.RegisterServiceResult;
import com.flux.servicecenter.model.RollbackConfigResult;
import com.flux.servicecenter.model.SaveConfigResult;
import com.flux.servicecenter.model.ServiceChangeEvent;
import com.flux.servicecenter.model.ServiceInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 真实业务场景集成测试（Stream 客户端）。
 *
 * <p>服务端使用 gateway 的 SQLite 测试守护进程 {@code cmd/servicecenter-testd}。</p>
 *
 * <p>启用方式（任一即可）：</p>
 * <ul>
 *   <li>设置 {@code GATEWAY_ROOT} 指向 gateway 仓库根目录（测试自动 {@code go run ./cmd/servicecenter-testd}）</li>
 *   <li>或设置 {@code SERVICE_CENTER_E2E=true}，并提供 {@code SERVICE_CENTER_HOST/PORT/NAMESPACE/GROUP}</li>
 * </ul>
 *
 * <p>示例：</p>
 * <pre>{@code
 * set GATEWAY_ROOT=E:\vscode\gateway
 * mvn -Dtest=ServiceCenterBusinessScenarioTest test
 * }</pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("com.flux.servicecenter.client.ServiceCenterBusinessScenarioTest#isE2EEnabled")
public class ServiceCenterBusinessScenarioTest {
    private static final Logger logger = LoggerFactory.getLogger(ServiceCenterBusinessScenarioTest.class);

    private static final Pattern READY = Pattern.compile(
            "SERVICE_CENTER_READY host=(\\S+) port=(\\d+) namespace=(\\S+) group=(\\S+)");

    private Process testdProcess;
    private StreamBasedServiceCenterClient client;
    private String host;
    private int port;
    private String namespaceId;
    private String groupName;
    private String registeredNodeId;

    static boolean isE2EEnabled() {
        if (truthy(System.getenv("SERVICE_CENTER_E2E"))) {
            return true;
        }
        String root = System.getenv("GATEWAY_ROOT");
        return root != null && !root.isBlank() && new File(root).isDirectory();
    }

    @BeforeAll
    void setUp() throws Exception {
        if (truthy(System.getenv("SERVICE_CENTER_E2E"))
                && System.getenv("SERVICE_CENTER_HOST") != null
                && System.getenv("SERVICE_CENTER_PORT") != null) {
            host = System.getenv("SERVICE_CENTER_HOST");
            port = Integer.parseInt(System.getenv("SERVICE_CENTER_PORT"));
            namespaceId = envOr("SERVICE_CENTER_NAMESPACE", "ns_e2e");
            groupName = envOr("SERVICE_CENTER_GROUP", "e2e-group");
            logger.info("Using external service center {}:{} ns={} group={}", host, port, namespaceId, groupName);
        } else {
            startTestdFromGatewayRoot();
        }

        ServiceCenterConfig config = new ServiceCenterConfig()
                .setServerHost(host)
                .setServerPort(port)
                .setEnableTls(false)
                .setNamespaceId(namespaceId)
                .setGroupName(groupName)
                .setHeartbeatInterval(3000)
                .setReconnectInterval(2000)
                .setMaxReconnectAttempts(5)
                .setRequestTimeout(15000);

        client = new StreamBasedServiceCenterClient(config);
        client.connect();
        Assertions.assertTrue(client.isConnected(), "client should be connected");
        logger.info("Stream client connected");
    }

    @AfterAll
    void tearDown() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("close client failed: {}", e.getMessage());
            }
        }
        if (testdProcess != null && testdProcess.isAlive()) {
            testdProcess.destroy();
            try {
                testdProcess.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                testdProcess.destroyForcibly();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("注册 + 心跳后发现 + getService 含 nodes")
    void registerHeartbeatDiscover() throws Exception {
        ServiceInfo service = new ServiceInfo();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setServiceName("java-e2e-order");
        service.setServiceType("HTTP");

        NodeInfo node = new NodeInfo();
        node.setNamespaceId(namespaceId);
        node.setGroupName(groupName);
        node.setServiceName("java-e2e-order");
        node.setIpAddress("127.0.0.1");
        node.setPortNumber(19081);
        node.setWeight(100);
        node.setHealthyStatus("HEALTHY");
        node.setInstanceStatus("UP");

        RegisterServiceResult reg = client.registerService(service, node);
        Assertions.assertTrue(reg.isSuccess(), () -> "register failed: " + reg.getMessage());
        Assertions.assertNotNull(reg.getNodeId());
        registeredNodeId = reg.getNodeId();

        Thread.sleep(3500);

        List<NodeInfo> nodes = client.discoverNodes(namespaceId, groupName, "java-e2e-order", true);
        Assertions.assertFalse(nodes.isEmpty(), "discover should return healthy nodes");

        GetServiceResult get = client.getService(namespaceId, groupName, "java-e2e-order");
        Assertions.assertTrue(get.isSuccess(), () -> "getService failed: " + get.getMessage());
        Assertions.assertNotNull(get.getNodes(), "getService nodes must not be null");
        Assertions.assertFalse(get.getNodes().isEmpty(), "getService must include nodes");
        logger.info("discover/getService ok, nodeId={}, nodeCount={}", registeredNodeId, get.getNodes().size());
    }

    @Test
    @Order(2)
    @DisplayName("订阅服务变更推送")
    void subscribeServicePush() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ServiceChangeEvent> eventRef = new AtomicReference<>();

        String subId = client.subscribeService(namespaceId, groupName, "java-e2e-pay",
                new ServiceChangeListener() {
                    @Override
                    public void onServiceChange(ServiceChangeEvent event) {
                        eventRef.set(event);
                        latch.countDown();
                    }
                });
        Assertions.assertNotNull(subId);
        Thread.sleep(300);

        ServiceInfo service = new ServiceInfo();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setServiceName("java-e2e-pay");
        service.setServiceType("HTTP");

        NodeInfo node = new NodeInfo();
        node.setNamespaceId(namespaceId);
        node.setGroupName(groupName);
        node.setServiceName("java-e2e-pay");
        node.setIpAddress("127.0.0.1");
        node.setPortNumber(19082);
        node.setWeight(100);
        node.setHealthyStatus("HEALTHY");
        node.setInstanceStatus("UP");

        RegisterServiceResult reg = client.registerService(service, node);
        Assertions.assertTrue(reg.isSuccess(), () -> "register pay failed: " + reg.getMessage());

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS), "timeout waiting service change push");
        Assertions.assertNotNull(eventRef.get());
        logger.info("service push ok: type={}", eventRef.get().getEventType());

        client.unsubscribe(subId);
    }

    @Test
    @Order(3)
    @DisplayName("配置发布 + Watch 推送 + 拉取")
    void configSaveWatchGet() throws Exception {
        String dataId = "java-e2e-app.yaml";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConfigChangeEvent> eventRef = new AtomicReference<>();

        String watchId = client.watchConfig(namespaceId, groupName, dataId, new ConfigChangeListener() {
            @Override
            public void onConfigChange(ConfigChangeEvent event) {
                eventRef.set(event);
                latch.countDown();
            }
        });
        Assertions.assertNotNull(watchId);
        Thread.sleep(300);

        ConfigInfo config = new ConfigInfo();
        config.setNamespaceId(namespaceId);
        config.setGroupName(groupName);
        config.setConfigDataId(dataId);
        config.setContentType("yaml");
        config.setConfigContent("app:\n  name: java-e2e\n");
        config.setConfigDesc("java e2e config");

        SaveConfigResult save = client.saveConfig(config);
        Assertions.assertTrue(save.isSuccess(), () -> "saveConfig failed: " + save.getMessage());

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS), "timeout waiting config change push");
        Assertions.assertNotNull(eventRef.get());
        Assertions.assertNotNull(eventRef.get().getConfig());

        GetConfigResult get = client.getConfig(namespaceId, groupName, dataId);
        Assertions.assertTrue(get.isSuccess());
        Assertions.assertTrue(get.getConfig().getConfigContent().contains("java-e2e"));
        logger.info("config watch/get ok");

        client.unwatch(watchId);
    }

    @Test
    @Order(4)
    @DisplayName("独立 RegisterNode + UnregisterService 整服务注销")
    void registerNodeAndUnregisterService() throws Exception {
        String serviceName = "java-e2e-regnode";
        ServiceInfo service = new ServiceInfo();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setServiceName(serviceName);
        service.setServiceType("HTTP");

        RegisterServiceResult svc = client.registerService(service, null);
        Assertions.assertTrue(svc.isSuccess(), () -> "registerService(no node): " + svc.getMessage());

        NodeInfo node = new NodeInfo();
        node.setNamespaceId(namespaceId);
        node.setGroupName(groupName);
        node.setServiceName(serviceName);
        node.setIpAddress("127.0.0.1");
        node.setPortNumber(19084);
        node.setWeight(100);
        node.setHealthyStatus("HEALTHY");
        node.setInstanceStatus("UP");

        RegisterNodeResult nodeResult = client.registerNode(node);
        Assertions.assertTrue(nodeResult.isSuccess(), () -> "registerNode: " + nodeResult.getMessage());
        Assertions.assertNotNull(nodeResult.getNodeId());

        List<NodeInfo> discovered = client.discoverNodes(namespaceId, groupName, serviceName, true);
        Assertions.assertTrue(discovered.stream().anyMatch(n -> nodeResult.getNodeId().equals(n.getNodeId())));

        OperationResult unreg = client.unregisterService(namespaceId, groupName, serviceName, null);
        Assertions.assertTrue(unreg.isSuccess(), () -> "unregisterService: " + unreg.getMessage());

        List<NodeInfo> after = client.discoverNodes(namespaceId, groupName, serviceName, false);
        Assertions.assertTrue(after.isEmpty(), "whole service unregister should remove all nodes");
        logger.info("registerNode + unregisterService ok, nodeId={}", nodeResult.getNodeId());
    }

    @Test
    @Order(5)
    @DisplayName("Stream CLIENT_SUBSCRIBE_NAMESPACE 推送")
    void subscribeNamespacePush() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ServiceChangeEvent> eventRef = new AtomicReference<>();

        String subId = client.subscribeNamespace(namespaceId, groupName, new ServiceChangeListener() {
            @Override
            public void onServiceChange(ServiceChangeEvent event) {
                if ("java-e2e-ns-push".equals(event.getServiceName())) {
                    eventRef.set(event);
                    latch.countDown();
                }
            }
        });
        Assertions.assertNotNull(subId);
        Thread.sleep(400);

        ServiceInfo service = new ServiceInfo();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setServiceName("java-e2e-ns-push");
        service.setServiceType("HTTP");

        NodeInfo node = new NodeInfo();
        node.setNamespaceId(namespaceId);
        node.setGroupName(groupName);
        node.setServiceName("java-e2e-ns-push");
        node.setIpAddress("127.0.0.1");
        node.setPortNumber(19085);
        node.setWeight(100);
        node.setHealthyStatus("HEALTHY");
        node.setInstanceStatus("UP");

        RegisterServiceResult reg = client.registerService(service, node);
        Assertions.assertTrue(reg.isSuccess(), () -> "register for ns subscribe: " + reg.getMessage());

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS), "timeout waiting namespace push");
        Assertions.assertNotNull(eventRef.get());
        logger.info("namespace stream push ok: type={}", eventRef.get().getEventType());
        client.unsubscribe(subId);
    }

    @Test
    @Order(6)
    @DisplayName("配置 List/History/Rollback/Delete")
    void configListHistoryRollbackDelete() throws Exception {
        String dataId = "java-e2e-lifecycle.yaml";
        String v1 = "app:\n  version: v1\n";
        String v2 = "app:\n  version: v2\n";

        ConfigInfo c1 = new ConfigInfo();
        c1.setNamespaceId(namespaceId);
        c1.setGroupName(groupName);
        c1.setConfigDataId(dataId);
        c1.setContentType("yaml");
        c1.setConfigContent(v1);
        SaveConfigResult s1 = client.saveConfig(c1);
        Assertions.assertTrue(s1.isSuccess(), () -> "save v1: " + s1.getMessage());

        ConfigInfo c2 = new ConfigInfo();
        c2.setNamespaceId(namespaceId);
        c2.setGroupName(groupName);
        c2.setConfigDataId(dataId);
        c2.setContentType("yaml");
        c2.setConfigContent(v2);
        SaveConfigResult s2 = client.saveConfig(c2);
        Assertions.assertTrue(s2.isSuccess(), () -> "save v2: " + s2.getMessage());

        List<ConfigInfo> listed = client.listConfigs(namespaceId, groupName, null, 1, 50);
        Assertions.assertTrue(listed.stream().anyMatch(c -> dataId.equals(c.getConfigDataId())),
                "listConfigs should include " + dataId);

        List<ConfigHistory> history = client.getConfigHistory(namespaceId, groupName, dataId, 1, 10);
        Assertions.assertTrue(history.size() >= 2, "history size should be >= 2, got " + history.size());

        long targetVersion = history.stream()
                .filter(h -> v1.equals(h.getConfigContent()))
                .map(ConfigHistory::getConfigVersion)
                .findFirst()
                .orElse(history.get(history.size() - 1).getConfigVersion());

        RollbackConfigResult rb = client.rollbackConfig(namespaceId, groupName, dataId, String.valueOf(targetVersion));
        Assertions.assertTrue(rb.isSuccess(), () -> "rollback: " + rb.getMessage());

        GetConfigResult afterRb = client.getConfig(namespaceId, groupName, dataId);
        Assertions.assertTrue(afterRb.isSuccess());
        Assertions.assertEquals(v1, afterRb.getConfig().getConfigContent());

        OperationResult del = client.deleteConfig(namespaceId, groupName, dataId);
        Assertions.assertTrue(del.isSuccess(), () -> "deleteConfig: " + del.getMessage());

        List<ConfigInfo> afterDel = client.listConfigs(namespaceId, groupName, null, 1, 50);
        Assertions.assertTrue(afterDel.stream().noneMatch(c -> dataId.equals(c.getConfigDataId())),
                "deleted config should not appear in list");
        logger.info("config lifecycle ok history={} rollbackTo={}", history.size(), targetVersion);
    }

    @Test
    @Order(7)
    @DisplayName("close 优雅注销后发现不到原节点")
    void closeGracefulUnregister() throws Exception {
        Assumptions.assumeTrue(registeredNodeId != null && !registeredNodeId.isEmpty(),
                "order node not registered in previous test");

        ServiceCenterConfig config = new ServiceCenterConfig()
                .setServerHost(host)
                .setServerPort(port)
                .setEnableTls(false)
                .setNamespaceId(namespaceId)
                .setGroupName(groupName)
                .setHeartbeatInterval(3000)
                .setRequestTimeout(15000);

        StreamBasedServiceCenterClient ephemeral = new StreamBasedServiceCenterClient(config);
        ephemeral.connect();

        ServiceInfo service = new ServiceInfo();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setServiceName("java-e2e-temp");
        service.setServiceType("HTTP");

        NodeInfo node = new NodeInfo();
        node.setNamespaceId(namespaceId);
        node.setGroupName(groupName);
        node.setServiceName("java-e2e-temp");
        node.setIpAddress("127.0.0.1");
        node.setPortNumber(19083);
        node.setWeight(100);
        node.setHealthyStatus("HEALTHY");
        node.setInstanceStatus("UP");

        RegisterServiceResult reg = ephemeral.registerService(service, node);
        Assertions.assertTrue(reg.isSuccess());
        String nodeId = reg.getNodeId();

        List<NodeInfo> before = client.discoverNodes(namespaceId, groupName, "java-e2e-temp", false);
        Assertions.assertTrue(before.stream().anyMatch(n -> nodeId.equals(n.getNodeId())));

        ephemeral.close();
        Thread.sleep(800);

        List<NodeInfo> after = client.discoverNodes(namespaceId, groupName, "java-e2e-temp", false);
        Assertions.assertTrue(after.stream().noneMatch(n -> nodeId.equals(n.getNodeId())),
                "node should be unregistered after client.close()");
        logger.info("close graceful unregister ok, nodeId={}", nodeId);
    }

    private void startTestdFromGatewayRoot() throws Exception {
        String gatewayRoot = System.getenv("GATEWAY_ROOT");
        Assertions.assertNotNull(gatewayRoot, "GATEWAY_ROOT is required when SERVICE_CENTER_E2E host/port not set");

        ProcessBuilder pb = new ProcessBuilder("go", "run", "./cmd/servicecenter-testd");
        pb.directory(new File(gatewayRoot));
        pb.redirectErrorStream(true);
        testdProcess = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(testdProcess.getInputStream(), StandardCharsets.UTF_8));

        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        String line;
        while (System.currentTimeMillis() < deadline) {
            line = reader.readLine();
            if (line == null) {
                break;
            }
            logger.info("[testd] {}", line);
            Matcher m = READY.matcher(line);
            if (m.find()) {
                host = m.group(1);
                port = Integer.parseInt(m.group(2));
                namespaceId = m.group(3);
                groupName = m.group(4);
                Thread drain = new Thread(() -> {
                    try {
                        String l;
                        while ((l = reader.readLine()) != null) {
                            logger.debug("[testd] {}", l);
                        }
                    } catch (Exception ignored) {
                        // process ended
                    }
                }, "servicecenter-testd-log");
                drain.setDaemon(true);
                drain.start();
                return;
            }
        }
        testdProcess.destroyForcibly();
        Assertions.fail("SERVICE_CENTER_READY not received from servicecenter-testd within timeout");
    }

    private static boolean truthy(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase();
        return "1".equals(s) || "true".equals(s) || "yes".equals(s);
    }

    private static String envOr(String key, String defaultValue) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? defaultValue : v;
    }
}
