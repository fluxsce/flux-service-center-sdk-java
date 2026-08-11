package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.ConfigChangeEvent;
import com.flux.servicecenter.model.ConfigInfo;
import com.flux.servicecenter.model.NodeInfo;
import com.flux.servicecenter.model.RegisterServiceResult;
import com.flux.servicecenter.model.SaveConfigResult;
import com.flux.servicecenter.model.ServiceChangeEvent;
import com.flux.servicecenter.model.ServiceInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 客户端并发业务场景：多线程注册发现 + 多配置订阅。
 *
 * <p>启用条件同 {@link ServiceCenterBusinessScenarioTest}（GATEWAY_ROOT 或 SERVICE_CENTER_E2E）。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("com.flux.servicecenter.client.ServiceCenterBusinessScenarioTest#isE2EEnabled")
public class ServiceCenterConcurrentScenarioTest {
    private static final Logger logger = LoggerFactory.getLogger(ServiceCenterConcurrentScenarioTest.class);
    private static final Pattern READY = Pattern.compile(
            "SERVICE_CENTER_READY host=(\\S+) port=(\\d+) namespace=(\\S+) group=(\\S+)");

    private Process testdProcess;
    private String host;
    private int port;
    private String namespaceId;
    private String groupName;
    private final Map<String, Object> report = new LinkedHashMap<>();

    @BeforeAll
    void setUp() throws Exception {
        if (truthy(System.getenv("SERVICE_CENTER_E2E"))
                && System.getenv("SERVICE_CENTER_HOST") != null
                && System.getenv("SERVICE_CENTER_PORT") != null) {
            host = System.getenv("SERVICE_CENTER_HOST");
            port = Integer.parseInt(System.getenv("SERVICE_CENTER_PORT"));
            namespaceId = envOr("SERVICE_CENTER_NAMESPACE", "ns_e2e");
            groupName = envOr("SERVICE_CENTER_GROUP", "e2e-group");
        } else {
            startTestd();
        }
        report.put("title", "Java Stream Client Concurrent Scenario Report");
        report.put("endpoint", host + ":" + port);
        report.put("namespace", namespaceId);
        report.put("group", groupName);
        report.put("startedAt", java.time.Instant.now().toString());
    }

