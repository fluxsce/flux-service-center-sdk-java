package com.flux.servicecenter.client.internal;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.stream.ServiceCenterStreamGrpc;
import com.flux.servicecenter.stream.StreamProto;
import com.flux.servicecenter.stream.StreamProto.ClientMessage;
import com.flux.servicecenter.stream.StreamProto.ClientMessageType;
import com.flux.servicecenter.stream.StreamProto.ServerMessage;
import com.flux.servicecenter.stream.StreamProto.ServerMessageType;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Single v3 bidirectional stream: handshake, requestId matching, and push dispatch.
 *
 * <p>Cluster failover: {@link ServiceCenterConfig#getServerAddresses()} is tried in order.
 * A failed handshake or a dropped live stream advances the index to the next address.</p>
 */
public final class StreamSession implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(StreamSession.class);
    public static final String PROTOCOL_VERSION = "v3";

    private final ServiceCenterConfig config;
    private final Consumer<com.flux.servicecenter.registry.RegistryProto.ServiceChangeEvent> onServiceChange;
    private final Consumer<com.flux.servicecenter.config.ConfigProto.ConfigChangeEvent> onConfigChange;
    private final Consumer<Throwable> onDisconnect;

    private final Map<String, CompletableFuture<ServerMessage>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean open = new AtomicBoolean(false);

    private final String clientId = UUID.randomUUID().toString();
    private final AtomicInteger addressIndex = new AtomicInteger(0);

    private ManagedChannel channel;
    private StreamObserver<ClientMessage> outbound;
    private volatile String connectionId = "";

    public StreamSession(ServiceCenterConfig config,
                         Consumer<com.flux.servicecenter.registry.RegistryProto.ServiceChangeEvent> onServiceChange,
                         Consumer<com.flux.servicecenter.config.ConfigProto.ConfigChangeEvent> onConfigChange,
                         Consumer<Throwable> onDisconnect) {
        this.config = config;
        this.onServiceChange = onServiceChange;
        this.onConfigChange = onConfigChange;
        this.onDisconnect = onDisconnect;
    }

    public synchronized void connect() {
        if (open.get()) {
            return;
        }
        int attempts = Math.max(1, addressCount());
        RuntimeException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                connectOnce();
                return;
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last != null ? last : new IllegalStateException("connection failed");
    }

    private void connectOnce() {
        closeQuietly();
        channel = buildChannel();
        ServiceCenterStreamGrpc.ServiceCenterStreamStub stub = ServiceCenterStreamGrpc.newStub(channel);
        Metadata md = authMetadata();
        if (md != null) {
            stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md));
        }
        outbound = stub.connect(new StreamObserver<>() {
            @Override
            public void onNext(ServerMessage msg) {
                handleInbound(msg);
            }

            @Override
            public void onError(Throwable t) {
                LOG.warn("v3 stream error", t);
                failAll(t);
                markClosed(t);
            }

            @Override
            public void onCompleted() {
                IllegalStateException closed = new IllegalStateException("stream closed");
                failAll(closed);
                markClosed(closed);
            }
        });
        try {
            ServerMessage hs = request(handshakeMessage());
            if (hs.getMessageType() != ServerMessageType.SERVER_HANDSHAKE || !hs.getHandshake().getSuccess()) {
                throw new IllegalStateException("handshake failed: " + hs.getHandshake().getMessage());
            }
            connectionId = hs.getHandshake().getConnectionId();
            open.set(true);
        } catch (RuntimeException e) {
            rotateAddress();
            closeQuietly();
            throw e;
        }
    }

    public boolean isOpen() {
        return open.get();
    }

    public String connectionId() {
        return connectionId;
    }

    public ServerMessage request(ClientMessage message) {
        if (outbound == null) {
            throw new IllegalStateException("not connected");
        }
        CompletableFuture<ServerMessage> future = new CompletableFuture<>();
        pending.put(message.getRequestId(), future);
        synchronized (this) {
            outbound.onNext(message);
        }
        try {
            ServerMessage resp = future.get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);
            if (resp.getMessageType() == ServerMessageType.SERVER_ERROR) {
                throw new ServiceCenterException(resp.getError().getCode(), resp.getError().getMessage());
            }
            return resp;
        } catch (ServiceCenterException e) {
            throw e;
        } catch (Exception e) {
            pending.remove(message.getRequestId());
            throw new IllegalStateException("request timed out or failed: " + message.getMessageType(), e);
        }
    }

    public ClientMessage.Builder newRequest(ClientMessageType type) {
        return ClientMessage.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setMessageType(type);
    }

    public void sendPing() {
        if (!open.get() || outbound == null) {
            return;
        }
        ClientMessage ping = newRequest(ClientMessageType.CLIENT_PING)
                .setPing(StreamProto.ClientPing.newBuilder()
                        .setTimestamp(System.currentTimeMillis())
                        .setConnectionId(connectionId)
                        .build())
                .build();
        try {
            request(ping);
        } catch (RuntimeException e) {
            LOG.debug("ping failed: {}", e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        open.set(false);
        failAll(new IllegalStateException("client closed"));
        closeQuietly();
    }

    private void closeQuietly() {
        if (outbound != null) {
            try {
                outbound.onCompleted();
            } catch (Exception ignored) {
            }
            outbound = null;
        }
        if (channel != null) {
            channel.shutdownNow();
            channel = null;
        }
    }

    private void rotateAddress() {
        var list = config.getServerAddresses();
        if (list != null && list.size() > 1) {
            addressIndex.incrementAndGet();
        }
    }

    private void handleInbound(ServerMessage msg) {
        if (msg.getMessageType() == ServerMessageType.SERVER_SERVICE_CHANGE) {
            if (onServiceChange != null) {
                onServiceChange.accept(msg.getServiceChange());
            }
            return;
        }
        if (msg.getMessageType() == ServerMessageType.SERVER_CONFIG_CHANGE) {
            if (onConfigChange != null) {
                onConfigChange.accept(msg.getConfigChange());
            }
            return;
        }
        CompletableFuture<ServerMessage> future = pending.remove(msg.getRequestId());
        if (future != null) {
            future.complete(msg);
        }
    }

    private void failAll(Throwable t) {
        pending.forEach((id, f) -> f.completeExceptionally(t));
        pending.clear();
    }

    private void markClosed(Throwable t) {
        boolean wasOpen = open.getAndSet(false);
        if (wasOpen) {
            // Prefer the next cluster address after a live stream drops.
            rotateAddress();
            if (onDisconnect != null) {
                onDisconnect.accept(t);
            }
        }
    }

    private ClientMessage handshakeMessage() {
        StreamProto.ClientMetadata.Builder meta = StreamProto.ClientMetadata.newBuilder()
                .setClientId(clientId)
                .setSdkVersion("3.0.0")
                .setLanguage("Java")
                .setStartTime(System.currentTimeMillis());
        if (config.getMetadata() != null) {
            meta.putAllLabels(config.getMetadata());
        }
        return newRequest(ClientMessageType.CLIENT_HANDSHAKE)
                .setHandshake(StreamProto.ClientHandshake.newBuilder()
                        .setMetadata(meta)
                        .setNamespaceId(nullToEmpty(config.getNamespaceId()))
                        .setKeepAlive(true)
                        .setKeepAliveInterval((int) Math.max(1, config.getHeartbeatInterval() / 1000))
                        .setProtocolVersion(PROTOCOL_VERSION)
                        .build())
                .build();
    }

    private ManagedChannel buildChannel() {
        String target = firstAddress();
        String[] hp = target.split(":");
        String host = hp[0];
        int port = hp.length > 1 ? Integer.parseInt(hp[1]) : config.getServerPort();
        if (config.isEnableTls()) {
            try {
                var ssl = GrpcSslContexts.forClient();
                if (notBlank(config.getTlsCaPath())) {
                    ssl.trustManager(new File(config.getTlsCaPath()));
                }
                if (notBlank(config.getTlsCertPath()) && notBlank(config.getTlsKeyPath())) {
                    ssl.keyManager(new File(config.getTlsCertPath()), new File(config.getTlsKeyPath()));
                }
                return NettyChannelBuilder.forAddress(host, port)
                        .sslContext(ssl.build())
                        .keepAliveTime(config.getKeepAliveTime(), TimeUnit.MILLISECONDS)
                        .keepAliveTimeout(config.getKeepAliveTimeout(), TimeUnit.MILLISECONDS)
                        .keepAliveWithoutCalls(config.isKeepAliveWithoutCalls())
                        .maxInboundMessageSize(config.getMaxInboundMessageSize())
                        .build();
            } catch (Exception e) {
                throw new IllegalStateException("TLS initialization failed", e);
            }
        }
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(config.getKeepAliveTime(), TimeUnit.MILLISECONDS)
                .keepAliveTimeout(config.getKeepAliveTimeout(), TimeUnit.MILLISECONDS)
                .keepAliveWithoutCalls(config.isKeepAliveWithoutCalls())
                .maxInboundMessageSize(config.getMaxInboundMessageSize())
                .build();
    }

    private Metadata authMetadata() {
        String header = null;
        if (notBlank(config.getUserId()) && config.getPassword() != null) {
            String raw = config.getUserId() + ":" + config.getPassword();
            header = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        } else if (notBlank(config.getAuthToken())) {
            header = "Bearer " + config.getAuthToken();
        }
        if (header == null) {
            return null;
        }
        Metadata md = new Metadata();
        md.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), header);
        return md;
    }

    private int addressCount() {
        var list = config.getServerAddresses();
        return list == null || list.isEmpty() ? 1 : list.size();
    }

    private String firstAddress() {
        var list = config.getServerAddresses();
        if (list == null || list.isEmpty()) {
            return config.getServerHost() + ":" + config.getServerPort();
        }
        int idx = Math.floorMod(addressIndex.get(), list.size());
        return list.get(idx);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
