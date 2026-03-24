package com.flux.servicecenter.client;

import com.flux.servicecenter.client.internal.StreamBusinessHelper;
import com.flux.servicecenter.client.internal.StreamConnectionManager;
import com.flux.servicecenter.config.ConfigProto;
import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.*;
import com.flux.servicecenter.registry.RegistryProto;
import com.flux.servicecenter.registry.ServiceRegistryGrpc;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于统一双向流的 Service Center 客户端实现
 * 
 * <p>使用单个双向 gRPC 流处理所有通信，包括：</p>
 * <ul>
 *   <li>服务注册发现</li>
 *   <li>配置中心</li>
 *   <li>实时事件推送</li>
 *   <li>心跳保持</li>
 * </ul>
 * 
 * @author shangjian
 * @version 2.0.0 (基于统一双向流)
 */
public class StreamBasedServiceCenterClient implements IServiceCenterClient {
    private static final Logger logger = LoggerFactory.getLogger(StreamBasedServiceCenterClient.class);
    
    // ========== 配置 ==========
    private final ServiceCenterConfig config;
    
    // ========== 连接管理 ==========
    private final ManagedChannel channel;
    private final StreamConnectionManager streamManager;
    private final StreamBusinessHelper businessHelper;
    
    // ========== 独立 RPC Stub ==========
    /** 独立的服务注册 stub，用于不适合通过流的操作（如 GetService） */
    private final ServiceRegistryGrpc.ServiceRegistryBlockingStub registryStub;
    
    // ========== 本地状态管理 ==========
    /** 已注册的节点 (nodeId -> NodeInfo) */
    private final Map<String, NodeInfo> registeredNodes = new ConcurrentHashMap<>();
    
    /** 节点心跳任务 (nodeId -> ScheduledFuture) */
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    
    /** 服务订阅 (subscriptionId -> Subscription) */
    private final Map<String, ServiceSubscription> serviceSubscriptions = new ConcurrentHashMap<>();
    
    /** 配置监听 (watchId -> ConfigWatch) */
    private final Map<String, ConfigWatch> configWatches = new ConcurrentHashMap<>();
    
    // ========== 线程池 ==========
    private final ScheduledExecutorService heartbeatExecutor;
    private final ExecutorService listenerExecutor;
    
    // ========== 状态 ==========
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // ========== 构造函数 ==========
    
