package com.flux.servicecenter.client.internal;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.*;
import com.flux.servicecenter.registry.RegistryProto;
import com.flux.servicecenter.registry.ServiceRegistryGrpc;
import io.grpc.ClientInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务注册发现管理器
 * 
 * <p>负责服务注册、发现、订阅和心跳等业务逻辑。
 * 与连接管理分离，专注于业务功能实现。</p>
 * 
 * @author shangjian
 */
public class ServiceRegistryManager {
    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistryManager.class);
    
    // ========== 配置和依赖 ==========
    
    /** 
     * 客户端配置
     * 
     * <p>包含心跳间隔、重连策略等配置信息。</p>
     */
    private final ServiceCenterConfig config;
    
    /** 
     * 连接管理器
     * 
     * <p>用于获取 gRPC 通道和元数据拦截器，不直接管理连接生命周期。</p>
     */
    private final ConnectionManager connectionManager;
    
    /** 
     * 心跳执行器
     * 
     * <p>用于执行节点心跳任务。与连接管理器的健康检查共享同一个执行器，
     * 避免创建过多的线程池。</p>
     */
    private final ScheduledExecutorService heartbeatExecutor;
    
    /** 
     * 订阅执行器
     * 
     * <p>用于执行订阅重连任务。使用固定大小的线程池，避免无限制创建线程。</p>
     */
    private final ExecutorService subscriptionExecutor;
    
    // ========== gRPC Stub ==========
    
    /** 
     * 服务注册发现 Stub（阻塞式）
     * 
     * <p>用于同步调用服务注册、注销、查询等操作。
     * 在 {@link #initializeStubs()} 方法中初始化。</p>
     */
    private ServiceRegistryGrpc.ServiceRegistryBlockingStub blockingStub;
    
    /** 
     * 服务注册发现 Stub（异步）
     * 
     * <p>用于异步调用服务订阅等流式操作。
     * 在 {@link #initializeStubs()} 方法中初始化。</p>
     */
    private ServiceRegistryGrpc.ServiceRegistryStub asyncStub;
    
    // ========== 心跳管理 ==========
    
    /** 
     * 节点ID池
     * 
     * <p>维护所有已注册的节点ID，用于健康检查上报和心跳时构建 Service 信息。
     * Key 为节点ID（nodeId），Value 为节点信息（包含命名空间、分组、服务名等）。</p>
     * 
     * <p>生命周期：</p>
     * <ul>
     *   <li>添加：在节点注册成功时自动添加到池中</li>
     *   <li>移除：在节点注销时自动从池中移除</li>
     *   <li>清理：在 {@link #close()} 时清空所有节点ID</li>
     * </ul>
     */
    private final Map<String, NodeInfo> registeredNodes = new ConcurrentHashMap<>();
    
    /** 
     * 心跳任务映射表
     * 
     * <p>维护所有活跃节点的心跳任务。Key 为节点ID（nodeId），Value 为心跳任务的 ScheduledFuture。</p>
     * 
     * <p>生命周期：</p>
     * <ul>
     *   <li>添加：在节点注册成功时自动启动心跳任务</li>
     *   <li>移除：在节点注销时自动停止并移除心跳任务</li>
     *   <li>清理：在 {@link #close()} 时停止所有心跳任务</li>
     * </ul>
     */
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    
    // ========== 订阅管理 ==========
    
    /** 
     * 服务订阅上下文映射表
     * 
     * <p>维护所有活跃的服务订阅。Key 为订阅ID（subscriptionId），Value 为订阅上下文。</p>
     * 
     * <p>订阅上下文包含：</p>
     * <ul>
     *   <li>订阅参数（namespaceId、groupName、serviceNames）</li>
     *   <li>监听器（listener）</li>
     *   <li>响应观察者（responseObserver），用于重连时恢复订阅</li>
     * </ul>
     * 
     * <p>生命周期：</p>
     * <ul>
     *   <li>添加：在 {@link #subscribe(String, String, List, ServiceChangeListener)} 时创建</li>
     *   <li>移除：在订阅连接断开或完成时自动移除</li>
     *   <li>清理：在 {@link #close()} 时取消所有订阅</li>
     * </ul>
     */
    private final Map<String, ServiceSubscriptionContext> subscriptions = new ConcurrentHashMap<>();
    
    // ========== 状态管理 ==========
    
    /** 
     * 关闭状态标志
     * 
     * <p>标识服务注册发现管理器是否已关闭。使用 {@link AtomicBoolean} 保证线程安全。</p>
     * 
     * <p>一旦设置为 true，将不再允许新的操作，确保资源正确释放。</p>
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    /**
     * 创建服务注册发现管理器
     * 
     * @param config 客户端配置
     * @param connectionManager 连接管理器
     * @param heartbeatExecutor 心跳执行器
     * @param subscriptionExecutor 订阅执行器
     */
    public ServiceRegistryManager(ServiceCenterConfig config,
                                 ConnectionManager connectionManager,
                                 ScheduledExecutorService heartbeatExecutor,
                                 ExecutorService subscriptionExecutor) {
        this.config = config;
        this.connectionManager = connectionManager;
        this.heartbeatExecutor = heartbeatExecutor;
        this.subscriptionExecutor = subscriptionExecutor;
    }
    
    /**
     * 初始化 Stub（在连接建立后调用）
     * 
     * <p>注意：不在创建时设置 deadline，改为在每次调用时动态设置，
     * 避免因任务延迟执行导致 deadline 过期的问题。</p>
     */
    public void initializeStubs() {
        ClientInterceptor interceptor = connectionManager.getMetadataInterceptor();
        
        // 不在创建时设置 deadline，改为在每次调用时动态设置
        blockingStub = ServiceRegistryGrpc.newBlockingStub(connectionManager.getChannel())
                .withInterceptors(interceptor);
        
        asyncStub = ServiceRegistryGrpc.newStub(connectionManager.getChannel())
                .withInterceptors(interceptor);
    }
    
    /**
     * 注册服务（使用领域对象）
     * 
     * <p>如果服务信息中包含节点（node 字段），则同时注册服务和节点。
     * 注册成功后，如果返回了 nodeId，会自动启动心跳任务。</p>
     * 
     * @param serviceInfo 服务信息领域对象，不能为 null
     * @param nodeInfo 可选的节点信息领域对象，如果提供则同时注册节点
     * @return 注册服务结果（包含生成的 nodeId，如果注册了节点）
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果注册失败
     */
    public RegisterServiceResult registerService(ServiceInfo serviceInfo, NodeInfo nodeInfo) {
        checkNotClosed();
        if (serviceInfo == null) {
            throw new IllegalArgumentException("服务信息不能为 null");
        }
        
        try {
            RegistryProto.Service.Builder serviceBuilder = ProtoConverter.toProtoService(serviceInfo).toBuilder();
            if (nodeInfo != null) {
                serviceBuilder.setNode(ProtoConverter.toProtoNode(nodeInfo));
            }
            RegistryProto.Service service = serviceBuilder.build();
            
            // 在每次调用时动态设置 deadline，避免因任务延迟执行导致 deadline 过期
            RegistryProto.RegisterServiceResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .registerService(service);
            logger.info("服务注册响应: success={}, message={}, nodeId={}", 
                    response.getSuccess(), response.getMessage(), response.getNodeId());
            
            RegisterServiceResult result = ProtoConverter.toRegisterServiceResult(response);
            
            if (result.isSuccess()) {
                logger.info("服务注册成功: {}/{}/{}", 
                        serviceInfo.getNamespaceId(), 
                        serviceInfo.getGroupName(), 
                        serviceInfo.getServiceName());
                
                // 如果注册了节点且返回了 nodeId，添加到节点ID池并启动心跳
                if (nodeInfo != null && result.getNodeId() != null && !result.getNodeId().isEmpty()) {
                    String nodeId = result.getNodeId();
                    // 创建节点信息副本并设置 nodeId
                    NodeInfo registeredNode = new NodeInfo();
                    registeredNode.setNodeId(nodeId);
                    registeredNode.setNamespaceId(serviceInfo.getNamespaceId());
                    registeredNode.setGroupName(serviceInfo.getGroupName());
                    registeredNode.setServiceName(serviceInfo.getServiceName());
                    if (nodeInfo.getIpAddress() != null) {
                        registeredNode.setIpAddress(nodeInfo.getIpAddress());
                    }
                    if (nodeInfo.getPortNumber() > 0) {
                        registeredNode.setPortNumber(nodeInfo.getPortNumber());
                    }
                    // 添加到节点ID池
                    registeredNodes.put(nodeId, registeredNode);
                    // 启动心跳
                    startHeartbeat(nodeId);
                }
            } else {
                logger.warn("服务注册失败: {}", result.getMessage());
            }
            return result;
        } catch (Exception e) {
            logger.error("注册服务失败", e);
            throw new RuntimeException("注册服务失败", e);
        }
    }
    
    /**
     * 注销服务
     * 
     * <p>如果指定了 nodeId，则只删除该节点；否则删除整个服务及其所有节点。</p>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param serviceName 服务名，不能为空
     * @param nodeId 可选的节点ID，如果指定则只删除该节点
     * @return 操作结果
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果注销失败
     */
    public OperationResult unregisterService(String namespaceId, String groupName, 
                                             String serviceName, String nodeId) {
        checkNotClosed();
        try {
            RegistryProto.ServiceKey.Builder builder = RegistryProto.ServiceKey.newBuilder()
                    .setNamespaceId(namespaceId)
                    .setGroupName(groupName != null ? groupName : "DEFAULT_GROUP")
                    .setServiceName(serviceName);
            
            if (nodeId != null && !nodeId.isEmpty()) {
                builder.setNodeId(nodeId);
                // 从节点ID池移除
                registeredNodes.remove(nodeId);
                // 停止心跳
                stopHeartbeat(nodeId);
            }
            
            // 在每次调用时动态设置 deadline
            RegistryProto.RegistryResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .unregisterService(builder.build());
            logger.info("服务注销响应: success={}, message={}, code={}", 
                    response.getSuccess(), response.getMessage(), response.getCode());
            
            OperationResult result = ProtoConverter.toOperationResult(response);
            if (result.isSuccess()) {
                logger.info("服务注销成功: {}/{}/{}", namespaceId, groupName, serviceName);
            } else {
                logger.warn("服务注销失败: {}", result.getMessage());
            }
            return result;
        } catch (Exception e) {
            logger.error("注销服务失败", e);
            throw new RuntimeException("注销服务失败", e);
        }
    }
    
    /**
     * 获取服务信息（包含节点列表）
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param serviceName 服务名，不能为空
     * @return 获取服务结果（包含服务信息和节点列表领域对象）
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果获取失败
     */
    public GetServiceResult getService(String namespaceId, String groupName, String serviceName) {
        checkNotClosed();
        try {
            RegistryProto.ServiceKey serviceKey = RegistryProto.ServiceKey.newBuilder()
                    .setNamespaceId(namespaceId)
                    .setGroupName(groupName != null ? groupName : "DEFAULT_GROUP")
                    .setServiceName(serviceName)
                    .build();
            
            // 在每次调用时动态设置 deadline
            RegistryProto.GetServiceResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .getService(serviceKey);
            logger.info("获取服务响应: success={}, message={}, service={}, nodesCount={}", 
                    response.getSuccess(), response.getMessage(), 
                    response.hasService() ? response.getService().getServiceName() : "null",
                    response.getNodesCount());
            
            return ProtoConverter.toGetServiceResult(response);
        } catch (Exception e) {
            logger.error("获取服务信息失败", e);
            throw new RuntimeException("获取服务信息失败", e);
        }
    }
    
    /**
     * 注册服务节点（使用领域对象）
     * 
     * <p>注册成功后会自动启动心跳任务。</p>
     * 
     * @param nodeInfo 节点信息领域对象，不能为 null
     * @return 注册节点结果（包含生成的 nodeId）
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果注册失败
     */
    public RegisterNodeResult registerNode(NodeInfo nodeInfo) {
        checkNotClosed();
        if (nodeInfo == null) {
            throw new IllegalArgumentException("节点信息不能为 null");
        }
        
        try {
            RegistryProto.Node node = ProtoConverter.toProtoNode(nodeInfo);
            // 在每次调用时动态设置 deadline
            RegistryProto.RegisterNodeResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .registerNode(node);
            logger.info("节点注册响应: success={}, message={}, nodeId={}", 
                    response.getSuccess(), response.getMessage(), response.getNodeId());
            
            RegisterNodeResult result = ProtoConverter.toRegisterNodeResult(response);
            
            if (result.isSuccess() && result.getNodeId() != null && !result.getNodeId().isEmpty()) {
                String nodeId = result.getNodeId();
                logger.info("节点注册成功: nodeId={}", nodeId);
                // 添加到节点ID池
                NodeInfo registeredNode = new NodeInfo();
                registeredNode.setNodeId(nodeId);
                registeredNode.setNamespaceId(nodeInfo.getNamespaceId());
                registeredNode.setGroupName(nodeInfo.getGroupName());
                registeredNode.setServiceName(nodeInfo.getServiceName());
                registeredNode.setIpAddress(nodeInfo.getIpAddress());
                registeredNode.setPortNumber(nodeInfo.getPortNumber());
                // 复制其他节点信息
                if (nodeInfo.getWeight() > 0) {
                    registeredNode.setWeight(nodeInfo.getWeight());
                }
                if (nodeInfo.getEphemeral() != null) {
                    registeredNode.setEphemeral(nodeInfo.getEphemeral());
                }
                if (nodeInfo.getInstanceStatus() != null) {
                    registeredNode.setInstanceStatus(nodeInfo.getInstanceStatus());
                }
                if (nodeInfo.getHealthyStatus() != null) {
                    registeredNode.setHealthyStatus(nodeInfo.getHealthyStatus());
                }
                if (nodeInfo.getMetadata() != null) {
                    registeredNode.setMetadata(new HashMap<>(nodeInfo.getMetadata()));
                }
                registeredNodes.put(nodeId, registeredNode);
                // 启动心跳
                startHeartbeat(nodeId);
            } else {
                logger.warn("节点注册失败: {}", result.getMessage());
            }
            return result;
        } catch (Exception e) {
            logger.error("注册节点失败", e);
            throw new RuntimeException("注册节点失败", e);
        }
    }
    
    /**
     * 注销服务节点
     * 
     * <p>注销成功后会自动停止心跳任务。</p>
     * 
     * @param nodeId 节点ID，不能为空
     * @return 操作结果
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果注销失败
     */
    public OperationResult unregisterNode(String nodeId) {
        checkNotClosed();
        if (nodeId == null || nodeId.isEmpty()) {
            throw new IllegalArgumentException("节点ID不能为空");
        }
        
        try {
            RegistryProto.NodeKey nodeKey = RegistryProto.NodeKey.newBuilder().setNodeId(nodeId).build();
            // 在每次调用时动态设置 deadline
            RegistryProto.RegistryResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .unregisterNode(nodeKey);
            logger.info("节点注销响应: success={}, message={}, code={}", 
                    response.getSuccess(), response.getMessage(), response.getCode());
            
            OperationResult result = ProtoConverter.toOperationResult(response);
            
            if (result.isSuccess()) {
                logger.info("节点注销成功: nodeId={}", nodeId);
                // 从节点ID池移除
                registeredNodes.remove(nodeId);
                // 停止心跳
                stopHeartbeat(nodeId);
            } else {
                logger.warn("节点注销失败: {}", result.getMessage());
            }
            return result;
        } catch (Exception e) {
            logger.error("注销节点失败", e);
            throw new RuntimeException("注销节点失败", e);
        }
    }
    
    /**
     * 发现服务节点（一次性查询）
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param serviceName 服务名，不能为空
     * @param healthyOnly 是否只返回健康节点
     * @return 节点信息列表（领域对象），如果失败则返回空列表
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果发现失败
     */
    public List<NodeInfo> discoverNodes(String namespaceId, String groupName, 
                                        String serviceName, boolean healthyOnly) {
        checkNotClosed();
        try {
            RegistryProto.DiscoverNodesRequest request = RegistryProto.DiscoverNodesRequest.newBuilder()
                    .setNamespaceId(namespaceId)
                    .setGroupName(groupName != null ? groupName : "DEFAULT_GROUP")
                    .setServiceName(serviceName)
                    .setHealthyOnly(healthyOnly)
                    .build();
            
            // 在每次调用时动态设置 deadline
            RegistryProto.DiscoverNodesResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .discoverNodes(request);
            logger.info("发现节点响应: success={}, message={}, nodesCount={}", 
                    response.getSuccess(), response.getMessage(), response.getNodesCount());
            
            if (response.getSuccess()) {
                return ProtoConverter.toNodeInfoList(response.getNodesList());
            } else {
                logger.warn("发现节点失败: {}", response.getMessage());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("发现节点失败", e);
            throw new RuntimeException("发现节点失败", e);
        }
    }
    
    /**
     * 订阅服务变更（统一接口）
     * 
     * <p>支持订阅单个或多个服务，也支持订阅整个命名空间/分组。
     * 使用领域对象而非 Proto 对象，实现业务层与 Proto 层的解耦。</p>
     * 
     * <p>订阅成功后，当服务节点发生变更（添加、更新、删除）时，会通过监听器实时推送。
     * 订阅连接断开时会自动重连，重连间隔和最大重试次数由配置决定。</p>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param serviceNames 服务名列表（可选）：
     *                     - 如果为 null 或空列表，则订阅整个命名空间/分组下的所有服务
     *                     - 如果指定了服务名列表，则只订阅指定的服务
     * @param listener 变更监听器，不能为 null
     * @return 订阅ID（用于取消订阅）
     * @throws IllegalStateException 如果客户端未连接
     */
    public String subscribe(String namespaceId, String groupName, 
                           List<String> serviceNames, ServiceChangeListener listener) {
        checkNotClosed();
        if (listener == null) {
            throw new IllegalArgumentException("监听器不能为 null");
        }
        
        String subscriptionId = UUID.randomUUID().toString();
        String groupKey = groupName != null ? groupName : "DEFAULT_GROUP";
        
        // 创建响应观察者，将 Proto 对象转换为领域对象
        StreamObserver<RegistryProto.ServiceChangeEvent> responseObserver = new StreamObserver<RegistryProto.ServiceChangeEvent>() {
            @Override
            public void onNext(RegistryProto.ServiceChangeEvent protoEvent) {
                try {
                    // 将 Proto 对象转换为领域对象
                    ServiceChangeEvent event = ProtoConverter.toServiceChangeEvent(protoEvent);
                    if (event == null) {
                        logger.warn("转换服务变更事件失败，事件为 null");
                        return;
                    }
                    
                    // 调用监听器（使用领域对象）
                    listener.onServiceChange(event);
                } catch (Exception e) {
                    logger.error("处理服务变更事件失败", e);
                }
            }
            
            @Override
            public void onError(Throwable t) {
                // 检查是否是正常的关闭（客户端主动关闭连接）
                // 只有明确的客户端主动关闭（Channel shutdownNow invoked）才不重连
                // 服务端关闭或其他网络错误都应该触发重连
                boolean isNormalShutdown = false;
                if (t instanceof StatusRuntimeException) {
                    StatusRuntimeException sre = (StatusRuntimeException) t;
                    Status status = sre.getStatus();
                    String description = status.getDescription();
                    // 只有明确的客户端主动关闭才认为是正常关闭
                    // "Channel shutdownNow invoked" 表示客户端调用了 channel.shutdownNow()
                    if (status.getCode() == Status.Code.UNAVAILABLE && 
                        description != null && 
                        description.contains("Channel shutdownNow invoked")) {
                        isNormalShutdown = true;
                    }
                }
                
                if (isNormalShutdown) {
                    // 正常关闭（客户端主动关闭），记录为 INFO 级别，不重连
                    logger.info("订阅服务变更连接已关闭（客户端主动关闭）: subscriptionId={}", subscriptionId);
                } else {
                    // 异常关闭（服务端关闭、网络错误等），记录为 WARN 级别，触发重连
                    logger.warn("订阅服务变更连接断开，将自动重连: subscriptionId={}, error={}", 
                            subscriptionId, t.getMessage());
                }
                
                listener.onDisconnected(t);
                subscriptions.remove(subscriptionId);
                
                // 只有在非正常关闭时才自动重连
                if (!isNormalShutdown) {
                    reconnectSubscription(subscriptionId, namespaceId, groupName, serviceNames, listener);
                }
            }
            
            @Override
            public void onCompleted() {
                logger.info("订阅服务变更完成: {}", subscriptionId);
                subscriptions.remove(subscriptionId);
            }
        };
        
        // 根据是否指定服务名列表，选择不同的订阅方式
        if (serviceNames != null && !serviceNames.isEmpty()) {
            // 订阅指定的服务
            RegistryProto.SubscribeServicesRequest request = RegistryProto.SubscribeServicesRequest.newBuilder()
                    .setNamespaceId(namespaceId)
                    .setGroupName(groupKey)
                    .addAllServiceNames(serviceNames)
                    .build();
            
            asyncStub.subscribeServices(request, responseObserver);
            
            logger.info("已订阅服务变更: subscriptionId={}, namespaceId={}, groupName={}, services={}", 
                    subscriptionId, namespaceId, groupKey, serviceNames);
        } else {
            // 订阅整个命名空间/分组
            RegistryProto.SubscribeNamespaceRequest.Builder requestBuilder = RegistryProto.SubscribeNamespaceRequest.newBuilder()
                    .setNamespaceId(namespaceId);
            if (groupKey != null && !groupKey.isEmpty() && !"DEFAULT_GROUP".equals(groupKey)) {
                requestBuilder.setGroupName(groupKey);
            }
            RegistryProto.SubscribeNamespaceRequest request = requestBuilder.build();
            
            asyncStub.subscribeNamespace(request, responseObserver);
            
            logger.info("已订阅命名空间变更: subscriptionId={}, namespaceId={}, groupName={}", 
                    subscriptionId, namespaceId, groupKey);
        }
        
        subscriptions.put(subscriptionId, new ServiceSubscriptionContext(
                subscriptionId, namespaceId, groupName, serviceNames, listener, responseObserver));
        
        return subscriptionId;
    }
    
    /**
     * 取消订阅
     */
    public void unsubscribe(String subscriptionId) {
        ServiceSubscriptionContext context = subscriptions.remove(subscriptionId);
        if (context != null) {
            logger.info("已取消服务订阅: {}", subscriptionId);
        }
    }
    
    /**
     * 发送心跳
     * 
     * <p>用于保持节点在线状态。通常不需要手动调用，注册节点后会自动启动心跳任务。</p>
     * 
     * <p>心跳请求包含完整的 Service 信息（包含节点信息），用于：</p>
     * <ul>
     *   <li>网络重连后可以完整恢复服务和节点信息</li>
     *   <li>验证服务信息是否一致</li>
     *   <li>更新服务信息（如服务版本、描述、元数据等可能变化）</li>
     *   <li>更新节点信息（如 IP、端口、权重、元数据等可能变化）</li>
     *   <li>连接跟踪器可以基于完整信息建立连接映射</li>
     * </ul>
     * 
     * @param nodeId 节点ID，不能为空
     * @return 操作结果
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果发送心跳失败
     */
    public OperationResult heartbeat(String nodeId) {
        checkNotClosed();
        if (nodeId == null || nodeId.isEmpty()) {
            throw new IllegalArgumentException("节点ID不能为空");
        }
        
        try {
            // 从节点ID池中获取节点信息
            NodeInfo nodeInfo = registeredNodes.get(nodeId);
            
            // 构建心跳请求
            RegistryProto.HeartbeatRequest.Builder requestBuilder = RegistryProto.HeartbeatRequest.newBuilder()
                    .setNodeId(nodeId);
            
            // 如果节点信息存在，构建完整的 Service 信息
            if (nodeInfo != null) {
                // 从节点信息构建基本的 Service 信息（节点信息中已包含服务的基本信息）
                RegistryProto.Service.Builder serviceBuilder = RegistryProto.Service.newBuilder()
                        .setNamespaceId(nodeInfo.getNamespaceId() != null ? nodeInfo.getNamespaceId() : "")
                        .setGroupName(nodeInfo.getGroupName() != null ? nodeInfo.getGroupName() : "DEFAULT_GROUP")
                        .setServiceName(nodeInfo.getServiceName() != null ? nodeInfo.getServiceName() : "");
                
                // 构建节点信息并添加到 Service 中
                RegistryProto.Node protoNode = ProtoConverter.toProtoNode(nodeInfo);
                if (protoNode != null) {
                    serviceBuilder.setNode(protoNode);
                }
                
                requestBuilder.setService(serviceBuilder.build());
            }
            
            RegistryProto.HeartbeatRequest request = requestBuilder.build();
            // 在每次调用时动态设置 deadline，避免因任务延迟执行导致 deadline 过期
            // 这是解决 DEADLINE_EXCEEDED 问题的关键：每次调用时基于当前时间计算 deadline
            RegistryProto.RegistryResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .heartbeat(request);
            logger.info("心跳响应: nodeId={}, success={}, message={}, code={}", 
                    nodeId, response.getSuccess(), response.getMessage(), response.getCode());
            
            return ProtoConverter.toOperationResult(response);
        } catch (io.grpc.StatusRuntimeException e) {
            // gRPC 异常直接抛出，让上层处理重连逻辑
            logger.error("心跳 gRPC 异常: nodeId={}, status={}", nodeId, e.getStatus(), e);
            throw e; // 直接抛出，不包装
        } catch (Exception e) {
            logger.error("发送心跳失败: nodeId={}", nodeId, e);
            throw new RuntimeException("发送心跳失败", e);
        }
    }
    
    /**
     * 启动心跳任务
     */
    private void startHeartbeat(String nodeId) {
        if (heartbeatTasks.containsKey(nodeId)) {
            logger.warn("节点 {} 的心跳任务已存在", nodeId);
            return;
        }
        
        ScheduledFuture<?> future = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                // 检查连接状态，如果断开则重连
                if (!connectionManager.isConnected()) {
                    logger.warn("连接已断开，尝试重连: nodeId={}", nodeId);
                    try {
                        connectionManager.reconnect();
                        if (!connectionManager.isConnected()) {
                            logger.error("重连失败，跳过本次心跳: nodeId={}", nodeId);
                            return; // 重连失败，跳过本次心跳
                        }
                        logger.info("重连成功，继续心跳: nodeId={}", nodeId);
                    } catch (Exception reconnectErr) {
                        logger.error("重连异常，跳过本次心跳: nodeId={}", nodeId, reconnectErr);
                        return; // 重连异常，跳过本次心跳
                    }
                }
                
                OperationResult result = heartbeat(nodeId);
                if (!result.isSuccess()) {
                    logger.warn("心跳失败: nodeId={}, message={}", nodeId, result.getMessage());
                    // 心跳失败可能是连接问题，检查连接状态
                    if (!connectionManager.isConnected()) {
                        logger.warn("心跳失败后检测到连接断开，将在下次心跳时重连: nodeId={}", nodeId);
                    }
                }
            } catch (io.grpc.StatusRuntimeException e) {
                // gRPC 异常（如 DEADLINE_EXCEEDED、UNAVAILABLE 等）通常是连接问题
                io.grpc.Status.Code statusCode = e.getStatus().getCode();
                logger.error("心跳 gRPC 异常: nodeId={}, status={}, code={}", nodeId, e.getStatus(), statusCode, e);
                
                // 这些状态码通常表示连接问题，需要重连
                if (statusCode == io.grpc.Status.Code.DEADLINE_EXCEEDED || 
                    statusCode == io.grpc.Status.Code.UNAVAILABLE ||
                    statusCode == io.grpc.Status.Code.UNAUTHENTICATED ||
                    statusCode == io.grpc.Status.Code.ABORTED ||
                    statusCode == io.grpc.Status.Code.CANCELLED) {
                    // 标记连接为断开状态，下次心跳时会自动重连
                    logger.warn("检测到连接问题，标记连接为断开: nodeId={}, code={}", nodeId, statusCode);
                    connectionManager.markDisconnected();
                }
            } catch (RuntimeException e) {
                // 检查是否是包装了 StatusRuntimeException 的 RuntimeException
                Throwable cause = e.getCause();
                if (cause instanceof io.grpc.StatusRuntimeException) {
                    io.grpc.StatusRuntimeException grpcException = (io.grpc.StatusRuntimeException) cause;
                    io.grpc.Status.Code statusCode = grpcException.getStatus().getCode();
                    logger.error("心跳异常（包装的 gRPC 异常）: nodeId={}, status={}, code={}", 
                            nodeId, grpcException.getStatus(), statusCode, e);
                    // 处理连接问题
                    if (statusCode == io.grpc.Status.Code.DEADLINE_EXCEEDED || 
                        statusCode == io.grpc.Status.Code.UNAVAILABLE ||
                        statusCode == io.grpc.Status.Code.UNAUTHENTICATED ||
                        statusCode == io.grpc.Status.Code.ABORTED ||
                        statusCode == io.grpc.Status.Code.CANCELLED) {
                        logger.warn("检测到连接问题（从包装异常中），标记连接为断开: nodeId={}, code={}", nodeId, statusCode);
                        connectionManager.markDisconnected();
                    }
                } else {
                    logger.error("心跳异常: nodeId={}", nodeId, e);
                }
            } catch (Exception e) {
                logger.error("心跳异常: nodeId={}", nodeId, e);
            }
        }, 0, config.getHeartbeatInterval(), TimeUnit.MILLISECONDS);
        
        heartbeatTasks.put(nodeId, future);
        logger.info("已启动心跳任务: nodeId={}, interval={}ms", nodeId, config.getHeartbeatInterval());
    }
    
    /**
     * 停止心跳任务
     */
    private void stopHeartbeat(String nodeId) {
        ScheduledFuture<?> future = heartbeatTasks.remove(nodeId);
        if (future != null) {
            future.cancel(false);
            logger.info("已停止心跳任务: nodeId={}", nodeId);
        }
    }
    
    /**
     * 获取所有已注册的节点ID
     * 
     * @return 节点ID集合（不可修改的副本）
     */
    public Set<String> getRegisteredNodeIds() {
        return Collections.unmodifiableSet(new HashSet<>(registeredNodes.keySet()));
    }
    
    /**
     * 获取已注册的节点信息
     * 
     * @param nodeId 节点ID
     * @return 节点信息，如果不存在则返回 null
     */
    public NodeInfo getRegisteredNode(String nodeId) {
        NodeInfo node = registeredNodes.get(nodeId);
        if (node != null) {
            // 返回副本，避免外部修改
            NodeInfo copy = new NodeInfo();
            copy.setNodeId(node.getNodeId());
            copy.setNamespaceId(node.getNamespaceId());
            copy.setGroupName(node.getGroupName());
            copy.setServiceName(node.getServiceName());
            copy.setIpAddress(node.getIpAddress());
            copy.setPortNumber(node.getPortNumber());
            return copy;
        }
        return null;
    }
    
    /**
     * 获取所有已注册的节点信息
     * 
     * @return 节点信息列表（不可修改的副本）
     */
    public List<NodeInfo> getAllRegisteredNodes() {
        List<NodeInfo> nodes = new ArrayList<>();
        for (NodeInfo node : registeredNodes.values()) {
            // 返回副本，避免外部修改
            NodeInfo copy = new NodeInfo();
            copy.setNodeId(node.getNodeId());
            copy.setNamespaceId(node.getNamespaceId());
            copy.setGroupName(node.getGroupName());
            copy.setServiceName(node.getServiceName());
            copy.setIpAddress(node.getIpAddress());
            copy.setPortNumber(node.getPortNumber());
            nodes.add(copy);
        }
        return Collections.unmodifiableList(nodes);
    }
    
    /**
     * 重连订阅
     */
    private void reconnectSubscription(String subscriptionId, String namespaceId, 
                                      String groupName, List<String> serviceNames, 
                                      ServiceChangeListener listener) {
        subscriptionExecutor.submit(() -> {
            int attempts = 0;
            long backoffMs = config.getReconnectInterval();
            final long maxBackoffMs = 30000;
            
            while (!closed.get() && (config.getMaxReconnectAttempts() < 0 || attempts < config.getMaxReconnectAttempts())) {
                try {
                    Thread.sleep(backoffMs);
                    attempts++;
                    backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
                    
                    logger.info("尝试重连服务订阅: subscriptionId={}, attempt={}, backoff={}ms", 
                            subscriptionId, attempts, backoffMs);
                    
                    // 检查是否已有相同订阅
                    boolean hasDuplicate = false;
                    for (ServiceSubscriptionContext ctx : subscriptions.values()) {
                        if (ctx.namespaceId.equals(namespaceId) && 
                            ctx.groupName.equals(groupName) &&
                            ctx.serviceNames != null && ctx.serviceNames.equals(serviceNames)) {
                            logger.info("发现已有相同订阅，跳过重连: subscriptionId={}", ctx.subscriptionId);
                            hasDuplicate = true;
                            break;
                        }
                    }
                    
                    if (hasDuplicate) {
                        return;
                    }
                    
                    // 重新连接
                    if (!connectionManager.isConnected()) {
                        connectionManager.reconnect();
                    }
                    
                    if (!connectionManager.isConnected()) {
                        continue;
                    }
                    
                    // 重新订阅
                    String newSubscriptionId = subscribe(namespaceId, groupName, serviceNames, listener);
                    logger.info("服务订阅重连成功: oldSubscriptionId={}, newSubscriptionId={}, attempts={}", 
                            subscriptionId, newSubscriptionId, attempts);
                    
                    listener.onReconnected();
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("服务订阅重连被中断: subscriptionId={}", subscriptionId);
                    return;
                } catch (Exception e) {
                    logger.warn("服务订阅重连失败: subscriptionId={}, attempt={}", subscriptionId, attempts, e);
                }
            }
            
            if (!closed.get()) {
                logger.error("服务订阅重连失败，已达到最大重试次数: subscriptionId={}, attempts={}", 
                        subscriptionId, attempts);
            }
        });
    }
    
    /**
     * 关闭管理器
     * 
     * <p>优雅关闭流程：</p>
     * <ol>
     *   <li>注销所有已注册的节点（向服务端发送注销请求）</li>
     *   <li>停止所有心跳任务</li>
     *   <li>取消所有服务订阅</li>
     *   <li>清空本地缓存</li>
     * </ol>
     */
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }
        
        logger.info("正在关闭服务注册发现管理器...");
        
        // 1. 注销所有已注册的节点（向服务端发送注销请求）
        // 需要在停止心跳之前完成，因为注销操作需要连接
        List<String> nodeIds = new ArrayList<>(registeredNodes.keySet());
        if (!nodeIds.isEmpty()) {
            logger.info("正在注销 {} 个已注册的节点...", nodeIds.size());
            for (String nodeId : nodeIds) {
                try {
                    // 调用 unregisterNode 向服务端发送注销请求
                    unregisterNode(nodeId);
                    logger.debug("已注销节点: {}", nodeId);
                } catch (Exception e) {
                    // 注销失败不影响关闭流程，只记录警告
                    logger.warn("注销节点失败: nodeId={}, error={}", nodeId, e.getMessage());
                }
            }
            logger.info("节点注销完成");
        }
        
        // 2. 停止所有心跳任务
        for (String nodeId : new ArrayList<>(heartbeatTasks.keySet())) {
            stopHeartbeat(nodeId);
        }
        
        // 3. 取消所有服务订阅
        for (String subscriptionId : new ArrayList<>(subscriptions.keySet())) {
            try {
                unsubscribe(subscriptionId);
            } catch (Exception e) {
                logger.warn("取消订阅失败: subscriptionId={}, error={}", subscriptionId, e.getMessage());
            }
        }
        
        // 4. 清空本地缓存
        registeredNodes.clear();
        heartbeatTasks.clear();
        subscriptions.clear();
        
        logger.info("服务注册发现管理器已关闭");
    }
    
    /**
     * 检查未关闭
     */
    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("服务注册发现管理器已关闭");
        }
        if (!connectionManager.isConnected()) {
            throw new IllegalStateException("未连接，请先调用 connect()");
        }
    }
    
    /**
     * 服务订阅上下文
     */
    @SuppressWarnings("unused")
    private static class ServiceSubscriptionContext {
        final String subscriptionId;
        final String namespaceId;
        final String groupName;
        final List<String> serviceNames;
        final ServiceChangeListener listener;
        final StreamObserver<?> responseObserver;
        
        ServiceSubscriptionContext(String subscriptionId, String namespaceId, String groupName,
                           List<String> serviceNames, ServiceChangeListener listener,
                           StreamObserver<?> responseObserver) {
            this.subscriptionId = subscriptionId;
            this.namespaceId = namespaceId;
            this.groupName = groupName;
            this.serviceNames = serviceNames;
            this.listener = listener;
            this.responseObserver = responseObserver;
        }
    }
}

