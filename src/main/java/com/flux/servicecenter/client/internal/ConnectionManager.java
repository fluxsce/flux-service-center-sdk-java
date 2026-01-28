package com.flux.servicecenter.client.internal;

import com.flux.servicecenter.config.ServiceCenterConfig;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 连接管理器
 * 
 * <p>负责 gRPC 连接的创建、管理、健康检查和关闭。
 * 将连接管理与业务逻辑分离，提高代码的可维护性和可测试性。</p>
 * 
 * @author shangjian
 */
public class ConnectionManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);
    
    // ========== 配置和执行器 ==========
    
    /** 
     * 客户端配置
     * 
     * <p>包含服务器地址、端口、TLS 设置、认证令牌等连接配置信息。</p>
     */
    private final ServiceCenterConfig config;
    
    /** 
     * 定时任务执行器
     * 
     * <p>用于执行连接健康检查任务。由外部传入，通常与心跳执行器共享，
     * 避免创建过多的线程池。</p>
     */
    private final ScheduledExecutorService executor;
    
    // ========== 连接相关 ==========
    
    /** 
     * gRPC 通道
     * 
     * <p>用于与服务端进行 gRPC 通信的底层通道。在 {@link #connect()} 方法中创建，
     * 在 {@link #close()} 方法中关闭。</p>
     * 
     * <p>该通道由服务注册发现和配置中心共享，提高资源利用效率。</p>
     */
    private ManagedChannel channel;
    
    /** 
     * 认证元数据
     * 
     * <p>包含认证令牌等元数据信息，用于在每次 gRPC 调用时附加到请求头中。
     * 如果配置中提供了认证令牌，则会在连接时创建并设置。</p>
     * 
     * <p>通过 {@link #getMetadataInterceptor()} 方法获取拦截器，
     * 用于自动将元数据附加到所有 gRPC 请求中。</p>
     */
    private Metadata metadata;
    
    // ========== 状态管理 ==========
    
    /** 
     * 连接状态标志
     * 
     * <p>标识当前连接是否已建立。使用 {@link AtomicBoolean} 保证线程安全。</p>
     * 
     * <p>状态变化：</p>
     * <ul>
     *   <li>false -> true: 在 {@link #connect()} 成功时设置为 true</li>
     *   <li>true -> false: 在连接失败、健康检查发现异常或 {@link #close()} 时设置为 false</li>
     * </ul>
     */
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    /** 
     * 关闭状态标志
     * 
     * <p>标识连接管理器是否已关闭。使用 {@link AtomicBoolean} 保证线程安全。</p>
     * 
     * <p>一旦设置为 true，将不再允许新的连接操作，确保资源正确释放。</p>
     * 
     * <p>状态变化：</p>
     * <ul>
     *   <li>false -> true: 在 {@link #close()} 时设置为 true</li>
     *   <li>一旦为 true，将始终保持为 true（不可逆）</li>
     * </ul>
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    /** 
     * 最后一次错误
     * 
     * <p>记录连接过程中发生的最后一次错误。使用 {@link AtomicReference} 保证线程安全。</p>
     * 
     * <p>主要用于：</p>
     * <ul>
     *   <li>调试和问题排查</li>
     *   <li>通过 {@link #getLastError()} 方法供外部查询</li>
     * </ul>
     * 
     * <p>在连接成功时会清空（设置为 null）。</p>
     */
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    
    // ========== 健康检查 ==========
    
    /** 
     * 连接健康检查任务
     * 
     * <p>定期检查 gRPC 通道的连接状态，如果发现连接异常（如 TRANSIENT_FAILURE 或 SHUTDOWN），
     * 会自动标记连接为断开状态，并触发 {@link #onConnectionLost()} 回调。</p>
     * 
     * <p>检查频率：每 30 秒检查一次（在 {@link #startHealthCheck()} 中配置）</p>
     * 
     * <p>生命周期：</p>
     * <ul>
     *   <li>创建：在 {@link #connect()} 成功时启动</li>
     *   <li>取消：在 {@link #close()} 时取消</li>
     * </ul>
     */
    private ScheduledFuture<?> healthCheckTask;
    
    /**
     * 创建连接管理器
     * 
     * @param config 客户端配置
     * @param executor 执行器（用于健康检查）
     */
    public ConnectionManager(ServiceCenterConfig config, ScheduledExecutorService executor) {
        this.config = config;
        this.executor = executor;
    }
    
    /**
     * 建立连接
     * 
     * @throws RuntimeException 如果连接失败
     */
    public synchronized void connect() {
        if (closed.get()) {
            throw new IllegalStateException("连接管理器已关闭");
        }
        if (connected.get()) {
            logger.warn("已连接，跳过重复连接");
            return;
        }
        
        try {
            // 构建 gRPC 通道
            // 如果设置了 serverAddress（可能包含多个地址），使用 forTarget；否则使用 forAddress
            ManagedChannelBuilder<?> channelBuilder;
            String serverAddress = config.getServerAddress();
            
            if (serverAddress != null && serverAddress.contains(",")) {
                // 多个地址（集群模式），使用 forTarget 支持负载均衡
                channelBuilder = ManagedChannelBuilder.forTarget(serverAddress);
                logger.info("使用集群模式连接: {}", serverAddress);
            } else {
                // 单个地址，使用 forAddress 或 forTarget
                if (config.getServerAddress() != null && !config.getServerAddress().trim().isEmpty()) {
                    // 使用配置的 serverAddress
                    channelBuilder = ManagedChannelBuilder.forTarget(config.getServerAddress());
                } else {
                    // 使用 serverHost:serverPort
                    channelBuilder = ManagedChannelBuilder.forAddress(
                            config.getServerHost(), 
                            config.getServerPort());
                }
            }
            
            if (!config.isEnableTls()) {
                channelBuilder.usePlaintext();
            }
            
            // 设置 Keep-alive 参数（使用配置值）
            channelBuilder.keepAliveTime(config.getKeepAliveTime(), TimeUnit.MILLISECONDS);
            channelBuilder.keepAliveTimeout(config.getKeepAliveTimeout(), TimeUnit.MILLISECONDS);
            channelBuilder.keepAliveWithoutCalls(config.isKeepAliveWithoutCalls());
            
            // 设置消息大小限制
            channelBuilder.maxInboundMessageSize(config.getMaxInboundMessageSize());
            
            channel = channelBuilder.build();
            
            // 创建认证元数据
            metadata = new Metadata();
            
            // 优先使用用户ID密码认证，如果未设置则使用令牌认证
            if (config.getUserId() != null && !config.getUserId().isEmpty() &&
                config.getPassword() != null && !config.getPassword().isEmpty()) {
                // 用户ID密码认证：使用 Basic Auth
                // 注意：这里使用 userId（用户ID），而不是 userName（用户名）
                // userId 是数据库中 HUB_USER 表的唯一主键标识
                String credentials = config.getUserId() + ":" + config.getPassword();
                String encoded = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
                metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                        "Basic " + encoded);
                logger.debug("使用用户ID密码认证: userId={}", config.getUserId());
            } else if (config.getAuthToken() != null && !config.getAuthToken().isEmpty()) {
                // 令牌认证：使用 Bearer Token
                metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                        "Bearer " + config.getAuthToken());
                logger.debug("使用令牌认证");
            }
            
            connected.set(true);
            lastError.set(null);
            
            // 启动健康检查
            startHealthCheck();
            
            logger.info("已连接到服务中心: {}", config.getServerAddress());
        } catch (Exception e) {
            connected.set(false);
            lastError.set(e);
            logger.error("连接服务中心失败: {}", config.getServerAddress(), e);
            throw new RuntimeException("连接服务中心失败", e);
        }
    }
    
    /**
     * 获取 gRPC 通道
     * 
     * @return gRPC 通道
     * @throws IllegalStateException 如果未连接
     */
    public ManagedChannel getChannel() {
        checkConnected();
        return channel;
    }
    
    /**
     * 获取认证元数据拦截器
     * 
     * @return 元数据拦截器
     */
    public ClientInterceptor getMetadataInterceptor() {
        return MetadataUtils.newAttachHeadersInterceptor(metadata);
    }
    
    /**
     * 获取请求超时时间
     * 
     * @return 超时时间（毫秒）
     */
    public long getRequestTimeout() {
        return config.getRequestTimeout();
    }
    
    /**
     * 启动连接健康检查
     */
    private void startHealthCheck() {
        if (healthCheckTask != null) {
            healthCheckTask.cancel(false);
        }
        
        healthCheckTask = executor.scheduleAtFixedRate(() -> {
            if (closed.get() || !connected.get()) {
                return;
            }
            
            try {
                if (channel != null) {
                    ConnectivityState state = channel.getState(false);
                    if (state == ConnectivityState.TRANSIENT_FAILURE || 
                        state == ConnectivityState.SHUTDOWN) {
                        logger.warn("检测到连接异常，状态: {}", state);
                        connected.set(false);
                        // 通知外部进行重连
                        onConnectionLost();
                    }
                }
            } catch (Exception e) {
                logger.error("健康检查异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 连接丢失回调（子类可以覆盖）
     */
    protected void onConnectionLost() {
        // 默认实现，子类可以覆盖
    }
    
    /**
     * 标记连接为断开状态（用于外部检测到连接问题时调用）
     * 
     * <p>当外部代码（如心跳任务）检测到连接问题时，可以调用此方法标记连接为断开，
     * 这样下次操作时会自动触发重连。</p>
     */
    public synchronized void markDisconnected() {
        if (connected.get()) {
            connected.set(false);
            logger.info("已标记连接为断开状态");
        }
    }
    
    /**
     * 检查连接状态
     */
    private void checkConnected() {
        if (closed.get()) {
            throw new IllegalStateException("连接管理器已关闭");
        }
        if (!connected.get()) {
            throw new IllegalStateException("未连接，请先调用 connect()");
        }
    }
    
    /**
     * 重新连接
     */
    public synchronized void reconnect() {
        if (closed.get()) {
            return;
        }
        if (connected.get()) {
            return;
        }
        
        logger.info("尝试重新连接...");
        try {
            connect();
        } catch (Exception e) {
            logger.error("重连失败", e);
        }
    }
    
    /**
     * 关闭连接
     */
    @Override
    public synchronized void close() {
        if (closed.getAndSet(true)) {
            return;
        }
        
        logger.info("正在关闭连接管理器...");
        
        // 停止健康检查
        if (healthCheckTask != null) {
            healthCheckTask.cancel(false);
            healthCheckTask = null;
        }
        
        // 关闭连接
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("gRPC 通道未在 5 秒内关闭，强制关闭");
                    channel.shutdownNow();
                    channel.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        connected.set(false);
        logger.info("连接管理器已关闭");
    }
    
    // Getters
    public boolean isConnected() {
        return connected.get() && !closed.get();
    }
    
    public boolean isClosed() {
        return closed.get();
    }
    
    public Throwable getLastError() {
        return lastError.get();
    }
}