    /**
     * 创建基于双向流的客户端
     * 
     * @param config 客户端配置
     */
    public StreamBasedServiceCenterClient(ServiceCenterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        validateConfig(config);
        this.config = config;
        
        // 创建 gRPC Channel（支持 TLS）
        this.channel = createChannel(config);
        
        // 创建线程池
        this.heartbeatExecutor = Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "stream-heartbeat-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                });
        
        this.listenerExecutor = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors() * 2),
                r -> {
                    Thread t = new Thread(r, "stream-listener-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                });
        
        // 创建双向流管理器
        this.streamManager = new StreamConnectionManager(config, channel);
        this.businessHelper = new StreamBusinessHelper(streamManager);
        
        // 创建独立的 RPC stub
        this.registryStub = ServiceRegistryGrpc.newBlockingStub(channel);
        
        // 注册事件监听器
        registerEventListeners();
    }
    
    /**
     * 验证配置
     */
    private void validateConfig(ServiceCenterConfig config) {
        if (config.getServerHost() == null || config.getServerHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Server host must not be empty");
        }
        if (config.getServerPort() < 1 || config.getServerPort() > 65535) {
            throw new IllegalArgumentException("Server port must be between 1 and 65535");
        }
    }
    
    /**
     * 创建 gRPC Channel（支持 TLS、集群地址和认证）
     */
    private ManagedChannel createChannel(ServiceCenterConfig config) {
        try {
            // 判断是否为集群模式（多个地址）
            String serverAddress = config.getServerAddress();
            boolean isClusterMode = serverAddress != null && serverAddress.contains(",");
            
            // 注意：认证逻辑已移至 StreamConnectionManager.connect() 中处理
            // 与 ConnectionManager 保持一致，在 connect 时创建认证元数据
            
            if (!config.isEnableTls()) {
                // ========== 明文通信（不使用 TLS） ==========
                ManagedChannelBuilder<?> channelBuilder;
                
                if (isClusterMode) {
                    // 集群模式：多个地址，使用 forTarget 支持负载均衡
                    logger.info("Creating plaintext gRPC channel (cluster mode): {}", serverAddress);
                    channelBuilder = ManagedChannelBuilder.forTarget(serverAddress)
                            .defaultLoadBalancingPolicy("round_robin"); // 轮询负载均衡
                } else {
                    // 单机模式：单个地址
                    if (serverAddress != null && !serverAddress.trim().isEmpty()) {
                        // 使用 serverAddress
                        logger.info("Creating plaintext gRPC channel: {}", serverAddress);
                        channelBuilder = ManagedChannelBuilder.forTarget(serverAddress);
                    } else {
                        // 使用 serverHost:serverPort
                        String host = config.getServerHost();
                        int port = config.getServerPort();
                        logger.info("Creating plaintext gRPC channel: {}:{}", host, port);
                        channelBuilder = ManagedChannelBuilder.forAddress(host, port);
                    }
                }
                
                return channelBuilder
                        .usePlaintext()
                        .keepAliveTime(30, TimeUnit.SECONDS)
                        .keepAliveTimeout(10, TimeUnit.SECONDS)
                        .keepAliveWithoutCalls(true)
                        .maxInboundMessageSize(16 * 1024 * 1024) // 16MB
                        .build();
            }
            
            // ========== TLS 加密通信 ==========
            logger.info("Creating TLS gRPC channel: {}", serverAddress);
            
            // 构建 SSL 上下文
            SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();
            
            // 如果配置了 CA 证书，则使用自定义证书（用于自签名证书）
            String tlsCaPath = config.getTlsCaPath();
            if (tlsCaPath != null && !tlsCaPath.trim().isEmpty()) {
                File caFile = new File(tlsCaPath);
                if (!caFile.exists()) {
                    throw new IllegalArgumentException("CA certificate file does not exist: " + tlsCaPath);
                }
                logger.info("Using custom CA certificate: {}", tlsCaPath);
                sslContextBuilder.trustManager(caFile);
            }
            // 否则使用系统默认的信任证书（用于由可信 CA 签名的证书）
            
            // 如果配置了客户端证书（双向 TLS）
            String tlsCertPath = config.getTlsCertPath();
            String tlsKeyPath = config.getTlsKeyPath();
            if (tlsCertPath != null && !tlsCertPath.trim().isEmpty() 
                    && tlsKeyPath != null && !tlsKeyPath.trim().isEmpty()) {
                File certFile = new File(tlsCertPath);
                File keyFile = new File(tlsKeyPath);
                if (!certFile.exists()) {
                    throw new IllegalArgumentException("Client certificate file does not exist: " + tlsCertPath);
                }
                if (!keyFile.exists()) {
                    throw new IllegalArgumentException("Client private key file does not exist: " + tlsKeyPath);
                }
                logger.info("Using client certificate (mTLS): cert={}, key={}", tlsCertPath, tlsKeyPath);
                sslContextBuilder.keyManager(certFile, keyFile);
            }
            
            SslContext sslContext = sslContextBuilder.build();
            
            // 创建 TLS Channel
            NettyChannelBuilder channelBuilder;
            
            if (isClusterMode) {
                // 集群模式：多个地址
                logger.info("TLS cluster mode: {}", serverAddress);
                channelBuilder = NettyChannelBuilder.forTarget(serverAddress)
                        .defaultLoadBalancingPolicy("round_robin"); // 轮询负载均衡
            } else {
                // 单机模式：单个地址
                if (serverAddress != null && !serverAddress.trim().isEmpty()) {
                    // 解析 host:port
                    String[] parts = serverAddress.split(":");
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("Invalid TLS server address format, expected host:port, value: " + serverAddress);
                    }
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    logger.info("TLS single-node mode: {}:{}", host, port);
                    channelBuilder = NettyChannelBuilder.forAddress(host, port);
                } else {
                    // 使用 serverHost:serverPort
                    String host = config.getServerHost();
                    int port = config.getServerPort();
                    logger.info("TLS single-node mode: {}:{}", host, port);
                    channelBuilder = NettyChannelBuilder.forAddress(host, port);
                }
            }
            
            // 注意：认证逻辑已移至 StreamConnectionManager.connect() 中处理
            
            return channelBuilder
                    .sslContext(sslContext)
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxInboundMessageSize(16 * 1024 * 1024) // 16MB
                    .build();
            
        } catch (SSLException e) {
            throw new RuntimeException("Failed to create TLS channel", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create gRPC channel", e);
        }
    }
    
    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        // 握手成功监听器 - 用于重连后恢复状态
        streamManager.setHandshakeListener(handshake -> {
            if (handshake.getSuccess()) {
                logger.info("Handshake succeeded, restoring client state after reconnect...");
                listenerExecutor.execute(this::restoreStateAfterReconnect);
            }
        });
        
        // 服务变更事件监听器
        streamManager.setServiceChangeListener(event -> {
            listenerExecutor.execute(() -> handleServiceChangeEvent(event));
        });
        
        // 配置变更事件监听器
        streamManager.setConfigChangeListener(event -> {
            listenerExecutor.execute(() -> handleConfigChangeEvent(event));
        });
        
        // 错误事件监听器
        streamManager.setErrorListener(error -> {
            logger.error("Server error from stream: code={}, message={}", error.getCode(), error.getMessage());
        });
        
        // 关闭通知监听器
        streamManager.setCloseListener(notification -> {
            logger.warn("Server requested stream close: reason={}", notification.getReason());
        });
    }
    
