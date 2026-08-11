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
 * 单向 TLS / mTLS 场景集成测试（Stream 客户端）。
 *
 * <p>依赖 {@code GATEWAY_ROOT}，自动拉起 {@code servicecenter-testd}。</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("com.flux.servicecenter.client.ServiceCenterTlsScenarioTest#isE2EEnabled")
public class ServiceCenterTlsScenarioTest {
    private static final Logger logger = LoggerFactory.getLogger(ServiceCenterTlsScenarioTest.class);

    private static final Pattern READY_TLS = Pattern.compile(
            "SERVICE_CENTER_READY host=(\\S+) port=(\\d+) namespace=(\\S+) group=(\\S+) auth=\\S+ tls=on ca=(\\S+)");
    private static final Pattern READY_MTLS = Pattern.compile(
            "SERVICE_CENTER_READY host=(\\S+) port=(\\d+) namespace=(\\S+) group=(\\S+) auth=\\S+ " +
                    "tls=mtls ca=(\\S+) clientCert=(\\S+) clientKey=(\\S+)");

    private Process tlsProcess;
    private Process mtlsProcess;

    private String tlsHost;
    private int tlsPort;
    private String tlsNamespace;
    private String tlsGroup;
    private String tlsCa;

    private String mtlsHost;
    private int mtlsPort;
    private String mtlsNamespace;
    private String mtlsGroup;
    private String mtlsCa;
    private String mtlsClientCert;
    private String mtlsClientKey;

    static boolean isE2EEnabled() {
        String root = System.getenv("GATEWAY_ROOT");
        return root != null && !root.isBlank() && new File(root).isDirectory();
    }

    @BeforeAll
    void setUp() throws Exception {
        startTlsTestd();
        startMtlsTestd();
    }

    @AfterAll
    void tearDown() {
        destroy(tlsProcess);
        destroy(mtlsProcess);
    }

    @Test
    @Order(1)
    @DisplayName("明文客户端连接 TLS 服务端应失败")
    void plaintextShouldFail() {
        ServiceCenterConfig config = baseConfig(tlsHost, tlsPort, tlsNamespace, tlsGroup)
                .setEnableTls(false);
        StreamBasedServiceCenterClient client = new StreamBasedServiceCenterClient(config);
        try {
            Assertions.assertThrows(Exception.class, client::connect);
        } finally {
            safeClose(client);
        }
    }

    @Test
    @Order(2)
    @DisplayName("信任 CA 后 TLS 可注册")
    void tlsWithCaOk() throws Exception {
        ServiceCenterConfig config = baseConfig(tlsHost, tlsPort, tlsNamespace, tlsGroup)
                .setEnableTls(true)
                .setTlsCaPath(tlsCa);
        StreamBasedServiceCenterClient client = new StreamBasedServiceCenterClient(config);
        try {
            client.connect();
            Assertions.assertTrue(client.isConnected());
            RegisterServiceResult reg = register(client, "java-tls-ok");
            Assertions.assertTrue(reg.isSuccess(), () -> "register failed: " + reg.getMessage());
            logger.info("TLS register ok nodeId={} ca={}", reg.getNodeId(), tlsCa);
        } finally {
            safeClose(client);
        }
    }

    @Test
    @Order(3)
    @DisplayName("mTLS 无客户端证书应失败")
    void mtlsWithoutClientCertShouldFail() {
        ServiceCenterConfig config = baseConfig(mtlsHost, mtlsPort, mtlsNamespace, mtlsGroup)
                .setEnableTls(true)
                .setTlsCaPath(mtlsCa);
        StreamBasedServiceCenterClient client = new StreamBasedServiceCenterClient(config);
        try {
            Assertions.assertThrows(Exception.class, client::connect);
        } finally {
            safeClose(client);
        }
    }

    @Test
    @Order(4)
    @DisplayName("mTLS 携带客户端证书可注册")
    void mtlsWithClientCertOk() throws Exception {
        ServiceCenterConfig config = baseConfig(mtlsHost, mtlsPort, mtlsNamespace, mtlsGroup)
                .setEnableTls(true)
                .setTlsCaPath(mtlsCa)
                .setTlsCertPath(mtlsClientCert)
                .setTlsKeyPath(mtlsClientKey);
        StreamBasedServiceCenterClient client = new StreamBasedServiceCenterClient(config);
        try {
            client.connect();
            Assertions.assertTrue(client.isConnected());
            RegisterServiceResult reg = register(client, "java-mtls-ok");
            Assertions.assertTrue(reg.isSuccess(), () -> "register failed: " + reg.getMessage());
            logger.info("mTLS register ok nodeId={}", reg.getNodeId());
        } finally {
            safeClose(client);
        }
    }

