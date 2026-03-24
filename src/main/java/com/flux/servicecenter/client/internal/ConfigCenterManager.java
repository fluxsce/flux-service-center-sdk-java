package com.flux.servicecenter.client.internal;

import com.flux.servicecenter.config.ConfigProto;
import com.flux.servicecenter.config.ConfigCenterGrpc; // 自动生成的 gRPC 服务类
import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.model.*;
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
 * 配置中心管理器
 * 
 * <p>负责配置管理、监听等业务逻辑。
 * 与连接管理分离，专注于配置中心功能实现。</p>
 * 
 * @author shangjian
 */
public class ConfigCenterManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigCenterManager.class);
    
    // ========== 配置和依赖 ==========
    
    /** 
     * 客户端配置
     * 
     * <p>包含重连策略、超时时间等配置信息。</p>
     */
    private final ServiceCenterConfig config;
    
    /** 
     * 连接管理器
     * 
     * <p>用于获取 gRPC 通道和元数据拦截器，不直接管理连接生命周期。</p>
     */
    private final ConnectionManager connectionManager;
    
    /** 
     * 订阅执行器
     * 
     * <p>用于执行订阅重连任务。使用固定大小的线程池，避免无限制创建线程。</p>
     */
    private final ExecutorService subscriptionExecutor;
    
    // ========== gRPC Stub ==========
    
    /** 
     * 配置中心 Stub（阻塞式）
     * 
     * <p>用于同步调用配置获取、保存、删除等操作。
     * 在 {@link #initializeStubs()} 方法中初始化。</p>
     */
    private ConfigCenterGrpc.ConfigCenterBlockingStub blockingStub;
    
    /** 
     * 配置中心 Stub（异步）
     * 
     * <p>用于异步调用配置监听等流式操作。
     * 在 {@link #initializeStubs()} 方法中初始化。</p>
     */
    private ConfigCenterGrpc.ConfigCenterStub asyncStub;
    
    // ========== 订阅管理 ==========
    
    /** 
     * 配置订阅上下文映射表
     * 
     * <p>维护所有活跃的配置订阅。Key 为订阅ID（subscriptionId），Value 为订阅上下文。</p>
     * 
     * <p>订阅上下文包含：</p>
     * <ul>
     *   <li>订阅参数（namespaceId、groupName、configDataIds）</li>
     *   <li>监听器（listener）</li>
     *   <li>响应观察者（responseObserver），用于重连时恢复订阅</li>
     * </ul>
     * 
     * <p>生命周期：</p>
     * <ul>
     *   <li>添加：在 {@link #watchConfig(String, String, List, ConfigChangeListener)} 时创建</li>
     *   <li>移除：在订阅连接断开或完成时自动移除</li>
     *   <li>清理：在 {@link #close()} 时取消所有订阅</li>
     * </ul>
     */
    private final Map<String, ConfigSubscriptionContext> subscriptions = new ConcurrentHashMap<>();
    
    // ========== 状态管理 ==========
    
    /** 
     * 关闭状态标志
     * 
     * <p>标识配置中心管理器是否已关闭。使用 {@link AtomicBoolean} 保证线程安全。</p>
     * 
     * <p>一旦设置为 true，将不再允许新的操作，确保资源正确释放。</p>
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    /**
     * 创建配置中心管理器
     * 
     * @param config 客户端配置
     * @param connectionManager 连接管理器
     * @param subscriptionExecutor 订阅执行器
     */
    public ConfigCenterManager(ServiceCenterConfig config,
                              ConnectionManager connectionManager,
                              ExecutorService subscriptionExecutor) {
        this.config = config;
        this.connectionManager = connectionManager;
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
        blockingStub = ConfigCenterGrpc.newBlockingStub(connectionManager.getChannel())
                .withInterceptors(interceptor);
        
        asyncStub = ConfigCenterGrpc.newStub(connectionManager.getChannel())
                .withInterceptors(interceptor);
    }
    
    /**
     * 获取配置
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置标识，不能为空
     * @return 获取配置结果（包含配置信息领域对象）
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果获取失败
     */
    public GetConfigResult getConfig(String namespaceId, String groupName, String configDataId) {
        checkNotClosed();
        try {
            ConfigProto.ConfigKey configKey = ConfigProto.ConfigKey.newBuilder()
                    .setNamespaceId(namespaceId)
                    .setGroupName(groupName != null ? groupName : "DEFAULT_GROUP")
                    .setConfigDataId(configDataId)
                    .build();
            
            // 在每次调用时动态设置 deadline
            ConfigProto.GetConfigResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .getConfig(configKey);
            logger.info("getConfig response: success={}, message={}, config={}", 
                    response.getSuccess(), response.getMessage(), 
                    response.hasConfig() ? response.getConfig().getConfigDataId() : "null");
            
            return ProtoConverter.toGetConfigResult(response);
        } catch (Exception e) {
            logger.error("getConfig failed", e);
            throw new RuntimeException("Get config failed", e);
        }
    }
    
    /**
     * 保存配置（使用领域对象）
     * 
     * <p>如果配置不存在则创建，如果已存在则更新。</p>
     * 
     * @param configInfo 配置信息领域对象，不能为 null
     * @return 保存配置结果（包含新版本号和 MD5）
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果保存失败
     */
    public SaveConfigResult saveConfig(ConfigInfo configInfo) {
        checkNotClosed();
        if (configInfo == null) {
            throw new IllegalArgumentException("ConfigInfo must not be null");
        }
        
        try {
            ConfigProto.ConfigData configData = ProtoConverter.toProtoConfigData(configInfo);
            // 在每次调用时动态设置 deadline
            ConfigProto.SaveConfigResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .saveConfig(configData);
            logger.info("saveConfig response: success={}, message={}, version={}, contentMd5={}", 
                    response.getSuccess(), response.getMessage(), response.getVersion(), response.getContentMd5());
            
            return ProtoConverter.toSaveConfigResult(response);
        } catch (Exception e) {
            logger.error("saveConfig failed", e);
            throw new RuntimeException("Save config failed", e);
        }
    }
    
    /**
     * 删除配置
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置标识，不能为空
     * @return 操作结果
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果删除失败
     */
    public OperationResult deleteConfig(String namespaceId, String groupName, String configDataId) {
        checkNotClosed();
        try {
            ConfigProto.ConfigKey configKey = ConfigProto.ConfigKey.newBuilder()
                    .setNamespaceId(namespaceId)
                    .setGroupName(groupName != null ? groupName : "DEFAULT_GROUP")
                    .setConfigDataId(configDataId)
                    .build();
            
            // 在每次调用时动态设置 deadline
            ConfigProto.ConfigResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .deleteConfig(configKey);
            logger.info("deleteConfig response: success={}, message={}, code={}", 
                    response.getSuccess(), response.getMessage(), response.getCode());
            
            OperationResult result = ProtoConverter.toOperationResult(response);
            if (result.isSuccess()) {
                logger.info("Config deleted: {}/{}/{}", namespaceId, groupName, configDataId);
            } else {
                logger.warn("Config delete failed: {}", result.getMessage());
            }
            return result;
        } catch (Exception e) {
            logger.error("deleteConfig failed", e);
            throw new RuntimeException("Delete config failed", e);
        }
    }
    
    /**
     * 列出配置列表
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @return 配置信息列表（领域对象），如果失败则返回空列表
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果列出失败
     */
    public List<ConfigInfo> listConfigs(String namespaceId, String groupName) {
        checkNotClosed();
        try {
            ConfigProto.ListConfigsRequest request = ConfigProto.ListConfigsRequest.newBuilder()
                    .setNamespaceId(namespaceId)
                    .setGroupName(groupName != null ? groupName : "DEFAULT_GROUP")
                    .build();
            
            // 在每次调用时动态设置 deadline
            ConfigProto.ListConfigsResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .listConfigs(request);
            logger.info("listConfigs response: success={}, message={}, configsCount={}", 
                    response.getSuccess(), response.getMessage(), response.getConfigsCount());
            
            if (response.getSuccess()) {
                return ProtoConverter.toConfigInfoList(response.getConfigsList());
            } else {
                logger.warn("listConfigs failed: {}", response.getMessage());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("listConfigs failed", e);
            throw new RuntimeException("List configs failed", e);
        }
    }
    
    /**
     * 监听配置变更
     */
    public String watchConfig(String namespaceId, String groupName, 
                              List<String> configDataIds, ConfigChangeListener listener) {
        checkNotClosed();
        if (configDataIds == null || configDataIds.isEmpty()) {
            throw new IllegalArgumentException("configDataIds must not be null or empty");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        
        String subscriptionId = UUID.randomUUID().toString();
        String groupKey = groupName != null ? groupName : "DEFAULT_GROUP";
        
        ConfigProto.WatchConfigRequest request = ConfigProto.WatchConfigRequest.newBuilder()
                .setNamespaceId(namespaceId)
                .setGroupName(groupKey)
                .addAllConfigDataIds(configDataIds)
                .build();
        
        // 创建响应观察者，将 Proto 对象转换为领域对象
        StreamObserver<ConfigProto.ConfigChangeEvent> responseObserver = new StreamObserver<ConfigProto.ConfigChangeEvent>() {
            @Override
            public void onNext(ConfigProto.ConfigChangeEvent protoEvent) {
                try {
                    // 将 Proto 对象转换为领域对象
                    ConfigChangeEvent event = ProtoConverter.toConfigChangeEvent(protoEvent);
                    if (event == null) {
                        logger.warn("toConfigChangeEvent returned null, skipping");
                        return;
                    }
                    
                    // 调用监听器（使用领域对象）
                    listener.onConfigChange(event);
                } catch (Exception e) {
                    logger.error("handle config change event failed", e);
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
                    logger.info("Config watch stream closed (client shutdown): watcherID={}", subscriptionId);
                } else {
                    // 异常关闭（服务端关闭、网络错误等），记录为 WARN 级别，触发重连
                    logger.warn("Config watch stream disconnected, will reconnect: watcherID={}, error={}", 
                            subscriptionId, t.getMessage());
                }
                
                listener.onDisconnected(t);
                subscriptions.remove(subscriptionId);
                
                // 只有在非正常关闭时才自动重连
                if (!isNormalShutdown) {
                    reconnectWatch(subscriptionId, namespaceId, groupName, configDataIds, listener);
                }
            }
            
            @Override
            public void onCompleted() {
                logger.info("Config watch stream completed: {}", subscriptionId);
                subscriptions.remove(subscriptionId);
            }
        };
        
        asyncStub.watchConfig(request, responseObserver);
        
        subscriptions.put(subscriptionId, new ConfigSubscriptionContext(
                subscriptionId, namespaceId, groupName, configDataIds, listener, responseObserver));
        
        logger.info("Config watch started: subscriptionId={}, configs={}", subscriptionId, configDataIds);
        return subscriptionId;
    }
    
    /**
     * 取消监听
     */
    public void unwatch(String subscriptionId) {
        ConfigSubscriptionContext context = subscriptions.remove(subscriptionId);
        if (context != null) {
            logger.info("Config watch cancelled: {}", subscriptionId);
        }
    }
    
    /**
     * 获取配置历史
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置标识，不能为空
     * @param limit 限制返回数量，如果 &lt;= 0 则使用默认值 100
     * @return 配置历史列表（领域对象），如果失败则返回空列表
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果获取失败
     */
    public List<ConfigHistory> getConfigHistory(String namespaceId, String groupName, 
                                                 String configDataId, int limit) {
        checkNotClosed();
        try {
            ConfigProto.GetConfigHistoryRequest request = ConfigProto.GetConfigHistoryRequest.newBuilder()
                    .setNamespaceId(namespaceId)
                    .setGroupName(groupName != null ? groupName : "DEFAULT_GROUP")
                    .setConfigDataId(configDataId)
                    .setLimit(limit > 0 ? limit : 100)
                    .build();
            
            // 在每次调用时动态设置 deadline
            ConfigProto.GetConfigHistoryResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .getConfigHistory(request);
            logger.info("getConfigHistory response: success={}, message={}, historyCount={}", 
                    response.getSuccess(), response.getMessage(), response.getHistoryCount());
            
            if (response.getSuccess()) {
                return ProtoConverter.toConfigHistoryList(response.getHistoryList());
            } else {
                logger.warn("getConfigHistory failed: {}", response.getMessage());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("getConfigHistory failed", e);
            throw new RuntimeException("Get config history failed", e);
        }
    }
    
    /**
     * 回滚配置
     * 
     * <p>将配置回滚到指定的历史版本。</p>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置标识，不能为空
     * @param targetVersion 目标版本号
     * @param changedBy 操作人，如果为 null 则使用 "system"
     * @param changeReason 回滚原因，如果为 null 则使用 "rollback"
     * @return 回滚配置结果（包含新版本号和 MD5）
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果回滚失败
     */
    public RollbackConfigResult rollbackConfig(String namespaceId, String groupName,
                                                String configDataId, long targetVersion,
                                                String changedBy, String changeReason) {
        checkNotClosed();
        try {
            ConfigProto.RollbackConfigRequest request = ConfigProto.RollbackConfigRequest.newBuilder()
                    .setNamespaceId(namespaceId)
                    .setGroupName(groupName != null ? groupName : "DEFAULT_GROUP")
                    .setConfigDataId(configDataId)
                    .setTargetVersion(targetVersion)
                    .setChangedBy(changedBy != null ? changedBy : "system")
                    .setChangeReason(changeReason != null ? changeReason : "rollback")
                    .build();
            
            // 在每次调用时动态设置 deadline
            ConfigProto.RollbackConfigResponse response = blockingStub
                    .withDeadlineAfter(connectionManager.getRequestTimeout(), TimeUnit.MILLISECONDS)
                    .rollbackConfig(request);
            logger.info("rollbackConfig response: success={}, message={}, newVersion={}, contentMd5={}", 
                    response.getSuccess(), response.getMessage(), response.getNewVersion(), response.getContentMd5());
            
            RollbackConfigResult result = ProtoConverter.toRollbackConfigResult(response);
            if (result.isSuccess()) {
                logger.info("Config rolled back: {}/{}/{}, targetVersion={}", 
                        namespaceId, groupName, configDataId, targetVersion);
            } else {
                logger.warn("Config rollback failed: {}", result.getMessage());
            }
            return result;
        } catch (Exception e) {
            logger.error("rollbackConfig failed", e);
            throw new RuntimeException("Rollback config failed", e);
        }
    }
    
    /**
     * 重连配置监听
     */
    private void reconnectWatch(String subscriptionId, String namespaceId, 
                               String groupName, List<String> configDataIds, 
                               ConfigChangeListener listener) {
        subscriptionExecutor.submit(() -> {
            int attempts = 0;
            long backoffMs = config.getReconnectInterval();
            final long maxBackoffMs = 30000;
            
            while (!closed.get() && (config.getMaxReconnectAttempts() < 0 || attempts < config.getMaxReconnectAttempts())) {
                try {
                    Thread.sleep(backoffMs);
                    attempts++;
                    backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
                    
                    logger.info("Reconnecting config watch: subscriptionId={}, attempt={}, backoffMs={}", 
                            subscriptionId, attempts, backoffMs);
                    
                    // 检查是否已有相同订阅
                    boolean hasDuplicate = false;
                    for (ConfigSubscriptionContext ctx : subscriptions.values()) {
                        if (ctx.namespaceId.equals(namespaceId) && 
                            ctx.groupName.equals(groupName) &&
                            ctx.configDataIds != null && ctx.configDataIds.equals(configDataIds)) {
                            logger.info("Duplicate config watch exists, skip reconnect: subscriptionId={}", ctx.subscriptionId);
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
                    
                    // 重新监听
                    String newSubscriptionId = watchConfig(namespaceId, groupName, configDataIds, listener);
                    logger.info("Config watch reconnected: oldSubscriptionId={}, newSubscriptionId={}, attempts={}", 
                            subscriptionId, newSubscriptionId, attempts);
                    
                    listener.onReconnected();
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Config watch reconnect interrupted: subscriptionId={}", subscriptionId);
                    return;
                } catch (Exception e) {
                    logger.warn("Config watch reconnect failed: subscriptionId={}, attempt={}", subscriptionId, attempts, e);
                }
            }
            
            if (!closed.get()) {
                logger.error("Config watch reconnect exhausted retries: subscriptionId={}, attempts={}", 
                        subscriptionId, attempts);
            }
        });
    }
    
    /**
     * 关闭管理器
     * 
     * <p>优雅关闭流程：</p>
     * <ol>
     *   <li>取消所有配置订阅</li>
     *   <li>清空本地缓存</li>
     * </ol>
     */
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }
        
        logger.info("Closing config center manager...");
        
        // 取消所有配置订阅
        List<String> subscriptionIds = new ArrayList<>(subscriptions.keySet());
        if (!subscriptionIds.isEmpty()) {
            logger.info("Cancelling {} config subscription(s)...", subscriptionIds.size());
            for (String subscriptionId : subscriptionIds) {
                try {
                    unwatch(subscriptionId);
                    logger.debug("Config subscription cancelled: {}", subscriptionId);
                } catch (Exception e) {
                    // 取消订阅失败不影响关闭流程，只记录警告
                    logger.warn("unwatch failed during close: subscriptionId={}, error={}", subscriptionId, e.getMessage());
                }
            }
            logger.info("Config subscription cancellation completed");
        }
        
        // 清空本地缓存
        subscriptions.clear();
        
        logger.info("Config center manager closed");
    }
    
    /**
     * 检查未关闭
     */
    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Config center manager is closed");
        }
        if (!connectionManager.isConnected()) {
            throw new IllegalStateException("Not connected; call connect() first");
        }
    }
    
    /**
     * 配置订阅上下文
     */
    @SuppressWarnings("unused")
    private static class ConfigSubscriptionContext {
        final String subscriptionId;
        final String namespaceId;
        final String groupName;
        final List<String> configDataIds;
        final ConfigChangeListener listener;
        final StreamObserver<?> responseObserver;
        
        ConfigSubscriptionContext(String subscriptionId, String namespaceId, String groupName,
                                 List<String> configDataIds, ConfigChangeListener listener,
                                 StreamObserver<?> responseObserver) {
            this.subscriptionId = subscriptionId;
            this.namespaceId = namespaceId;
            this.groupName = groupName;
            this.configDataIds = configDataIds;
            this.listener = listener;
            this.responseObserver = responseObserver;
        }
    }
}