    /**
     * 重连后恢复状态（重新注册节点和订阅）
     * 注意：重连时保持原有的 nodeId 不变
     */
    private void restoreStateAfterReconnect() {
        logger.info("Restoring state after reconnect...");
        
        // 1. 重新注册所有节点（保持原有 nodeId）
        if (!registeredNodes.isEmpty()) {
            logger.info("Re-registering {} node(s)...", registeredNodes.size());
            // 创建副本，避免并发修改
            Map<String, NodeInfo> nodesToReregister = new HashMap<>(registeredNodes);
            
            for (Map.Entry<String, NodeInfo> entry : nodesToReregister.entrySet()) {
                String nodeId = entry.getKey();
                NodeInfo nodeInfo = entry.getValue();
                
                try {
                    logger.debug("Re-registering node: serviceName={}, nodeId={}", nodeInfo.getServiceName(), nodeId);
                    
                    // 停止旧的心跳任务
                    stopHeartbeat(nodeId);
                    
                    // 确保 nodeInfo 中有 nodeId（用于重连时传给服务端）
                    nodeInfo.setNodeId(nodeId);
                    
                    // 重新注册节点（带上原有的 nodeId）
                    RegistryProto.Node node = buildNodeProto(nodeInfo, nodeInfo.getServiceName());
                    RegistryProto.RegisterNodeResponse response = businessHelper.registerNode(node);
                    
                    if (response.getSuccess()) {
                        // 重新启动心跳（使用原有的 nodeId）
                        startHeartbeat(nodeId);
                        logger.info("Node re-registered: serviceName={}, nodeId={}", nodeInfo.getServiceName(), nodeId);
                    } else {
                        logger.warn("Node re-register failed: nodeId={}, message={}", nodeId, response.getMessage());
                    }
                } catch (Exception e) {
                    logger.error("Node re-register failed: nodeId={}", nodeId, e);
                }
            }
        }
        
        // 2. 重新订阅所有服务
        if (!serviceSubscriptions.isEmpty()) {
            logger.info("Re-subscribing {} service subscription(s)...", serviceSubscriptions.size());
            for (ServiceSubscription subscription : serviceSubscriptions.values()) {
                try {
                    for (String serviceName : subscription.serviceNames) {
                        logger.debug("Re-subscribing service: {}/{}/{}", 
                                subscription.namespaceId, subscription.groupName, serviceName);
                        
                        RegistryProto.SubscribeServicesRequest request = RegistryProto.SubscribeServicesRequest.newBuilder()
                                .setNamespaceId(subscription.namespaceId)
                                .setGroupName(subscription.groupName)
                                .addServiceNames(serviceName)
                                .build();
                        
                        businessHelper.subscribeServices(request);
                        logger.info("Service re-subscribed: {}", serviceName);
                    }
                } catch (Exception e) {
                    logger.error("Service re-subscribe failed", e);
                }
            }
        }
        
        // 3. 重新监听所有配置
        if (!configWatches.isEmpty()) {
            logger.info("Re-watching {} config watch(es)...", configWatches.size());
            for (ConfigWatch watch : configWatches.values()) {
                try {
                    for (String configDataId : watch.configDataIds) {
                        logger.debug("Re-watching config: {}/{}/{}", 
                                watch.namespaceId, watch.groupName, configDataId);
                        
                        ConfigProto.WatchConfigRequest request = ConfigProto.WatchConfigRequest.newBuilder()
                                .setNamespaceId(watch.namespaceId)
                                .setGroupName(watch.groupName)
                                .addConfigDataIds(configDataId)
                                .build();
                        
                        businessHelper.watchConfig(request);
                        logger.info("Config re-watch sent: {}", configDataId);
                    }
                } catch (Exception e) {
                    logger.error("Config re-watch failed", e);
                }
            }
        }
        
        logger.info("State restore after reconnect completed");
    }
    
    // ========== 连接管理 ==========
    
    @Override
    public synchronized void connect() {
        if (closed.get()) {
            throw new IllegalStateException("Client is already closed");
        }
        
        logger.info("Connecting to service center: {}:{}", config.getServerHost(), config.getServerPort());
        streamManager.connect();
        logger.info("Connected to service center");
    }
    
    @Override
    public synchronized void close() {
        if (closed.getAndSet(true)) {
            return;
        }
        
        logger.info("Closing client...");
        
        // 1. 注销所有节点
        unregisterAllNodes();
        
        // 2. 停止所有心跳任务
        stopAllHeartbeats();
        
        // 3. 关闭双向流
        streamManager.close();
        
        // 4. 关闭 Channel
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 5. 关闭线程池
        shutdownExecutor(heartbeatExecutor, "heartbeat");
        shutdownExecutor(listenerExecutor, "listener");
        
        logger.info("Client closed");
    }
    
    @Override
    public boolean isConnected() {
        return streamManager.isConnected();
    }
    
    @Override
    public boolean checkHealth() {
        return isConnected();
    }
    
    // ========== 服务注册发现 API ==========
    