    private ServiceCenterConfig baseConfig(String host, int port, String ns, String group) {
        return new ServiceCenterConfig()
                .setServerHost(host)
                .setServerPort(port)
                .setNamespaceId(ns)
                .setGroupName(group)
                .setHeartbeatInterval(3000)
                .setReconnectInterval(500)
                .setMaxReconnectAttempts(0)
                .setRequestTimeout(5000);
    }

    private RegisterServiceResult register(StreamBasedServiceCenterClient client, String serviceName) {
        String ns = serviceName.startsWith("java-mtls") ? mtlsNamespace : tlsNamespace;
        String group = serviceName.startsWith("java-mtls") ? mtlsGroup : tlsGroup;

        ServiceInfo service = new ServiceInfo();
        service.setNamespaceId(ns);
        service.setGroupName(group);
        service.setServiceName(serviceName);
        service.setServiceType("HTTP");

        NodeInfo node = new NodeInfo();
        node.setNamespaceId(ns);
        node.setGroupName(group);
        node.setServiceName(serviceName);
        node.setIpAddress("127.0.0.1");
        node.setPortNumber(19301);
        node.setWeight(100);
        node.setHealthyStatus("HEALTHY");
        node.setInstanceStatus("UP");
        return client.registerService(service, node);
    }

    private void startTlsTestd() throws Exception {
        Ready r = startTestd("true", "false", READY_TLS);
        tlsProcess = r.process;
        tlsHost = r.host;
        tlsPort = r.port;
        tlsNamespace = r.namespace;
        tlsGroup = r.group;
        tlsCa = r.ca;
        logger.info("TLS testd ready {}:{} ca={}", tlsHost, tlsPort, tlsCa);
    }

    private void startMtlsTestd() throws Exception {
        Ready r = startTestd("false", "true", READY_MTLS);
        mtlsProcess = r.process;
        mtlsHost = r.host;
        mtlsPort = r.port;
        mtlsNamespace = r.namespace;
        mtlsGroup = r.group;
        mtlsCa = r.ca;
        mtlsClientCert = r.clientCert;
        mtlsClientKey = r.clientKey;
        logger.info("mTLS testd ready {}:{} ca={}", mtlsHost, mtlsPort, mtlsCa);
    }

    private Ready startTestd(String enableTls, String enableMtls, Pattern readyPattern) throws Exception {
        String gatewayRoot = System.getenv("GATEWAY_ROOT");
        ProcessBuilder pb = new ProcessBuilder("go", "run", "./cmd/servicecenter-testd");
        pb.directory(new File(gatewayRoot));
        pb.redirectErrorStream(true);
        pb.environment().put("SC_E2E_ENABLE_TLS", enableTls);
        pb.environment().put("SC_E2E_ENABLE_MTLS", enableMtls);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        String line;
        while (System.currentTimeMillis() < deadline) {
            line = reader.readLine();
            if (line == null) {
                break;
            }
            logger.info("[testd] {}", line);
            Matcher m = readyPattern.matcher(line);
            if (m.find()) {
                Ready r = new Ready();
                r.process = process;
                r.host = m.group(1);
                r.port = Integer.parseInt(m.group(2));
                r.namespace = m.group(3);
                r.group = m.group(4);
                r.ca = m.group(5);
                if (m.groupCount() >= 7) {
                    r.clientCert = m.group(6);
                    r.clientKey = m.group(7);
                }
                Thread drain = new Thread(() -> {
                    try {
                        String l;
                        while ((l = reader.readLine()) != null) {
                            logger.debug("[testd] {}", l);
                        }
                    } catch (Exception ignored) {
                        // ended
                    }
                }, "tls-testd-log");
                drain.setDaemon(true);
                drain.start();
                return r;
            }
        }
        process.destroyForcibly();
        Assertions.fail("SERVICE_CENTER_READY tls not received");
        return null;
    }

    private static void destroy(Process p) {
        if (p == null || !p.isAlive()) {
            return;
        }
        p.destroy();
        try {
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
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

    private static final class Ready {
        Process process;
        String host;
        int port;
        String namespace;
        String group;
        String ca;
        String clientCert;
        String clientKey;
    }
}