    @AfterAll
    void tearDown() throws Exception {
        report.put("finishedAt", java.time.Instant.now().toString());
        Path out = Path.of("target", "e2e-reports", "latest-java-concurrent-scenario.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, toJson(report), StandardCharsets.UTF_8);
        logger.info("Java concurrent report written: {}", out.toAbsolutePath());

        if (testdProcess != null && testdProcess.isAlive()) {
            testdProcess.destroy();
            testdProcess.waitFor(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("多线程注册发现（20 线程 x 5 服务 x 2 节点）")
    void multiThreadRegisterDiscover() throws Exception {
        final int workers = 20;
        final int servicesPerWorker = 5;
        final int nodesPerService = 2;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger discoverHits = new AtomicInteger();

        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            final int workerId = w;
            futures.add(pool.submit(() -> {
                StreamBasedServiceCenterClient c = newClient();
                try {
                    c.connect();
                    for (int s = 0; s < servicesPerWorker; s++) {
                        String svc = String.format("jthr-%02d-svc-%02d", workerId, s);
                        for (int n = 0; n < nodesPerService; n++) {
                            ServiceInfo service = new ServiceInfo();
                            service.setNamespaceId(namespaceId);
                            service.setGroupName(groupName);
                            service.setServiceName(svc);
                            service.setServiceType("HTTP");

                            NodeInfo node = new NodeInfo();
                            node.setNamespaceId(namespaceId);
                            node.setGroupName(groupName);
                            node.setServiceName(svc);
                            node.setIpAddress(String.format("12.%d.%d.%d", workerId + 1, s + 1, n + 1));
                            node.setPortNumber(50000 + workerId * 100 + s * 10 + n);
                            node.setWeight(100);
                            node.setHealthyStatus("HEALTHY");
                            node.setInstanceStatus("UP");

                            RegisterServiceResult reg = c.registerService(service, node);
                            if (reg != null && reg.isSuccess()) {
                                success.incrementAndGet();
                            } else {
                                failed.incrementAndGet();
                            }
                        }
                        List<NodeInfo> nodes = c.discoverNodes(namespaceId, groupName, svc, true);
                        if (nodes != null && nodes.size() >= nodesPerService) {
                            discoverHits.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                    }
                } finally {
                    try {
                        c.close();
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        int expectedDiscover = workers * servicesPerWorker;
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("name", "multi_thread_register_discover");
        scenario.put("passed", failed.get() == 0 && discoverHits.get() == expectedDiscover);
        scenario.put("durationMs", durationMs);
        scenario.put("workers", workers);
        scenario.put("services", expectedDiscover);
        scenario.put("nodesPerService", nodesPerService);
        scenario.put("successOps", success.get());
        scenario.put("failedOps", failed.get());
        scenario.put("discoverHits", discoverHits.get());
        scenario.put("opsPerSecond", durationMs == 0 ? 0 : success.get() * 1000.0 / durationMs);
        report.put("multi_thread_register_discover", scenario);

        logger.info("multiThreadRegisterDiscover success={} discover={}/{} failed={} {}ms",
                success.get(), discoverHits.get(), expectedDiscover, failed.get(), durationMs);
        Assertions.assertEquals(0, failed.get());
        Assertions.assertEquals(expectedDiscover, discoverHits.get());
    }

    @Test
    @DisplayName("配置多订阅 + 并发发布（8 订阅者 x 15 配置）")
    void multiConfigWatchPublish() throws Exception {
        final int watchers = 8;
        final int configs = 15;
        AtomicInteger updates = new AtomicInteger();
        CountDownLatch watchersReady = new CountDownLatch(watchers);
        List<StreamBasedServiceCenterClient> clients = new ArrayList<>();
        List<String> watchIds = new ArrayList<>();

        for (int w = 0; w < watchers; w++) {
            StreamBasedServiceCenterClient c = newClient();
            c.connect();
            clients.add(c);
            for (int i = 0; i < configs; i++) {
                String dataId = String.format("jcfg-%03d.yaml", i);
                String watchId = c.watchConfig(namespaceId, groupName, dataId, new ConfigChangeListener() {
                    @Override
                    public void onConfigChange(ConfigChangeEvent event) {
                        if (event != null && event.getConfig() != null
                                && event.getConfig().getConfigContent() != null
                                && !event.getConfig().getConfigContent().isEmpty()) {
                            updates.incrementAndGet();
                        }
                    }
                });
                watchIds.add(watchId);
            }
            watchersReady.countDown();
        }
        Assertions.assertTrue(watchersReady.await(5, TimeUnit.SECONDS));
        Thread.sleep(500);

        long start = System.nanoTime();
        StreamBasedServiceCenterClient publisher = newClient();
        publisher.connect();
        int publishOK = 0;
        for (int i = 0; i < configs; i++) {
            String dataId = String.format("jcfg-%03d.yaml", i);
            ConfigInfo cfg = new ConfigInfo();
            cfg.setNamespaceId(namespaceId);
            cfg.setGroupName(groupName);
            cfg.setConfigDataId(dataId);
            cfg.setContentType("yaml");
            cfg.setConfigContent("app:\n  id: " + i + "\n");
            SaveConfigResult save = publisher.saveConfig(cfg);
            if (save != null && save.isSuccess()) {
                publishOK++;
            }
        }

        int expectedMin = (int) (watchers * configs * 0.9);
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline && updates.get() < expectedMin) {
            Thread.sleep(50);
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("name", "multi_config_watch_publish");
        scenario.put("passed", publishOK == configs && updates.get() >= expectedMin);
        scenario.put("durationMs", durationMs);
        scenario.put("watchers", watchers);
        scenario.put("configs", configs);
        scenario.put("publishOK", publishOK);
        scenario.put("configUpdates", updates.get());
        scenario.put("minUpdates", expectedMin);
        report.put("multi_config_watch_publish", scenario);

        logger.info("multiConfigWatchPublish publishOK={} updates={} min={} {}ms",
                publishOK, updates.get(), expectedMin, durationMs);

        for (String id : watchIds) {
            // best-effort
        }
        publisher.close();
        for (StreamBasedServiceCenterClient c : clients) {
            c.close();
        }

        Assertions.assertEquals(configs, publishOK);
        Assertions.assertTrue(updates.get() >= expectedMin,
                "updates=" + updates.get() + " min=" + expectedMin);
    }

    @Test
    @DisplayName("服务订阅推送（注册 20 个服务）")
    void serviceSubscribePush() throws Exception {
        final int services = 20;
        AtomicInteger events = new AtomicInteger();
        StreamBasedServiceCenterClient subscriber = newClient();
        subscriber.connect();

        // 先订一批服务名，再注册同名服务
        List<String> subIds = new ArrayList<>();
        for (int i = 0; i < services; i++) {
            String svc = String.format("jpush-%03d", i);
            String id = subscriber.subscribeService(namespaceId, groupName, svc, new ServiceChangeListener() {
                @Override
                public void onServiceChange(ServiceChangeEvent event) {
                    events.incrementAndGet();
                }
            });
            subIds.add(id);
        }
        Thread.sleep(400);

        StreamBasedServiceCenterClient registrar = newClient();
        registrar.connect();
        int regOK = 0;
        long start = System.nanoTime();
        for (int i = 0; i < services; i++) {
            String svc = String.format("jpush-%03d", i);
            ServiceInfo service = new ServiceInfo();
            service.setNamespaceId(namespaceId);
            service.setGroupName(groupName);
            service.setServiceName(svc);
            service.setServiceType("HTTP");
            NodeInfo node = new NodeInfo();
            node.setNamespaceId(namespaceId);
            node.setGroupName(groupName);
            node.setServiceName(svc);
            node.setIpAddress("127.0.0.1");
            node.setPortNumber(51000 + i);
            node.setWeight(100);
            node.setHealthyStatus("HEALTHY");
            node.setInstanceStatus("UP");
            RegisterServiceResult reg = registrar.registerService(service, node);
            if (reg != null && reg.isSuccess()) {
                regOK++;
            }
        }
        int minEvents = (int) (services * 0.8);
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline && events.get() < minEvents) {
            Thread.sleep(50);
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("name", "service_subscribe_push");
        scenario.put("passed", regOK == services && events.get() >= minEvents);
        scenario.put("durationMs", durationMs);
        scenario.put("services", services);
        scenario.put("registerOK", regOK);
        scenario.put("pushEvents", events.get());
        scenario.put("minEvents", minEvents);
        report.put("service_subscribe_push", scenario);

        logger.info("serviceSubscribePush regOK={} events={} min={} {}ms",
                regOK, events.get(), minEvents, durationMs);

        registrar.close();
        for (String id : subIds) {
            subscriber.unsubscribe(id);
        }
        subscriber.close();

        Assertions.assertEquals(services, regOK);
        Assertions.assertTrue(events.get() >= minEvents);
    }

    private StreamBasedServiceCenterClient newClient() {
        ServiceCenterConfig config = new ServiceCenterConfig()
                .setServerHost(host)
                .setServerPort(port)
                .setEnableTls(false)
                .setNamespaceId(namespaceId)
                .setGroupName(groupName)
                .setHeartbeatInterval(5000)
                .setRequestTimeout(20000)
                .setMaxReconnectAttempts(3);
        return new StreamBasedServiceCenterClient(config);
    }

    private void startTestd() throws Exception {
        String gatewayRoot = System.getenv("GATEWAY_ROOT");
        Assertions.assertNotNull(gatewayRoot);
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
            Matcher m = READY.matcher(line);
            if (m.find()) {
                host = m.group(1);
                port = Integer.parseInt(m.group(2));
                namespaceId = m.group(3);
                groupName = m.group(4);
                Thread drain = new Thread(() -> {
                    try {
                        while (reader.readLine() != null) {
                            // drain
                        }
                    } catch (Exception ignored) {
                        // end
                    }
                }, "testd-drain");
                drain.setDaemon(true);
                drain.start();
                return;
            }
        }
        testdProcess.destroyForcibly();
        Assertions.fail("testd not ready");
    }

    private static boolean truthy(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase();
        return "1".equals(s) || "true".equals(s) || "yes".equals(s);
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (i++ > 0) {
                sb.append(",\n");
            }
            sb.append("  ").append(quote(e.getKey())).append(": ");
            appendValue(sb, e.getValue(), "  ");
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object v, String indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            sb.append(quote((String) v));
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (i++ > 0) {
                    sb.append(",\n");
                }
                sb.append(indent).append("  ").append(quote(e.getKey())).append(": ");
                appendValue(sb, e.getValue(), indent + "  ");
            }
            sb.append("\n").append(indent).append("}");
        } else {
            sb.append(quote(String.valueOf(v)));
        }
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