    @Override
    public RegisterServiceResult registerService(ServiceInfo serviceInfo, NodeInfo nodeInfo) {
        ensureConnected();
        
        try {
            // 构建 Service Proto
            RegistryProto.Service.Builder serviceBuilder = RegistryProto.Service.newBuilder()
                    .setNamespaceId(getOrDefault(serviceInfo.getNamespaceId(), config.getNamespaceId()))
                    .setGroupName(getOrDefault(serviceInfo.getGroupName(), config.getGroupName()))
                    .setServiceName(serviceInfo.getServiceName())
                    .setServiceType(getOrDefault(serviceInfo.getServiceType(), "HTTP")); // Model 中是 serviceType
            
            if (serviceInfo.getServiceDescription() != null) {
                serviceBuilder.setServiceDescription(serviceInfo.getServiceDescription()); // Model 中是 serviceDescription
            }
            if (serviceInfo.getMetadata() != null) {
                serviceBuilder.putAllMetadata(serviceInfo.getMetadata());
            }
            
            // 如果提供了 nodeInfo，同时注册节点
            if (nodeInfo != null) {
                RegistryProto.Node node = buildNodeProto(nodeInfo, serviceInfo.getServiceName());
                serviceBuilder.setNode(node); // proto 中是 node (单数)，而不是 addNodes
            }
            
            // 发送注册请求
            RegistryProto.RegisterServiceResponse response = businessHelper.registerService(serviceBuilder.build());
            
            RegisterServiceResult result = new RegisterServiceResult();
            result.setSuccess(response.getSuccess());
            result.setMessage(response.getMessage());
            
            // 如果注册了节点，启动心跳并缓存节点信息
            if (nodeInfo != null && response.getNodeId() != null && !response.getNodeId().isEmpty()) {
                String nodeId = response.getNodeId(); // proto 中是 nodeId (单数)
                result.setNodeId(nodeId);
                nodeInfo.setNodeId(nodeId);
                registeredNodes.put(nodeId, nodeInfo);
                startHeartbeat(nodeId);
                logger.info("Service node registered: serviceName={}, nodeId={}", serviceInfo.getServiceName(), nodeId);
            }
            
            return result;
            
        } catch (TimeoutException e) {
            throw new RuntimeException("Register service timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Register service failed", e);
        }
    }
    
    @Override
    public OperationResult unregisterService(String namespaceId, String groupName, String serviceName, String nodeId) {
        ensureConnected();
        
        try {
            if (nodeId != null && !nodeId.isEmpty()) {
                // 注销特定节点
                return unregisterNode(nodeId);
            } else {
                // 注销整个服务
                RegistryProto.ServiceKey serviceKey = RegistryProto.ServiceKey.newBuilder()
                        .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                        .setGroupName(getOrDefault(groupName, config.getGroupName()))
                        .setServiceName(serviceName)
                        .build();
                
                RegistryProto.RegistryResponse response = businessHelper.unregisterService(serviceKey);
                
                OperationResult result = new OperationResult();
                result.setSuccess(response.getSuccess());
                result.setMessage(response.getMessage());
                return result;
            }
        } catch (TimeoutException e) {
            throw new RuntimeException("Unregister service timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Unregister service failed", e);
        }
    }
    
    @Override
    public RegisterNodeResult registerNode(NodeInfo nodeInfo) {
        ensureConnected();
        
        try {
            RegistryProto.Node node = buildNodeProto(nodeInfo, nodeInfo.getServiceName());
            RegistryProto.RegisterNodeResponse response = businessHelper.registerNode(node);
            
            RegisterNodeResult result = new RegisterNodeResult();
            result.setSuccess(response.getSuccess());
            result.setMessage(response.getMessage());
            
            if (response.getSuccess() && response.getNodeId() != null && !response.getNodeId().isEmpty()) {
                String nodeId = response.getNodeId();
                result.setNodeId(nodeId);
                nodeInfo.setNodeId(nodeId);
                registeredNodes.put(nodeId, nodeInfo);
                startHeartbeat(nodeId);
                logger.info("Node registered: nodeId={}", nodeId);
            }
            
            return result;
            
        } catch (TimeoutException e) {
            throw new RuntimeException("Register node timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Register node failed", e);
        }
    }
    
    @Override
    public OperationResult unregisterNode(String nodeId) {
        ensureConnected();
        
        try {
            // 停止心跳
            stopHeartbeat(nodeId);
            
            // 发送注销请求
            RegistryProto.NodeKey nodeKey = RegistryProto.NodeKey.newBuilder()
                    .setNodeId(nodeId)
                    .build();
            
            RegistryProto.RegistryResponse response = businessHelper.unregisterNode(nodeKey);
            
            // 移除本地缓存
            registeredNodes.remove(nodeId);
            
            OperationResult result = new OperationResult();
            result.setSuccess(response.getSuccess());
            result.setMessage(response.getMessage());
            
            logger.info("Node unregistered: nodeId={}", nodeId);
            return result;
            
        } catch (TimeoutException e) {
            throw new RuntimeException("Unregister node timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Unregister node failed", e);
        }
    }
    
