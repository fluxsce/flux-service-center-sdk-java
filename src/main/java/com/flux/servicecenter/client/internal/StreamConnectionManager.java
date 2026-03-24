package com.flux.servicecenter.client.internal;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.stream.StreamProto.*;
import com.flux.servicecenter.stream.ServiceCenterStreamGrpc;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 统一双向流连接管理器
 * 
 * <p>负责管理与服务端的双向流连接，处理所有的请求-响应和事件推送。</p>
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>建立和管理双向流连接</li>
 *   <li>发送握手和 Ping 心跳</li>
 *   <li>路由服务端消息到对应的处理器</li>
 *   <li>管理待处理的请求-响应</li>
 *   <li>自动重连</li>
 * </ul>
 */
public class StreamConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(StreamConnectionManager.class);
    
    // ========== 配置 ==========
    
    private final ServiceCenterConfig config;
    private final ManagedChannel channel;
    
    // ========== 连接状态 ==========
    
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicReference<String> connectionId = new AtomicReference<>();
    private final AtomicReference<String> clientId = new AtomicReference<>(UUID.randomUUID().toString());
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    
    // ========== gRPC Stub ==========
    
    private ServiceCenterStreamGrpc.ServiceCenterStreamStub asyncStub;
    private StreamObserver<ClientMessage> requestObserver;
    
    // ========== 请求管理 ==========
    
    /** 待处理的请求（requestId -> CompletableFuture） */
    private final Map<String, CompletableFuture<ServerMessage>> pendingRequests = new ConcurrentHashMap<>();
    
    /** 请求超时时间（毫秒） */
    private final long requestTimeoutMs;
    
    // ========== 监听器 ==========
    
    /** 握手成功监听器 */
    private Consumer<ServerHandshake> handshakeListener;
    
    /** 服务变更事件监听器 */
    private Consumer<com.flux.servicecenter.registry.RegistryProto.ServiceChangeEvent> serviceChangeListener;
    
    /** 配置变更事件监听器 */
    private Consumer<com.flux.servicecenter.config.ConfigProto.ConfigChangeEvent> configChangeListener;
    
    /** 关闭通知监听器 */
    private Consumer<ServerCloseNotification> closeListener;
    
    /** 错误监听器 */
    private Consumer<ErrorResponse> errorListener;
    
    // ========== 心跳 ==========
    
    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledFuture<?> heartbeatFuture;
    
    // ========== 认证 ==========
    
    /** 
     * 认证元数据
     * 
     * <p>包含认证令牌等元数据信息，用于在每次 gRPC 调用时附加到请求头中。
     * 如果配置中提供了认证信息，则会在连接时创建并设置。</p>
     */
    private Metadata authMetadata;
    
    // ========== 构造函数 ==========
    
    public StreamConnectionManager(ServiceCenterConfig config, ManagedChannel channel) {
        this.config = config;
        this.channel = channel;
        this.requestTimeoutMs = config.getRequestTimeout() * 1000L;
    }
    
    // ========== 连接管理 ==========
    
    /**
     * 建立双向流连接（线程安全）
     */
    public synchronized void connect() {
        if (connected.get()) {
            logger.warn("Stream already connected, skipping duplicate connection");
            return;
        }
        
        try {
            logger.info("Establishing bidirectional stream connection...");
            
            // 关闭旧的 stream（如果存在）
            closeOldStream();
            
            // 清理旧状态（重连时必须清理，否则 waitForConnection 会使用旧 connectionId）
            connectionId.set(null);
            pendingRequests.clear();
            connected.set(false);
            
            // 创建认证元数据（与 ConnectionManager 保持一致）
            createAuthMetadata();
            
            // 创建异步 Stub（如果有认证信息，则添加拦截器）
            if (authMetadata != null) {
                ClientInterceptor authInterceptor = MetadataUtils.newAttachHeadersInterceptor(authMetadata);
                asyncStub = ServiceCenterStreamGrpc.newStub(channel).withInterceptors(authInterceptor);
                logger.debug("Attached authentication interceptor to gRPC Stub");
            } else {
                asyncStub = ServiceCenterStreamGrpc.newStub(channel);
            }
            
            // 建立双向流
            requestObserver = asyncStub.connect(new StreamResponseObserver());
            
            // 发送握手
            sendHandshake();
            
            // 等待握手响应（最多等待 5 秒）
            if (!waitForConnection(5000)) {
                // 握手超时，清理状态
                closeOldStream();
                throw new RuntimeException("Handshake timeout");
            }
            
            // 注意：connected 标志已在 handleHandshake() 中设置为 true
            // 这样可以确保 handshakeListener 触发时，连接状态已经是 true
            
            // 启动 Ping 心跳
            startPingHeartbeat();
            
            logger.info("Bidirectional stream connection established successfully, connectionId: {}", connectionId.get());
            
        } catch (Exception e) {
            // 连接失败，清理状态
            connected.set(false);
            connectionId.set(null);
            
            // 停止可能已启动的心跳任务
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
                heartbeatFuture = null;
            }
            
            logger.error("Failed to establish bidirectional stream connection", e);
            lastError.set(e);
            throw new RuntimeException("Failed to establish bidirectional stream connection", e);
        }
    }
    
    /**
     * 关闭旧的 stream（避免旧连接干扰新连接）
     */
    private void closeOldStream() {
        if (requestObserver != null) {
            try {
                logger.debug("Closing old stream");
                requestObserver.onCompleted();
            } catch (Exception e) {
                logger.debug("Exception occurred while closing old stream (ignored): {}", e.getMessage());
            }
            requestObserver = null;
        }
    }
    
    /**
     * 等待连接建立
     */
    private boolean waitForConnection(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (connectionId.get() == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return connectionId.get() != null;
    }
    
    /**
     * 发送握手消息
     */
    private void sendHandshake() {
        ClientMetadata metadata = ClientMetadata.newBuilder()
            .setClientId(clientId.get())
            .setClientVersion("1.0.0")
            .setSdkVersion("1.0.0")
            .setLanguage("Java")
            .setStartTime(System.currentTimeMillis())
            .putLabels("env", System.getProperty("env", "production"))
            .putLabels("app", System.getProperty("app.name", "unknown"))
            .build();
        
        // 使用配置的心跳间隔（毫秒转秒）
        int keepAliveIntervalSeconds = (int) (config.getHeartbeatInterval() / 1000);
        if (keepAliveIntervalSeconds <= 0) {
            keepAliveIntervalSeconds = 5; // 默认 5 秒
        }
        
        ClientHandshake handshake = ClientHandshake.newBuilder()
            .setMetadata(metadata)
            .setNamespaceId(config.getNamespaceId())
            .setKeepAlive(true)
            .setKeepAliveInterval(keepAliveIntervalSeconds)
            .addSubscribeTypes("registry")
            .addSubscribeTypes("config")
            .build();
        
        String requestId = UUID.randomUUID().toString();
        ClientMessage message = ClientMessage.newBuilder()
            .setRequestId(requestId)
            .setMessageType(ClientMessageType.CLIENT_HANDSHAKE)
            .setHandshake(handshake)
            .build();
        
        sendMessage(message);
        logger.info("Handshake message sent, requestId: {}, keepAlive interval: {}s", requestId, keepAliveIntervalSeconds);
    }
    
    /**
     * 启动 Ping 心跳
     */
    private void startPingHeartbeat() {
        if (heartbeatExecutor == null) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "stream-ping-heartbeat");
                t.setDaemon(true);
                return t;
            });
        }
        
        // 取消旧的心跳任务（避免重复启动）
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            heartbeatFuture.cancel(false);
            logger.debug("Cancelled old Ping heartbeat task");
        }
        
        // 使用配置的心跳间隔（毫秒转秒）
        long intervalSeconds = config.getHeartbeatInterval() / 1000;
        if (intervalSeconds <= 0) {
            intervalSeconds = 5; // 默认 5 秒
        }
        
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            // 检查连接状态，断开时静默跳过
            if (!connected.get()) {
                logger.trace("Connection disconnected, skipping Ping heartbeat");
                return;
            }
            
            try {
                sendPing();
            } catch (IllegalStateException e) {
                // 连接断开异常，静默处理
                logger.trace("Connection disconnected during Ping heartbeat");
            } catch (Exception e) {
                logger.warn("Failed to send Ping heartbeat: {}", e.getMessage());
                logger.debug("Ping heartbeat failure details", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        logger.info("Ping heartbeat started, interval: {} seconds", intervalSeconds);
    }
    
    /**
     * 发送 Ping 消息
     */
    private void sendPing() {
        ClientPing ping = ClientPing.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .build();
        
        ClientMessage message = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_PING)
            .setPing(ping)
            .build();
        
        sendMessage(message);
        logger.trace("Ping heartbeat sent");
    }
    
    /**
     * 发送消息（线程安全）
     */
    private synchronized void sendMessage(ClientMessage message) {
        if (requestObserver == null) {
            throw new IllegalStateException("Bidirectional stream not connected");
        }
        requestObserver.onNext(message);
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (!connected.getAndSet(false)) {
            return;
        }
        
        logger.info("Closing bidirectional stream connection...");
        
        // 停止心跳
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
        }
        
        // 完成所有待处理的请求
        pendingRequests.forEach((requestId, future) -> 
            future.completeExceptionally(new RuntimeException("Connection closed")));
        pendingRequests.clear();
        
        // 关闭流
        if (requestObserver != null) {
            try {
                requestObserver.onCompleted();
            } catch (Exception e) {
                logger.warn("Failed to close request stream", e);
            }
        }
        
        connectionId.set(null);
        logger.info("Bidirectional stream connection closed");
    }
    
    // ========== 请求发送 ==========
    
    /**
     * 发送请求并等待响应
     * 
     * @param message 客户端消息
     * @return 服务端响应
     * @throws TimeoutException 如果请求超时
     * @throws RuntimeException 如果请求失败
     */
    public ServerMessage sendRequest(ClientMessage message) throws TimeoutException {
        if (!connected.get()) {
            throw new IllegalStateException("Bidirectional stream not connected");
        }
        
        String requestId = message.getRequestId();
        CompletableFuture<ServerMessage> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);
        
        try {
            // 发送消息
            sendMessage(message);
            
            // 等待响应
            return future.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            
        } catch (TimeoutException e) {
            pendingRequests.remove(requestId);
            throw e;
        } catch (InterruptedException e) {
            pendingRequests.remove(requestId);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        } catch (ExecutionException e) {
            pendingRequests.remove(requestId);
            throw new RuntimeException("Request failed", e.getCause());
        }
    }
    
    /**
     * 发送请求（不等待响应）
     * 
     * @param message 客户端消息
     */
    public void sendRequestAsync(ClientMessage message) {
        if (!connected.get()) {
            throw new IllegalStateException("Bidirectional stream not connected");
        }
        sendMessage(message);
    }
    
    // ========== 响应观察者 ==========
    
    /**
     * 服务端消息观察者
     */
    private class StreamResponseObserver implements StreamObserver<ServerMessage> {
        
        @Override
        public void onNext(ServerMessage message) {
            try {
                handleServerMessage(message);
            } catch (Exception e) {
                logger.error("处理服务端消息失败", e);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            logger.error("Bidirectional stream error occurred", t);
            lastError.set(t);
            connected.set(false);
            
            // 尝试重连
            reconnect();
        }
        
        @Override
        public void onCompleted() {
            logger.info("Bidirectional stream completed (server closed connection)");
            connected.set(false);
            
            // 服务端正常关闭也应该尝试重连（例如服务端重启场景）
            reconnect();
        }
    }
    
    /**
     * 处理服务端消息
     */
    private void handleServerMessage(ServerMessage message) {
        String requestId = message.getRequestId();
        
        logger.debug("Received server message: type={}, requestId={}", 
            message.getMessageType(), requestId);
        
        // 处理响应消息（有 requestId）
        if (requestId != null && !requestId.isEmpty()) {
            CompletableFuture<ServerMessage> future = pendingRequests.remove(requestId);
            if (future != null) {
                logger.debug("Completed pending request: requestId={}", requestId);
                future.complete(message);
                return;
            } else {
                logger.debug("Pending request not found, treating as push message: requestId={}", requestId);
            }
        }
        
        // 处理服务端主动推送（无 requestId）或未匹配的响应
        switch (message.getMessageType()) {
            case SERVER_HANDSHAKE:
                logger.debug("Processing handshake response");
                handleHandshake(message.getHandshake());
                break;
                
            case SERVER_PONG:
                handlePong(message.getPong());
                break;
                
            case SERVER_SERVICE_CHANGE:
                handleServiceChange(message.getServiceChange());
                break;
                
            case SERVER_CONFIG_CHANGE:
                handleConfigChange(message.getConfigChange());
                break;
                
            case SERVER_CLOSE:
                handleCloseNotification(message.getClose());
                break;
                
            case SERVER_ERROR:
                handleError(message.getError());
                break;
                
            default:
                logger.warn("Unknown server message type: {}", message.getMessageType());
        }
    }
    
    /**
     * 处理握手响应
     */
    private void handleHandshake(ServerHandshake handshake) {
        if (handshake.getSuccess()) {
            connectionId.set(handshake.getConnectionId());
            
            // 握手成功，立即标记连接成功（在调用 handshakeListener 之前）
            // 这样 restoreStateAfterReconnect() 就能正常调用业务方法
            connected.set(true);
            
            logger.info("Handshake successful, connectionId: {}, tenantId: {}", 
                handshake.getConnectionId(), handshake.getTenantId());
            
            if (handshakeListener != null) {
                handshakeListener.accept(handshake);
            }
        } else {
            logger.error("Handshake failed: {}", handshake.getMessage());
            lastError.set(new RuntimeException("Handshake failed: " + handshake.getMessage()));
        }
    }
    
    /**
     * 处理 Pong 响应
     */
    private void handlePong(ServerPong pong) {
        long rtt = System.currentTimeMillis() - pong.getClientTimestamp();
        logger.trace("收到 Pong 响应，RTT: {} ms", rtt);
    }
    
    /**
     * 处理服务变更事件
     */
    private void handleServiceChange(com.flux.servicecenter.registry.RegistryProto.ServiceChangeEvent event) {
        logger.debug("Received service change event: {}, service: {}", 
            event.getEventType(), event.getServiceName());
        
        if (serviceChangeListener != null) {
            serviceChangeListener.accept(event);
        }
    }
    
    /**
     * 处理配置变更事件
     */
    private void handleConfigChange(com.flux.servicecenter.config.ConfigProto.ConfigChangeEvent event) {
        logger.debug("Received config change event: {}, config: {}", 
            event.getEventType(), event.getConfigDataId());
        
        if (configChangeListener != null) {
            configChangeListener.accept(event);
        }
    }
    
    /**
     * 处理关闭通知
     */
    private void handleCloseNotification(ServerCloseNotification notification) {
        logger.warn("Received server close notification: {}, message: {}, grace period: {}s", 
            notification.getReason(), notification.getMessage(), notification.getGracePeriod());
        
        if (closeListener != null) {
            closeListener.accept(notification);
        }
        
        // 延迟关闭
        ScheduledExecutorService delayedExecutor = Executors.newSingleThreadScheduledExecutor();
        delayedExecutor.schedule(() -> {
            close();
            delayedExecutor.shutdown();
        }, notification.getGracePeriod(), TimeUnit.SECONDS);
    }
    
    /**
     * 处理错误响应
     */
    private void handleError(ErrorResponse error) {
        logger.error("Received server error: {}, message: {}", error.getCode(), error.getMessage());
        
        if (errorListener != null) {
            errorListener.accept(error);
        }
    }
    
    // ========== 重连 ==========
    
    /**
     * 自动重连（支持指数退避重试，支持无限重试）
     * 
     * <p>重连策略与 ConfigCenterManager 保持一致：</p>
     * <ul>
     *   <li>当 maxReconnectAttempts &lt; 0 时，无限重试</li>
     *   <li>当 maxReconnectAttempts &gt;= 0 时，最多重试指定次数</li>
     *   <li>使用指数退避算法，最大延迟 30 秒</li>
     * </ul>
     */
    private void reconnect() {
        if (reconnecting.getAndSet(true)) {
            logger.debug("Reconnection already in progress, skipping");
            return;
        }
        
        // 异步重连（避免阻塞 gRPC 线程）
        CompletableFuture.runAsync(() -> {
            int attempts = 0;
            long baseDelay = config.getReconnectInterval();
            final long maxBackoffMs = 30000; // 最大退避延迟 30 秒
            int maxReconnectAttempts = config.getMaxReconnectAttempts();
            
            // 支持无限重试：maxReconnectAttempts < 0 表示无限重试
            while (maxReconnectAttempts < 0 || attempts < maxReconnectAttempts) {
                try {
                    // 计算退避延迟（指数退避）
                    long delay = baseDelay * (1L << Math.min(attempts, 10)); // 2^attempts，防止溢出
                    delay = Math.min(delay, maxBackoffMs);
                    
                    attempts++;
                    
                    if (maxReconnectAttempts < 0) {
                        logger.info("Starting auto-reconnect... attempt {} (infinite retry mode), waiting {}ms", attempts, delay);
                    } else {
                        logger.info("Starting auto-reconnect... attempt {}/{}, waiting {}ms", attempts, maxReconnectAttempts, delay);
                    }
                    
                    Thread.sleep(delay);
                    
                    // 尝试连接
                    connect();
                    logger.info("Auto-reconnect successful after {} attempt(s)", attempts);
                    return; // 连接成功，退出重试循环
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Auto-reconnect interrupted");
                    return;
                } catch (Exception e) {
                    if (maxReconnectAttempts < 0) {
                        logger.warn("Auto-reconnect failed (attempt {}), will continue retrying: {}", attempts, e.getMessage());
                    } else if (attempts < maxReconnectAttempts) {
                        logger.warn("Auto-reconnect failed (attempt {}/{}), will continue retrying: {}", 
                                attempts, maxReconnectAttempts, e.getMessage());
                    } else {
                        logger.error("Auto-reconnect failed (max retry attempts {} reached), giving up", maxReconnectAttempts, e);
                    }
                }
            }
        }).whenComplete((result, ex) -> {
            reconnecting.set(false);
        });
    }
    
    // ========== 监听器设置 ==========
    
    public void setHandshakeListener(Consumer<ServerHandshake> listener) {
        this.handshakeListener = listener;
    }
    
    public void setServiceChangeListener(Consumer<com.flux.servicecenter.registry.RegistryProto.ServiceChangeEvent> listener) {
        this.serviceChangeListener = listener;
    }
    
    public void setConfigChangeListener(Consumer<com.flux.servicecenter.config.ConfigProto.ConfigChangeEvent> listener) {
        this.configChangeListener = listener;
    }
    
    public void setCloseListener(Consumer<ServerCloseNotification> listener) {
        this.closeListener = listener;
    }
    
    public void setErrorListener(Consumer<ErrorResponse> listener) {
        this.errorListener = listener;
    }
    
    // ========== 认证方法 ==========
    
    /**
     * 创建认证元数据（与 ConnectionManager 保持一致）
     * 
     * <p>在 connect() 时调用，根据配置创建认证信息：</p>
     * <ul>
     *   <li>优先使用用户ID密码认证（Basic Auth）</li>
     *   <li>否则使用令牌认证（Bearer Token）</li>
     * </ul>
     */
    private void createAuthMetadata() {
        authMetadata = new Metadata();
        
        // 优先使用用户ID密码认证，如果未设置则使用令牌认证
        if (config.getUserId() != null && !config.getUserId().isEmpty() &&
            config.getPassword() != null && !config.getPassword().isEmpty()) {
            // 用户ID密码认证：使用 Basic Auth
            // 注意：这里使用 userId（用户ID），而不是 userName（用户名）
            // userId 是数据库中 HUB_USER 表的唯一主键标识
            String credentials = config.getUserId() + ":" + config.getPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            authMetadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Basic " + encoded);
            logger.debug("Using user ID/password authentication: userId={}", config.getUserId());
        } else if (config.getAuthToken() != null && !config.getAuthToken().isEmpty()) {
            // 令牌认证：使用 Bearer Token
            authMetadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Bearer " + config.getAuthToken());
            logger.debug("Using token authentication");
        } else {
            // 没有认证信息
            authMetadata = null;
            logger.debug("No authentication configured");
        }
    }
    
    /**
     * 获取认证元数据拦截器（与 ConnectionManager 保持一致）
     * 
     * @return 元数据拦截器，如果没有认证信息则返回 null
     */
    public ClientInterceptor getMetadataInterceptor() {
        if (authMetadata == null) {
            return null;
        }
        return MetadataUtils.newAttachHeadersInterceptor(authMetadata);
    }
    
    // ========== Getter ==========
    
    public boolean isConnected() {
        return connected.get();
    }
    
    public String getConnectionId() {
        return connectionId.get();
    }
    
    public Throwable getLastError() {
        return lastError.get();
    }
}

