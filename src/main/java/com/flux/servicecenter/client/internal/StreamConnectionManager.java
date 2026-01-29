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
            logger.warn("双向流已连接，跳过重复连接");
            return;
        }
        
        try {
            logger.info("正在建立双向流连接...");
            
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
                logger.debug("已附加认证拦截器到 gRPC Stub");
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
                throw new RuntimeException("握手超时");
            }
            
            // 注意：connected 标志已在 handleHandshake() 中设置为 true
            // 这样可以确保 handshakeListener 触发时，连接状态已经是 true
            
            // 启动 Ping 心跳
            startPingHeartbeat();
            
            logger.info("双向流连接建立成功，connectionId: {}", connectionId.get());
            
        } catch (Exception e) {
            // 连接失败，清理状态
            connected.set(false);
            connectionId.set(null);
            
            // 停止可能已启动的心跳任务
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
                heartbeatFuture = null;
            }
            
            logger.error("建立双向流连接失败", e);
            lastError.set(e);
            throw new RuntimeException("建立双向流连接失败", e);
        }
    }
    
    /**
     * 关闭旧的 stream（避免旧连接干扰新连接）
     */
    private void closeOldStream() {
        if (requestObserver != null) {
            try {
                logger.debug("关闭旧的 stream");
                requestObserver.onCompleted();
            } catch (Exception e) {
                logger.debug("关闭旧 stream 时发生异常（忽略）: {}", e.getMessage());
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
        logger.info("已发送握手消息, requestId: {}, 心跳间隔: {}秒", requestId, keepAliveIntervalSeconds);
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
        
        // 使用配置的心跳间隔（毫秒转秒）
        long intervalSeconds = config.getHeartbeatInterval() / 1000;
        if (intervalSeconds <= 0) {
            intervalSeconds = 5; // 默认 5 秒
        }
        
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            // 检查连接状态，断开时静默跳过
            if (!connected.get()) {
                logger.trace("连接已断开，跳过 Ping 心跳");
                return;
            }
            
            try {
                sendPing();
            } catch (IllegalStateException e) {
                // 连接断开异常，静默处理
                logger.trace("Ping 心跳时连接已断开");
            } catch (Exception e) {
                logger.warn("发送 Ping 心跳失败: {}", e.getMessage());
                logger.debug("Ping 心跳失败详情", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        logger.info("Ping 心跳已启动，间隔: {} 秒", intervalSeconds);
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
        logger.trace("已发送 Ping 心跳");
    }
    
    /**
     * 发送消息（线程安全）
     */
    private synchronized void sendMessage(ClientMessage message) {
        if (requestObserver == null) {
            throw new IllegalStateException("双向流未连接");
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
        
        logger.info("正在关闭双向流连接...");
        
        // 停止心跳
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
        }
        
        // 完成所有待处理的请求
        pendingRequests.forEach((requestId, future) -> 
            future.completeExceptionally(new RuntimeException("连接已关闭")));
        pendingRequests.clear();
        
        // 关闭流
        if (requestObserver != null) {
            try {
                requestObserver.onCompleted();
            } catch (Exception e) {
                logger.warn("关闭请求流失败", e);
            }
        }
        
        connectionId.set(null);
        logger.info("双向流连接已关闭");
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
            throw new IllegalStateException("双向流未连接");
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
            throw new RuntimeException("请求被中断", e);
        } catch (ExecutionException e) {
            pendingRequests.remove(requestId);
            throw new RuntimeException("请求失败", e.getCause());
        }
    }
    
    /**
     * 发送请求（不等待响应）
     * 
     * @param message 客户端消息
     */
    public void sendRequestAsync(ClientMessage message) {
        if (!connected.get()) {
            throw new IllegalStateException("双向流未连接");
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
            logger.error("双向流发生错误", t);
            lastError.set(t);
            connected.set(false);
            
            // 尝试重连
            reconnect();
        }
        
        @Override
        public void onCompleted() {
            logger.info("双向流已完成");
            connected.set(false);
        }
    }
    
    /**
     * 处理服务端消息
     */
    private void handleServerMessage(ServerMessage message) {
        String requestId = message.getRequestId();
        
        logger.debug("收到服务端消息: type={}, requestId={}", 
            message.getMessageType(), requestId);
        
        // 处理响应消息（有 requestId）
        if (requestId != null && !requestId.isEmpty()) {
            CompletableFuture<ServerMessage> future = pendingRequests.remove(requestId);
            if (future != null) {
                logger.debug("完成待处理请求: requestId={}", requestId);
                future.complete(message);
                return;
            } else {
                logger.debug("未找到待处理请求，将作为推送消息处理: requestId={}", requestId);
            }
        }
        
        // 处理服务端主动推送（无 requestId）或未匹配的响应
        switch (message.getMessageType()) {
            case SERVER_HANDSHAKE:
                logger.debug("开始处理握手响应");
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
                logger.warn("未知的服务端消息类型: {}", message.getMessageType());
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
            
            logger.info("握手成功，connectionId: {}, tenantId: {}", 
                handshake.getConnectionId(), handshake.getTenantId());
            
            if (handshakeListener != null) {
                handshakeListener.accept(handshake);
            }
        } else {
            logger.error("握手失败: {}", handshake.getMessage());
            lastError.set(new RuntimeException("握手失败: " + handshake.getMessage()));
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
        logger.debug("收到服务变更事件: {}, 服务: {}", 
            event.getEventType(), event.getServiceName());
        
        if (serviceChangeListener != null) {
            serviceChangeListener.accept(event);
        }
    }
    
    /**
     * 处理配置变更事件
     */
    private void handleConfigChange(com.flux.servicecenter.config.ConfigProto.ConfigChangeEvent event) {
        logger.debug("收到配置变更事件: {}, 配置: {}", 
            event.getEventType(), event.getConfigDataId());
        
        if (configChangeListener != null) {
            configChangeListener.accept(event);
        }
    }
    
    /**
     * 处理关闭通知
     */
    private void handleCloseNotification(ServerCloseNotification notification) {
        logger.warn("收到服务端关闭通知: {}, 消息: {}, 宽限期: {}秒", 
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
        logger.error("收到服务端错误: {}, 消息: {}", error.getCode(), error.getMessage());
        
        if (errorListener != null) {
            errorListener.accept(error);
        }
    }
    
    // ========== 重连 ==========
    
    /**
     * 自动重连（支持指数退避重试）
     */
    private void reconnect() {
        if (reconnecting.getAndSet(true)) {
            logger.debug("正在重连中，跳过");
            return;
        }
        
        // 异步重连（避免阻塞 gRPC 线程）
        CompletableFuture.runAsync(() -> {
            int maxRetries = 5; // 最多重试 5 次
            long baseDelay = config.getReconnectInterval();
            
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    // 计算退避延迟（指数退避：1x, 2x, 4x, 8x, 16x）
                    long delay = baseDelay * (1L << (attempt - 1)); // 2^(attempt-1)
                    delay = Math.min(delay, 30000); // 最多 30 秒
                    
                    logger.info("开始自动重连... 尝试 {}/{}, 等待 {}ms", attempt, maxRetries, delay);
                    Thread.sleep(delay);
                    
                    // 尝试连接
                    connect();
                    logger.info("自动重连成功");
                    return; // 连接成功，退出重试循环
                    
                } catch (Exception e) {
                    if (attempt < maxRetries) {
                        logger.warn("自动重连失败（尝试 {}/{}），将继续重试: {}", 
                                attempt, maxRetries, e.getMessage());
                    } else {
                        logger.error("自动重连失败（已达最大重试次数 {}），放弃重连", maxRetries, e);
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
            logger.debug("使用用户ID密码认证: userId={}", config.getUserId());
        } else if (config.getAuthToken() != null && !config.getAuthToken().isEmpty()) {
            // 令牌认证：使用 Bearer Token
            authMetadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Bearer " + config.getAuthToken());
            logger.debug("使用令牌认证");
        } else {
            // 没有认证信息
            authMetadata = null;
            logger.debug("未配置认证信息");
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

