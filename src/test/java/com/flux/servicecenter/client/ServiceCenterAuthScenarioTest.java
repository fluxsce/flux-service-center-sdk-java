package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.model.NodeInfo;
import com.flux.servicecenter.model.RegisterServiceResult;
import com.flux.servicecenter.model.ServiceInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 认证开启场景：Basic / Bearer API Token 正反路径（Stream 客户端）。
 *
 * <p>自动拉起 gateway {@code servicecenter-testd} 并设置 {@code SC_E2E_ENABLE_AUTH=true}。</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("com.flux.servicecenter.client.ServiceCenterAuthScenarioTest#isE2EEnabled")
public class ServiceCenterAuthScenarioTest {
    private static final Logger logger = LoggerFactory.getLogger(ServiceCenterAuthScenarioTest.class);

    private static final Pattern READY = Pattern.compile(
            "SERVICE_CENTER_READY host=(\\S+) port=(\\d+) namespace=(\\S+) group=(\\S+) auth=on " +
                    "user=(\\S+) password=(\\S+) token=(\\S+) jwtSecret=(\\S+) jwtIssuer=(\\S+)");

    private Process testdProcess;
    private String host;
    private int port;
    private String namespaceId;
    private String groupName;
    private String userId;
    private String password;
    private String apiToken;

    static boolean isE2EEnabled() {
        String root = System.getenv("GATEWAY_ROOT");
        return root != null && !root.isBlank() && new File(root).isDirectory();
    }

    @BeforeAll
    void setUp() throws Exception {
        startAuthTestd();
        Assertions.assertNotNull(userId);
        Assertions.assertNotNull(apiToken);
        logger.info("Auth testd ready {}:{} user={} tokenLen={}", host, port, userId, apiToken.length());
    }

    @AfterAll
    void tearDown() {
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
    @DisplayName("无认证连接应失败")
    void rejectWithoutCredentials() {
        StreamBasedServiceCenterClient client = newClient(null, null, null);
        try {
            Assertions.assertThrows(Exception.class, client::connect);
        } finally {
            safeClose(client);
        }
    }

    @Test
    @Order(2)
    @DisplayName("错误 Basic 应失败")
    void rejectWrongBasic() {
        StreamBasedServiceCenterClient client = newClient(userId, "wrong-pass", null);
        try {
            Assertions.assertThrows(Exception.class, client::connect);
        } finally {
            safeClose(client);
        }
    }

    @Test
    @Order(3)
    @DisplayName("正确 Basic 可注册服务")
    void acceptBasic() throws Exception {
        StreamBasedServiceCenterClient client = newClient(userId, password, null);
        try {
            client.connect();
            Assertions.assertTrue(client.isConnected());
            RegisterServiceResult reg = registerOnce(client, "java-auth-basic");
            Assertions.assertTrue(reg.isSuccess(), () -> "register failed: " + reg.getMessage());
            logger.info("Basic auth register ok nodeId={}", reg.getNodeId());
        } finally {
            safeClose(client);
        }
    }

    @Test
    @Order(4)
    @DisplayName("错误 API Token 应失败")
    void rejectWrongToken() {
        StreamBasedServiceCenterClient client = newClient(null, null, "bad-token-value");
        try {
            Assertions.assertThrows(Exception.class, client::connect);
        } finally {
            safeClose(client);
        }
    }

    @Test
    @Order(5)
    @DisplayName("正确 API Token 可注册服务")
    void acceptApiToken() throws Exception {
        StreamBasedServiceCenterClient client = newClient(null, null, apiToken);
        try {
            client.connect();
            Assertions.assertTrue(client.isConnected());
            RegisterServiceResult reg = registerOnce(client, "java-auth-token");
            Assertions.assertTrue(reg.isSuccess(), () -> "register failed: " + reg.getMessage());
            logger.info("API token register ok nodeId={}", reg.getNodeId());
        } finally {
            safeClose(client);
        }
    }

    private StreamBasedServiceCenterClient newClient(String user, String pass, String token) {
        ServiceCenterConfig config = new ServiceCenterConfig()
                .setServerHost(host)
                .setServerPort(port)
                .setEnableTls(false)
                .setNamespaceId(namespaceId)
                .setGroupName(groupName)
                .setHeartbeatInterval(3000)
                .setReconnectInterval(500)
                .setMaxReconnectAttempts(0)
                .setRequestTimeout(3000);
        if (user != null) {
            config.setUserId(user);
        }
        if (pass != null) {
            config.setPassword(pass);
        }
        if (token != null) {
            config.setAuthToken(token);
        }
        return new StreamBasedServiceCenterClient(config);
    }

    private RegisterServiceResult registerOnce(StreamBasedServiceCenterClient client, String serviceName) {
        ServiceInfo service = new ServiceInfo();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setServiceName(serviceName);
        service.setServiceType("HTTP");

        NodeInfo node = new NodeInfo();
        node.setNamespaceId(namespaceId);
        node.setGroupName(groupName);
        node.setServiceName(serviceName);
        node.setIpAddress("127.0.0.1");
        node.setPortNumber(19101);
        node.setWeight(100);
        node.setHealthyStatus("HEALTHY");
        node.setInstanceStatus("UP");
        return client.registerService(service, node);
    }

    private void startAuthTestd() throws Exception {
        String gatewayRoot = System.getenv("GATEWAY_ROOT");
        ProcessBuilder pb = new ProcessBuilder("go", "run", "./cmd/servicecenter-testd");
        pb.directory(new File(gatewayRoot));
        pb.redirectErrorStream(true);
        pb.environment().put("SC_E2E_ENABLE_AUTH", "true");
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
                userId = m.group(5);
                password = m.group(6);
                apiToken = m.group(7);
                Thread drain = new Thread(() -> {
                    try {
                        String l;
                        while ((l = reader.readLine()) != null) {
                            logger.debug("[testd] {}", l);
                        }
                    } catch (Exception ignored) {
                        // process ended
                    }
                }, "auth-testd-log");
                drain.setDaemon(true);
                drain.start();
                return;
            }
        }
        testdProcess.destroyForcibly();
        Assertions.fail("SERVICE_CENTER_READY auth=on not received");
    }

    private static void safeClose(StreamBasedServiceCenterClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            logger.warn("close failed: {}", e.getMessage());
        }
    }
}