    @Override
    public GetServiceResult getService(String namespaceId, String groupName, String serviceName) {
        ensureConnected();
        
        // GetService 使用独立的 RPC stub，不通过统一流
        // 这是一个简单的请求-响应操作，不需要双向流的复杂性
        try {
            RegistryProto.ServiceKey serviceKey = RegistryProto.ServiceKey.newBuilder()
                    .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                    .setGroupName(getOrDefault(groupName, config.getGroupName()))
                    .setServiceName(serviceName)
                    .build();
            
            // 使用独立的阻塞 stub 调用
            RegistryProto.GetServiceResponse response = registryStub
                    .withDeadlineAfter(config.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .getService(serviceKey);
            
            GetServiceResult result = new GetServiceResult();
            result.setSuccess(response.getSuccess());
            result.setMessage(response.getMessage());
            
            if (response.hasService()) {
                result.setService(ProtoConverter.toServiceInfo(response.getService()));
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("getService failed", e);
            GetServiceResult result = new GetServiceResult();
            result.setSuccess(false);
            result.setMessage("getService failed: " + e.getMessage());
            return result;
        }
    }
    
    public List<NodeInfo> discoverNodes(String namespaceId, String groupName, String serviceName, boolean healthyOnly) {
        ensureConnected();
        
        try {
            RegistryProto.DiscoverNodesRequest request = RegistryProto.DiscoverNodesRequest.newBuilder()
                    .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                    .setGroupName(getOrDefault(groupName, config.getGroupName()))
                    .setServiceName(serviceName)
                    .setHealthyOnly(healthyOnly)
                    .build();
            
            RegistryProto.DiscoverNodesResponse response = businessHelper.discoverNodes(request);
            
            if (response.getSuccess()) {
                return ProtoConverter.toNodeInfoList(response.getNodesList());
            } else {
                logger.warn("discoverNodes failed: {}", response.getMessage());
                return Collections.emptyList();
            }
            
        } catch (TimeoutException e) {
            logger.error("discoverNodes timed out", e);
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("discoverNodes failed", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String subscribeService(String namespaceId, String groupName, String serviceName, ServiceChangeListener listener) {
        ensureConnected();
        
        String subscriptionId = UUID.randomUUID().toString();
        
        // 构建订阅请求
        RegistryProto.SubscribeServicesRequest request = RegistryProto.SubscribeServicesRequest.newBuilder()
                .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                .setGroupName(getOrDefault(groupName, config.getGroupName()))
                .addServiceNames(serviceName)
                .build();
        
        // 发送订阅请求（异步）
        businessHelper.subscribeServices(request);
        
        // 保存订阅信息
        ServiceSubscription subscription = new ServiceSubscription();
        subscription.subscriptionId = subscriptionId;
        subscription.namespaceId = getOrDefault(namespaceId, config.getNamespaceId());
        subscription.groupName = getOrDefault(groupName, config.getGroupName());
        subscription.serviceNames = Collections.singletonList(serviceName);
        subscription.listener = listener;
        serviceSubscriptions.put(subscriptionId, subscription);
        
        logger.info("Service subscribed: serviceName={}, subscriptionId={}", serviceName, subscriptionId);
        return subscriptionId;
    }
    
    @Override
    public OperationResult unsubscribe(String subscriptionId) {
        serviceSubscriptions.remove(subscriptionId);
        
        OperationResult result = new OperationResult();
        result.setSuccess(true);
        result.setMessage("Unsubscribed successfully");
        return result;
    }
    
    @Override
    public OperationResult sendHeartbeat(String nodeId) {
        ensureConnected();
        
        try {
            RegistryProto.HeartbeatRequest.Builder requestBuilder = RegistryProto.HeartbeatRequest.newBuilder()
                    .setNodeId(nodeId);
            
            // 携带完整服务信息，与 ServiceRegistryManager 保持一致
            // 网络重连后服务端可能已剔除不健康节点，携带 service 信息便于服务端恢复/更新节点
            NodeInfo nodeInfo = registeredNodes.get(nodeId);
            if (nodeInfo != null) {
                RegistryProto.Service.Builder serviceBuilder = RegistryProto.Service.newBuilder()
                        .setNamespaceId(getOrDefault(nodeInfo.getNamespaceId(), config.getNamespaceId()))
                        .setGroupName(getOrDefault(nodeInfo.getGroupName(), config.getGroupName()))
                        .setServiceName(getOrDefault(nodeInfo.getServiceName(), ""));
                
                RegistryProto.Node protoNode = ProtoConverter.toProtoNode(nodeInfo);
                if (protoNode != null) {
                    serviceBuilder.setNode(protoNode);
                }
                requestBuilder.setService(serviceBuilder.build());
            }
            
            RegistryProto.RegistryResponse response = businessHelper.heartbeat(requestBuilder.build());
            
            OperationResult result = new OperationResult();
            result.setSuccess(response.getSuccess());
            result.setMessage(response.getMessage());
            return result;
            
        } catch (TimeoutException e) {
            throw new RuntimeException("Send heartbeat timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Send heartbeat failed", e);
        }
    }
    
    @Override
    public List<String> getRegisteredNodeIds() {
        return new ArrayList<>(registeredNodes.keySet());
    }
    
    @Override
    public List<String> getActiveSubscriptions() {
        return new ArrayList<>(serviceSubscriptions.keySet());
    }
    
    // ========== 配置中心 API ==========
    
    @Override
    public GetConfigResult getConfig(String namespaceId, String groupName, String configDataId) {
        ensureConnected();
        
        try {
            ConfigProto.ConfigKey configKey = ConfigProto.ConfigKey.newBuilder()
                    .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                    .setGroupName(getOrDefault(groupName, config.getGroupName()))
                    .setConfigDataId(configDataId)
                    .build();
            
            ConfigProto.GetConfigResponse response = businessHelper.getConfig(configKey);
            
            GetConfigResult result = new GetConfigResult();
            result.setSuccess(response.getSuccess());
            result.setMessage(response.getMessage());
            
            if (response.hasConfig()) {
                result.setConfig(ProtoConverter.toConfigInfo(response.getConfig()));
            }
            
            return result;
            
        } catch (TimeoutException e) {
            throw new RuntimeException("Get config timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Get config failed", e);
        }
    }
    
    @Override
    public SaveConfigResult saveConfig(ConfigInfo configInfo) {
        ensureConnected();
        
        try {
            ConfigProto.ConfigData.Builder configBuilder = ConfigProto.ConfigData.newBuilder()
                    .setNamespaceId(getOrDefault(configInfo.getNamespaceId(), config.getNamespaceId()))
                    .setGroupName(getOrDefault(configInfo.getGroupName(), config.getGroupName()))
                    .setConfigDataId(configInfo.getConfigDataId())
                    .setConfigContent(configInfo.getConfigContent())
                    .setContentType(getOrDefault(configInfo.getContentType(), "text"));
            
            if (configInfo.getConfigDesc() != null) {
                configBuilder.setConfigDesc(configInfo.getConfigDesc());
            }
            
            ConfigProto.SaveConfigResponse response = businessHelper.saveConfig(configBuilder.build());
            
            SaveConfigResult result = new SaveConfigResult();
            result.setSuccess(response.getSuccess());
            result.setMessage(response.getMessage());
            result.setVersion(response.getVersion()); // Model 中是 setVersion
            result.setContentMd5(response.getContentMd5()); // Model 中是 setContentMd5
            
            return result;
            
        } catch (TimeoutException e) {
            throw new RuntimeException("Save config timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Save config failed", e);
        }
    }
    
    @Override
    public OperationResult deleteConfig(String namespaceId, String groupName, String configDataId) {
        ensureConnected();
        
        try {
            ConfigProto.ConfigKey configKey = ConfigProto.ConfigKey.newBuilder()
                    .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                    .setGroupName(getOrDefault(groupName, config.getGroupName()))
                    .setConfigDataId(configDataId)
                    .build();
            
            ConfigProto.ConfigResponse response = businessHelper.deleteConfig(configKey);
            
            OperationResult result = new OperationResult();
            result.setSuccess(response.getSuccess());
            result.setMessage(response.getMessage());
            return result;
            
        } catch (TimeoutException e) {
            throw new RuntimeException("Delete config timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Delete config failed", e);
        }
    }
    
    @Override
    public List<ConfigInfo> listConfigs(String namespaceId, String groupName, String searchKey, int pageNum, int pageSize) {
        ensureConnected();
        
        try {
            // proto 中的 ListConfigsRequest 没有分页和搜索字段，只有 namespaceId 和 groupName
            ConfigProto.ListConfigsRequest.Builder requestBuilder = ConfigProto.ListConfigsRequest.newBuilder()
                    .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                    .setGroupName(getOrDefault(groupName, config.getGroupName()));
            
            // 忽略 pageNum, pageSize, searchKey，因为 proto 不支持
            
            ConfigProto.ListConfigsResponse response = businessHelper.listConfigs(requestBuilder.build());
            
            if (response.getSuccess()) {
                return ProtoConverter.toConfigInfoList(response.getConfigsList());
            } else {
                logger.warn("listConfigs failed: {}", response.getMessage());
                return Collections.emptyList();
            }
            
        } catch (TimeoutException e) {
            logger.error("listConfigs timed out", e);
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("listConfigs failed", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String watchConfig(String namespaceId, String groupName, String configDataId, ConfigChangeListener listener) {
        ensureConnected();
        
        String watchId = UUID.randomUUID().toString();
        
        // 构建监听请求
        ConfigProto.WatchConfigRequest request = ConfigProto.WatchConfigRequest.newBuilder()
                .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                .setGroupName(getOrDefault(groupName, config.getGroupName()))
                .addConfigDataIds(configDataId)
                .build();
        
        // 发送监听请求（异步）
        businessHelper.watchConfig(request);
        
        // 保存监听信息
        ConfigWatch watch = new ConfigWatch();
        watch.watchId = watchId;
        watch.namespaceId = getOrDefault(namespaceId, config.getNamespaceId());
        watch.groupName = getOrDefault(groupName, config.getGroupName());
        watch.configDataIds = Collections.singletonList(configDataId);
        watch.listener = listener;
        configWatches.put(watchId, watch);
        
        logger.info("Config watch started: configDataId={}, watchId={}", configDataId, watchId);
        return watchId;
    }
    
    @Override
    public OperationResult unwatch(String watchId) {
        configWatches.remove(watchId);
        
        OperationResult result = new OperationResult();
        result.setSuccess(true);
        result.setMessage("Config watch removed successfully");
        return result;
    }
    
    @Override
    public List<String> getActiveWatches() {
        return new ArrayList<>(configWatches.keySet());
    }
    
    @Override
    public List<ConfigHistory> getConfigHistory(String namespaceId, String groupName, String configDataId, int pageNum, int pageSize) {
        ensureConnected();
        
        try {
            ConfigProto.GetConfigHistoryRequest request = ConfigProto.GetConfigHistoryRequest.newBuilder()
                    .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                    .setGroupName(getOrDefault(groupName, config.getGroupName()))
                    .setConfigDataId(configDataId)
                    .setLimit(pageSize)
                    .build();
            
            ConfigProto.GetConfigHistoryResponse response = businessHelper.getConfigHistory(request);
            
            if (response.getSuccess()) {
                return ProtoConverter.toConfigHistoryList(response.getHistoryList()); // proto 中是 history，不是 histories
            } else {
                logger.warn("getConfigHistory failed: {}", response.getMessage());
                return Collections.emptyList();
            }
            
        } catch (TimeoutException e) {
            logger.error("getConfigHistory timed out", e);
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("getConfigHistory failed", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public RollbackConfigResult rollbackConfig(String namespaceId, String groupName, String configDataId, String historyId) {
        ensureConnected();
        
        try {
            long targetVersion;
            try {
                targetVersion = Long.parseLong(historyId);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid history id: " + historyId);
            }
            
            ConfigProto.RollbackConfigRequest request = ConfigProto.RollbackConfigRequest.newBuilder()
                    .setNamespaceId(getOrDefault(namespaceId, config.getNamespaceId()))
                    .setGroupName(getOrDefault(groupName, config.getGroupName()))
                    .setConfigDataId(configDataId)
                    .setTargetVersion(targetVersion)
                    .build();
            
            ConfigProto.RollbackConfigResponse response = businessHelper.rollbackConfig(request);
            
            RollbackConfigResult result = new RollbackConfigResult();
            result.setSuccess(response.getSuccess());
            result.setMessage(response.getMessage());
            result.setNewVersion(response.getNewVersion()); // Model 中是 setNewVersion
            result.setContentMd5(response.getContentMd5()); // Model 中是 setContentMd5
            
            return result;
            
        } catch (TimeoutException e) {
            throw new RuntimeException("Rollback config timed out", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Rollback config failed", e);
        }
    }
    
    // ========== 事件处理 ==========
    
    /**
     * 处理服务变更事件
     */
    private void handleServiceChangeEvent(RegistryProto.ServiceChangeEvent event) {
        String namespaceId = event.getNamespaceId();
        String groupName = event.getGroupName();
        String serviceName = event.getServiceName();
        
        // 找到匹配的订阅并通知监听器
        for (ServiceSubscription subscription : serviceSubscriptions.values()) {
            if (subscription.matches(namespaceId, groupName, serviceName)) {
                try {
                    ServiceChangeEvent domainEvent = ProtoConverter.toServiceChangeEvent(event);
                    subscription.listener.onServiceChange(domainEvent);
                } catch (Exception e) {
                    logger.error("handleServiceChangeEvent failed", e);
                }
            }
        }
    }
    
    /**
     * 处理配置变更事件
     */
    private void handleConfigChangeEvent(ConfigProto.ConfigChangeEvent event) {
        String namespaceId = event.getNamespaceId();
        String groupName = event.getGroupName();
        String configDataId = event.getConfigDataId();
        
        // 找到匹配的监听并通知监听器
        for (ConfigWatch watch : configWatches.values()) {
            if (watch.matches(namespaceId, groupName, configDataId)) {
                try {
                    ConfigChangeEvent domainEvent = ProtoConverter.toConfigChangeEvent(event);
                    watch.listener.onConfigChange(domainEvent);
                } catch (Exception e) {
                    logger.error("handleConfigChangeEvent failed", e);
                }
            }
        }
    }
    
    // ========== 心跳管理 ==========
    
    /**
     * 启动心跳任务
     */
    private void startHeartbeat(String nodeId) {
        stopHeartbeat(nodeId); // 先停止旧的心跳任务
        
        ScheduledFuture<?> future = heartbeatExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        OperationResult result = sendHeartbeat(nodeId);
                        if (!result.isSuccess()) {
                            logger.error("Heartbeat rejected: nodeId={}, message={}", 
                                    nodeId, result.getMessage());
                        }
                    } catch (Exception e) {
                        logger.error("sendHeartbeat failed: nodeId={}", nodeId, e);
                    }
                },
                config.getHeartbeatInterval(),
                config.getHeartbeatInterval(),
                TimeUnit.MILLISECONDS  // 修复：使用毫秒
        );
        
        heartbeatTasks.put(nodeId, future);
        logger.debug("Heartbeat task started: nodeId={}, intervalMs={}", nodeId, config.getHeartbeatInterval());
    }
    
    /**
     * 停止心跳任务
     */
    private void stopHeartbeat(String nodeId) {
        ScheduledFuture<?> future = heartbeatTasks.remove(nodeId);
        if (future != null) {
            future.cancel(false);
            logger.debug("Heartbeat task stopped: nodeId={}", nodeId);
        }
    }
    
    /**
     * 停止所有心跳任务
     */
    private void stopAllHeartbeats() {
        for (Map.Entry<String, ScheduledFuture<?>> entry : heartbeatTasks.entrySet()) {
            entry.getValue().cancel(false);
        }
        heartbeatTasks.clear();
        logger.info("All heartbeat tasks stopped");
    }
    
    /**
     * 注销所有节点
     */
    private void unregisterAllNodes() {
        List<String> nodeIds = new ArrayList<>(registeredNodes.keySet());
        for (String nodeId : nodeIds) {
            try {
                unregisterNode(nodeId);
            } catch (Exception e) {
                logger.error("unregisterNode failed: nodeId={}", nodeId, e);
            }
        }
    }
    
    // ========== 工具方法 ==========
    
    /**
     * 确保已连接
     */
    private void ensureConnected() {
        if (!isConnected()) {
            throw new IllegalStateException("Client not connected; call connect() first");
        }
    }
    
    /**
     * 获取默认值
     */
    private String getOrDefault(String value, String defaultValue) {
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * 优雅关闭执行器
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Executor '{}' did not terminate within 5s, forcing shutdown", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 构建节点 Proto
     */
    private RegistryProto.Node buildNodeProto(NodeInfo nodeInfo, String serviceName) {
        RegistryProto.Node.Builder builder = RegistryProto.Node.newBuilder()
                .setNamespaceId(getOrDefault(nodeInfo.getNamespaceId(), config.getNamespaceId()))
                .setGroupName(getOrDefault(nodeInfo.getGroupName(), config.getGroupName()))
                .setServiceName(getOrDefault(serviceName, nodeInfo.getServiceName()))
                .setIpAddress(nodeInfo.getIpAddress())
                .setPortNumber(nodeInfo.getPortNumber())
                .setWeight(nodeInfo.getWeight() > 0 ? nodeInfo.getWeight() : 100.0); // Model 中 weight 已经是 double
        
        // 如果有 nodeId（重连场景），传给服务端以保持 nodeId 不变
        if (nodeInfo.getNodeId() != null && !nodeInfo.getNodeId().isEmpty()) {
            builder.setNodeId(nodeInfo.getNodeId());
        }
        if (nodeInfo.getHealthyStatus() != null) {
            builder.setHealthyStatus(nodeInfo.getHealthyStatus());
        }
        if (nodeInfo.getInstanceStatus() != null) {
            builder.setInstanceStatus(nodeInfo.getInstanceStatus());
        }
        if (nodeInfo.getMetadata() != null) {
            builder.putAllMetadata(nodeInfo.getMetadata());
        }
        
        return builder.build();
    }
    
    // ========== 转换方法 (使用 ProtoConverter 统一转换逻辑) ==========
    // 注意：所有转换逻辑已迁移到 ProtoConverter 类，避免代码重复
    
    // ========== 内部类 ==========
    
    /**
     * 服务订阅信息
     */
    private static class ServiceSubscription {
        @SuppressWarnings("unused") // 用于追踪订阅ID
        String subscriptionId;
        String namespaceId;
        String groupName;
        List<String> serviceNames;
        ServiceChangeListener listener;
        
        boolean matches(String namespaceId, String groupName, String serviceName) {
            if (!this.namespaceId.equals(namespaceId)) {
                return false;
            }
            if (!this.groupName.equals(groupName)) {
                return false;
            }
            // 如果 serviceNames 为空或 null，表示订阅整个命名空间/分组
            if (serviceNames == null || serviceNames.isEmpty()) {
                return true;
            }
            return serviceNames.contains(serviceName);
        }
    }
    
    /**
     * 配置监听信息
     */
    private static class ConfigWatch {
        @SuppressWarnings("unused") // 用于追踪监听ID
        String watchId;
        String namespaceId;
        String groupName;
        List<String> configDataIds;
        ConfigChangeListener listener;
        
        boolean matches(String namespaceId, String groupName, String configDataId) {
            if (!this.namespaceId.equals(namespaceId)) {
                return false;
            }
            if (!this.groupName.equals(groupName)) {
                return false;
            }
            // 如果 configDataIds 为空或 null，表示监听整个命名空间/分组
            if (configDataIds == null || configDataIds.isEmpty()) {
                return true;
            }
            return configDataIds.contains(configDataId);
        }
    }
}

