package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.NodeInfo;
import com.flux.servicecenter.model.RegisterNodeResult;
import com.flux.servicecenter.model.ServiceChangeEvent;
import com.flux.servicecenter.model.ServiceInfo;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程内 gRPC：订阅、对端下线、断线重连、地址切换与并发注册。
 */
class StreamReliabilityTest {
    private FakeV3Stream fake;
    private Server server;
    private ServiceCenterClient consumer;
    private ServiceCenterClient provider;

    @BeforeEach
    void start() throws IOException {
        fake = new FakeV3Stream();
        server = ServerBuilder.forPort(0).addService(fake).build().start();
    }

    @AfterEach
    void stop() {
        if (consumer != null) {
            consumer.close();
        }
        if (provider != null) {
            provider.close();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void subscriberSeesPeerRegisterAndUnregister() throws Exception {
        RecordingListener listener = new RecordingListener();
        consumer = newClient();
        consumer.connect();
        consumer.subscribeService("ns", "g", "svc", listener);

        provider = newClient();
        provider.connect();
        NodeInfo node = node("10.0.0.8", 8080);
        RegisterNodeResult reg = provider.registerNode(node);
        assertTrue(reg.isSuccess());

        ServiceChangeEvent added = listener.await(e -> e.getEventType() == ServiceChangeEvent.EventType.NODE_ADDED, 3);
        assertEquals(reg.getNodeId(), added.getChangedNode().getNodeId());

        assertTrue(provider.unregisterNode(reg.getNodeId()).isSuccess());
        ServiceChangeEvent removed = listener.await(e -> e.getEventType() == ServiceChangeEvent.EventType.NODE_REMOVED, 3);
        assertEquals(reg.getNodeId(), removed.getChangedNode().getNodeId());
    }

    @Test
    void subscriberSeesProviderCrashWithoutUnregister() throws Exception {
        RecordingListener listener = new RecordingListener();
        consumer = newClient();
        consumer.connect();
        consumer.subscribeService("ns", "g", "svc", listener);

        provider = newClient();
        provider.connect();
        RegisterNodeResult reg = provider.registerNode(node("10.0.0.9", 8081));
        listener.await(e -> e.getEventType() == ServiceChangeEvent.EventType.NODE_ADDED, 3);

        fake.dropProviders();
        ServiceChangeEvent evicted = listener.await(e -> e.getEventType() == ServiceChangeEvent.EventType.NODE_REMOVED, 3);
        assertEquals(reg.getNodeId(), evicted.getChangedNode().getNodeId());
        assertTrue(consumer.isConnected());
    }

    @Test
    void reconnectRestoresSubscribeAndReregistersNode() throws Exception {
        RecordingListener listener = new RecordingListener();
        consumer = newClient();
        consumer.connect();
        consumer.subscribeService("ns", "g", "svc", listener);

        provider = newClient();
        provider.connect();
        provider.registerNode(node("10.0.0.10", 8082));
        listener.await(e -> e.getEventType() == ServiceChangeEvent.EventType.NODE_ADDED, 3);

        int before = fake.handshakeCount();
        listener.events.clear();
        fake.dropAll();
        assertTrue(listener.disconnected.await(3, TimeUnit.SECONDS));
        awaitTrue(() -> consumer.isConnected() && provider.isConnected(), 5, "clients did not reconnect");
        assertTrue(listener.reconnected.await(3, TimeUnit.SECONDS));
        assertTrue(fake.handshakeCount() > before);
        ServiceChangeEvent restored = listener.await(e -> e.getEventType() == ServiceChangeEvent.EventType.NODE_ADDED, 5);
        assertEquals("svc", restored.getServiceName());
    }

    @Test
    void concurrentRegisterOnOneConnection() throws Exception {
        provider = newClient();
        provider.connect();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger ok = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(16);
        for (int i = 0; i < 16; i++) {
            int port = 9100 + i;
            pool.execute(() -> {
                try {
                    RegisterNodeResult r = provider.registerNode(node("10.1.0.1", port));
                    if (r.isSuccess() && r.getNodeId() != null && !r.getNodeId().isBlank()) {
                        ok.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(8, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(16, ok.get());
        assertEquals(16, provider.getRegisteredNodeIds().size());
    }

    @Test
    void connectFailsOverDeadAddress() throws Exception {
        int dead = freePort();
        ServiceCenterConfig config = new ServiceCenterConfig()
                .setServerHost("127.0.0.1")
                .setServerPort(server.getPort())
                .setServerAddress("127.0.0.1:" + dead + ",127.0.0.1:" + server.getPort())
                .setNamespaceId("ns")
                .setGroupName("g")
                .setRequestTimeout(800)
                .setReconnectInterval(100)
                .setHeartbeatInterval(200)
                .setMaxReconnectAttempts(8);
        consumer = new ServiceCenterClient(config);
        consumer.connect();
        assertTrue(consumer.isConnected());
        assertEquals(1, fake.handshakeCount());
    }

    @Test
    void registerServiceNotifiesSubscriber() throws Exception {
        RecordingListener listener = new RecordingListener();
        consumer = newClient();
        consumer.connect();
        consumer.subscribeService("ns", "g", "svc", listener);

        provider = newClient();
        provider.connect();
        ServiceInfo service = new ServiceInfo("ns", "g", "svc");
        NodeInfo node = node("10.0.0.11", 8090);
        assertTrue(provider.registerService(service, node).isSuccess());
        ServiceChangeEvent added = listener.await(e -> e.getEventType() == ServiceChangeEvent.EventType.NODE_ADDED, 3);
        assertEquals("svc", added.getServiceName());
    }

    private ServiceCenterClient newClient() {
        return new ServiceCenterClient(new ServiceCenterConfig()
                .setServerHost("127.0.0.1")
                .setServerPort(server.getPort())
                .setNamespaceId("ns")
                .setGroupName("g")
                .setRequestTimeout(2000)
                .setReconnectInterval(100)
                .setHeartbeatInterval(200)
                .setMaxReconnectAttempts(20));
    }

    private static NodeInfo node(String ip, int port) {
        NodeInfo n = new NodeInfo(ip, port);
        n.setNamespaceId("ns");
        n.setGroupName("g");
        n.setServiceName("svc");
        n.setEphemeral("Y");
        return n;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitTrue(java.util.function.BooleanSupplier cond, int seconds, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(30);
        }
        throw new AssertionError(message);
    }

    private static final class RecordingListener implements ServiceChangeListener {
        final List<ServiceChangeEvent> events = new CopyOnWriteArrayList<>();
        final CountDownLatch disconnected = new CountDownLatch(1);
        final CountDownLatch reconnected = new CountDownLatch(1);

        @Override
        public void onServiceChange(ServiceChangeEvent event) {
            events.add(event);
        }

        @Override
        public void onDisconnected(Throwable cause) {
            disconnected.countDown();
        }

        @Override
        public void onReconnected() {
            reconnected.countDown();
        }

        ServiceChangeEvent await(Predicate<ServiceChangeEvent> match, int seconds) throws InterruptedException {
            long deadline = System.currentTimeMillis() + seconds * 1000L;
            while (System.currentTimeMillis() < deadline) {
                for (ServiceChangeEvent event : events) {
                    if (match.test(event)) {
                        return event;
                    }
                }
                Thread.sleep(20);
            }
            throw new AssertionError("event not received: " + events);
        }
    }
}
