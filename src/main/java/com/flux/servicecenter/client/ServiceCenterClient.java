package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.*;
import com.flux.servicecenter.client.internal.ConnectionManager;
import com.flux.servicecenter.client.internal.ServiceRegistryManager;
import com.flux.servicecenter.client.internal.ConfigCenterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Flux Service Center 统一客户端实现
 * 
 * <p>提供类似 Nacos 的统一客户端接口，包含服务注册发现和配置中心的所有功能。
 * 采用职责分离设计：连接管理、服务注册发现、配置中心分别由不同的管理器负责。</p>
 * 
 * <p><b>架构设计：</b></p>
 * <ul>
 *   <li>{@link ConnectionManager} - 负责连接管理、健康检查</li>
 *   <li>{@link ServiceRegistryManager} - 负责服务注册发现业务逻辑</li>
 *   <li>{@link ConfigCenterManager} - 负责配置中心业务逻辑</li>
 * </ul>
 * 
 * <p><b>快速开始示例：</b></p>
 * <pre>{@code
 * // 1. 创建配置
 * ServiceCenterConfig config = new ServiceCenterConfig()
 *     .setServerHost("localhost")
 *     .setServerPort(50051)
 *     .setNamespaceId("my-namespace")
 *     .setGroupName("my-group");
 * 
 * // 2. 创建客户端并连接
 * try (IServiceCenterClient client = new ServiceCenterClient(config)) {
 *     client.connect();
 *     
 *     // 3. 注册服务节点
 *     ServiceInfo service = new ServiceInfo()
 *         .setServiceName("user-service")
 *         .setProtocolType("HTTP");
 *     NodeInfo node = new NodeInfo()
 *         .setIpAddress("192.168.1.100")
 *         .setPortNumber(8080);
 *     RegisterServiceResult result = client.registerService(service, node);
 *     
 *     // 4. 获取配置
 *     GetConfigResult configResult = client.getConfig("my-namespace", "my-group", "app-config");
 *     String configContent = configResult.getConfig().getConfigContent();
 * } // 自动关闭并释放资源
 * }</pre>
 * 
 * <p><b>线程安全性：</b>此类是线程安全的，可以在多线程环境中使用。</p>
 * 
 * @author shangjian
 * @version 1.0.0
 * @see IServiceCenterClient
 * @see IRegistryService
 * @see IConfigService
 */
public class ServiceCenterClient implements IServiceCenterClient {
    private static final Logger logger = LoggerFactory.getLogger(ServiceCenterClient.class);
    
    // ========== 配置 ==========
    
    /** 客户端配置 */
    private final ServiceCenterConfig config;
    
    // ========== 管理器 ==========
    
    /** 连接管理器（负责连接管理、健康检查） */
    private final ConnectionManager connectionManager;
    
    /** 服务注册发现管理器（负责服务注册发现业务逻辑） */
    private final ServiceRegistryManager serviceRegistryManager;
    
    /** 配置中心管理器（负责配置中心业务逻辑） */
    private final ConfigCenterManager configCenterManager;
    
    // ========== 线程池 ==========
    
    /** 心跳执行器 */
    private final ScheduledExecutorService heartbeatExecutor;
    
    /** 订阅执行器（用于重连） */
    private final ExecutorService subscriptionExecutor;
    
    /** 关闭状态 */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // ========== 构造函数 ==========
    
    /**
     * 创建统一客户端实例
     * 
     * <p>初始化连接管理器、服务注册发现管理器和配置中心管理器。
     * 采用职责分离设计，提高代码的可维护性和可测试性。</p>
     * 
     * @param config 客户端配置，不能为 null
     * @throws IllegalArgumentException 如果配置无效
     */
    public ServiceCenterClient(ServiceCenterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为 null");
        }
        // 验证配置有效性
        validateConfig(config);
        this.config = config;
        
        // 初始化线程池（根据 CPU 核心数动态调整）
        int heartbeatThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.heartbeatExecutor = Executors.newScheduledThreadPool(
                heartbeatThreads,
                r -> {
                    Thread t = new Thread(r, "service-center-heartbeat-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                });
        
        // 订阅执行器使用固定大小的线程池，避免无限制创建线程
        this.subscriptionExecutor = new ThreadPoolExecutor(
                2, 10, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "service-center-subscription-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者运行
        );
        
        // 创建连接管理器
        this.connectionManager = new ConnectionManager(config, heartbeatExecutor);
        
        // 创建业务管理器
        this.serviceRegistryManager = new ServiceRegistryManager(
                config, connectionManager, heartbeatExecutor, subscriptionExecutor);
        this.configCenterManager = new ConfigCenterManager(
                config, connectionManager, subscriptionExecutor);
    }
    
    /**
     * 验证配置有效性
     * 
     * @param config 配置对象
     * @throws IllegalArgumentException 如果配置无效
     */
    private void validateConfig(ServiceCenterConfig config) {
        if (config.getServerHost() == null || config.getServerHost().trim().isEmpty()) {
            throw new IllegalArgumentException("服务器地址不能为空");
        }
        if (config.getServerPort() < 1 || config.getServerPort() > 65535) {
            throw new IllegalArgumentException("服务器端口必须在 1-65535 范围内");
        }
        if (config.getHeartbeatInterval() <= 0) {
            throw new IllegalArgumentException("心跳间隔必须大于 0");
        }
        if (config.getReconnectInterval() <= 0) {
            throw new IllegalArgumentException("重连间隔必须大于 0");
        }
        if (config.getRequestTimeout() <= 0) {
            throw new IllegalArgumentException("请求超时时间必须大于 0");
        }
    }
    
    // ========== 连接管理 ==========
    
    /**
     * 连接到服务中心
     * 
     * <p>建立 gRPC 连接，初始化服务注册发现和配置中心的 Stub。
     * 如果已经连接，则跳过重复连接。</p>
     * 
     * @throws RuntimeException 如果连接失败
     */
    public synchronized void connect() {
        // 连接管理器负责连接建立
        connectionManager.connect();
        
        // 连接成功后，初始化业务管理器的 Stub
        serviceRegistryManager.initializeStubs();
        configCenterManager.initializeStubs();
        
        logger.info("客户端已连接到服务中心: {}", config.getServerAddress());
    }
    
    /**
     * 断开连接并释放资源
     * 
     * <p>优雅关闭流程（按顺序执行）：</p>
     * <ol>
     *   <li><b>注销所有已注册的节点</b> - 向服务端发送注销请求，避免服务端认为节点仍在线</li>
     *   <li><b>停止所有心跳任务</b> - 停止节点心跳和连接健康检查</li>
     *   <li><b>取消所有服务订阅</b> - 关闭服务变更监听流</li>
     *   <li><b>取消所有配置订阅</b> - 关闭配置变更监听流</li>
     *   <li><b>关闭 gRPC 连接</b> - 断开与服务中心的连接</li>
     *   <li><b>关闭线程池</b> - 优雅关闭心跳和订阅线程池</li>
     * </ol>
     * 
     * <p>此方法是幂等的，可以安全地多次调用。关闭过程中的异常不会中断流程，只会记录警告日志。</p>
     * 
     * <p><b>重要提示：</b>客户端关闭后无法重新使用，如需重新连接请创建新的客户端实例。</p>
     */
    @Override
    public synchronized void close() {
        if (closed.getAndSet(true)) {
            return; // 已经关闭，避免重复关闭
        }
        
        logger.info("正在关闭客户端...");
        
        // 1. 关闭服务注册发现管理器（包含注销节点、停止心跳、取消服务订阅）
        try {
            serviceRegistryManager.close();
        } catch (Exception e) {
            logger.error("关闭服务注册发现管理器失败", e);
        }
        
        // 2. 关闭配置中心管理器（包含取消配置订阅）
        try {
            configCenterManager.close();
        } catch (Exception e) {
            logger.error("关闭配置中心管理器失败", e);
        }
        
        // 3. 关闭连接管理器（关闭 gRPC 连接）
        try {
            connectionManager.close();
        } catch (Exception e) {
            logger.error("关闭连接管理器失败", e);
        }
        
        // 4. 优雅关闭线程池
        shutdownExecutor(heartbeatExecutor, "心跳");
        shutdownExecutor(subscriptionExecutor, "订阅");
        
        logger.info("客户端已成功关闭");
    }
    
    /**
     * 优雅关闭执行器
     * 
     * @param executor 执行器
     * @param name 执行器名称（用于日志）
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("{} 执行器未在 5 秒内关闭，强制关闭", name);
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("{} 执行器强制关闭失败", name);
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // ========== 服务注册发现 API ==========
    
    /**
     * 注册服务（使用领域对象）
     * 
     * <p>如果提供了节点信息，则同时注册服务和节点。
     * 注册成功后，如果返回了 nodeId，会自动启动心跳任务。</p>
     * 
     * <p>注意：如果 serviceInfo 中的 namespaceId 或 groupName 为空，将使用配置中的默认值。</p>
     * 
     * @param serviceInfo 服务信息领域对象，不能为 null
     * @param nodeInfo 可选的节点信息领域对象，如果提供则同时注册节点
     * @return 注册服务结果（包含生成的 nodeId，如果注册了节点）
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果注册失败
     */
    @Override
    public RegisterServiceResult registerService(ServiceInfo serviceInfo, NodeInfo nodeInfo) {
        // 如果 serviceInfo 中的 namespaceId 或 groupName 为空，使用配置中的默认值
        if (serviceInfo.getNamespaceId() == null || serviceInfo.getNamespaceId().isEmpty()) {
            serviceInfo.setNamespaceId(config.getNamespaceId());
        }
        if (serviceInfo.getGroupName() == null || serviceInfo.getGroupName().isEmpty()) {
            serviceInfo.setGroupName(config.getGroupName());
        }
        // 如果 nodeInfo 不为空，也设置默认值
        if (nodeInfo != null) {
            if (nodeInfo.getNamespaceId() == null || nodeInfo.getNamespaceId().isEmpty()) {
                nodeInfo.setNamespaceId(config.getNamespaceId());
            }
            if (nodeInfo.getGroupName() == null || nodeInfo.getGroupName().isEmpty()) {
                nodeInfo.setGroupName(config.getGroupName());
            }
            // 如果 nodeInfo 的 serviceName 为空，使用 serviceInfo 的 serviceName
            if ((nodeInfo.getServiceName() == null || nodeInfo.getServiceName().isEmpty()) 
                    && serviceInfo.getServiceName() != null && !serviceInfo.getServiceName().isEmpty()) {
                nodeInfo.setServiceName(serviceInfo.getServiceName());
            }
        }
        return serviceRegistryManager.registerService(serviceInfo, nodeInfo);
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
    @Override
    public OperationResult unregisterService(String namespaceId, String groupName, 
                                             String serviceName, String nodeId) {
        return serviceRegistryManager.unregisterService(namespaceId, groupName, serviceName, nodeId);
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
    @Override
    public GetServiceResult getService(String namespaceId, String groupName, String serviceName) {
        return serviceRegistryManager.getService(namespaceId, groupName, serviceName);
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
    @Override
    public RegisterNodeResult registerNode(NodeInfo nodeInfo) {
        // 如果 nodeInfo 中的 namespaceId 或 groupName 为空，使用配置中的默认值
        if (nodeInfo.getNamespaceId() == null || nodeInfo.getNamespaceId().isEmpty()) {
            nodeInfo.setNamespaceId(config.getNamespaceId());
        }
        if (nodeInfo.getGroupName() == null || nodeInfo.getGroupName().isEmpty()) {
            nodeInfo.setGroupName(config.getGroupName());
        }
        return serviceRegistryManager.registerNode(nodeInfo);
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
        return serviceRegistryManager.unregisterNode(nodeId);
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
        return serviceRegistryManager.discoverNodes(namespaceId, groupName, serviceName, healthyOnly);
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
     * <p>使用示例：</p>
     * <pre>{@code
     * // 方式1：订阅指定服务
     * String subscriptionId = client.subscribe(
     *     "public", 
     *     "DEFAULT_GROUP", 
     *     Arrays.asList("order-service", "user-service"), 
     *     listener);
     * 
     * // 方式2：订阅整个命名空间/分组（serviceNames 为 null）
     * String subscriptionId = client.subscribe(
     *     "public", 
     *     "DEFAULT_GROUP", 
     *     null, 
     *     listener);
     * }</pre>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param serviceNames 服务名列表（可选）：
     *                     - 如果为 null 或空列表，则订阅整个命名空间/分组下的所有服务
     *                     - 如果指定了服务名列表，则只订阅指定的服务
     * @param listener 变更监听器，不能为 null。建议使用 {@link com.flux.servicecenter.listener.ServiceChangeListenerAdapter}
     * @return 订阅ID（用于取消订阅）
     * @throws IllegalStateException 如果客户端未连接
     */
    public String subscribe(String namespaceId, String groupName, 
                           List<String> serviceNames, ServiceChangeListener listener) {
        return serviceRegistryManager.subscribe(namespaceId, groupName, serviceNames, listener);
    }
    
    /**
     * 订阅单个服务变更（接口方法）
     * 
     * @param namespaceId 命名空间ID
     * @param groupName 分组名
     * @param serviceName 服务名
     * @param listener 监听器
     * @return 订阅ID
     */
    @Override
    public String subscribeService(String namespaceId, String groupName, 
                                   String serviceName, ServiceChangeListener listener) {
        return subscribe(namespaceId, groupName, Arrays.asList(serviceName), listener);
    }
    
    /**
     * 取消服务订阅（接口方法）
     * 
     * @param subscriptionId 订阅ID
     * @return 操作结果
     */
    @Override
    public OperationResult unsubscribe(String subscriptionId) {
        serviceRegistryManager.unsubscribe(subscriptionId);
        OperationResult result = new OperationResult();
        result.setSuccess(true);
        result.setMessage("取消订阅成功");
        return result;
    }
    
    /**
     * 取消服务订阅（内部方法）
     * 
     * @param subscriptionId 订阅ID
     */
    public void unsubscribeService(String subscriptionId) {
        serviceRegistryManager.unsubscribe(subscriptionId);
    }
    
    /**
     * 获取所有活跃的订阅ID列表（接口方法）
     * 
     * @return 订阅ID列表
     */
    @Override
    public List<String> getActiveSubscriptions() {
        // TODO: 实现获取活跃订阅列表的逻辑
        return new ArrayList<>();
    }
    
    /**
     * 发送心跳
     * 
     * <p>用于保持节点在线状态。通常不需要手动调用，注册节点后会自动启动心跳任务。</p>
     * 
     * @param nodeId 节点ID，不能为空
     * @return 操作结果
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果发送心跳失败
     */
    public OperationResult heartbeat(String nodeId) {
        return serviceRegistryManager.heartbeat(nodeId);
    }
    
    /**
     * 发送节点心跳（接口方法）
     * 
     * @param nodeId 节点ID，不能为空
     * @return 操作结果
     */
    @Override
    public OperationResult sendHeartbeat(String nodeId) {
        return heartbeat(nodeId);
    }
    
    /**
     * 获取所有已注册的节点ID（接口方法 - 返回List）
     * 
     * <p>返回当前客户端已注册的所有节点ID列表，用于健康检查上报。</p>
     * 
     * @return 节点ID列表（不可修改的副本）
     */
    @Override
    public List<String> getRegisteredNodeIds() {
        return new ArrayList<>(serviceRegistryManager.getRegisteredNodeIds());
    }
    
    /**
     * 获取所有已注册的节点ID集合（内部使用）
     * 
     * <p>返回当前客户端已注册的所有节点ID集合，用于健康检查上报。</p>
     * 
     * @return 节点ID集合（不可修改的副本）
     */
    public Set<String> getRegisteredNodeIdSet() {
        return serviceRegistryManager.getRegisteredNodeIds();
    }
    
    /**
     * 获取已注册的节点信息
     * 
     * @param nodeId 节点ID
     * @return 节点信息，如果不存在则返回 null
     */
    public NodeInfo getRegisteredNode(String nodeId) {
        return serviceRegistryManager.getRegisteredNode(nodeId);
    }
    
    /**
     * 获取所有已注册的节点信息
     * 
     * <p>返回当前客户端已注册的所有节点信息列表，用于健康检查上报。</p>
     * 
     * @return 节点信息列表（不可修改的副本）
     */
    public List<NodeInfo> getAllRegisteredNodes() {
        return serviceRegistryManager.getAllRegisteredNodes();
    }
    
    // ========== 配置中心 API ==========
    
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
    @Override
    public GetConfigResult getConfig(String namespaceId, String groupName, String configDataId) {
        return configCenterManager.getConfig(namespaceId, groupName, configDataId);
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
    @Override
    public SaveConfigResult saveConfig(ConfigInfo configInfo) {
        // 如果 configInfo 中的 namespaceId 或 groupName 为空，使用配置中的默认值
        if (configInfo.getNamespaceId() == null || configInfo.getNamespaceId().isEmpty()) {
            configInfo.setNamespaceId(config.getNamespaceId());
        }
        if (configInfo.getGroupName() == null || configInfo.getGroupName().isEmpty()) {
            configInfo.setGroupName(config.getGroupName());
        }
        return configCenterManager.saveConfig(configInfo);
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
    @Override
    public OperationResult deleteConfig(String namespaceId, String groupName, String configDataId) {
        return configCenterManager.deleteConfig(namespaceId, groupName, configDataId);
    }
    
    /**
     * 列出配置列表（接口方法 - 带分页）
     * 
     * @param namespaceId 命名空间ID
     * @param groupName 分组名
     * @param searchKey 搜索关键字
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 配置列表
     */
    @Override
    public List<ConfigInfo> listConfigs(String namespaceId, String groupName, 
                                       String searchKey, int pageNum, int pageSize) {
        // TODO: 实现带分页和搜索的配置列表查询
        logger.warn("listConfigs(带分页) 方法暂未实现，调用简化版本");
        return listConfigs(namespaceId, groupName);
    }
    
    /**
     * 列出配置列表（简化版本）
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @return 配置信息列表（领域对象），如果失败则返回空列表
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果列出失败
     */
    public List<ConfigInfo> listConfigs(String namespaceId, String groupName) {
        return configCenterManager.listConfigs(namespaceId, groupName);
    }
    
    /**
     * 监听配置变更（支持监听单个或多个配置）
     * 
     * <p>订阅成功后，当配置发生变更（更新、删除）时，会通过监听器实时推送。
     * 支持监听多个配置，所有配置共用同一个流，减少连接数。</p>
     * 
     * <p>订阅连接断开时会自动重连，重连间隔和最大重试次数由配置决定。</p>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataIds 配置标识列表（支持单个或多个），不能为空
     * @param listener 变更监听器，不能为 null
     * @return 订阅ID（用于取消订阅）
     * @throws IllegalStateException 如果客户端未连接
     */
    public String watchConfig(String namespaceId, String groupName, 
                              List<String> configDataIds, ConfigChangeListener listener) {
        return configCenterManager.watchConfig(namespaceId, groupName, configDataIds, listener);
    }
    
    /**
     * 监听单个配置变更（接口方法）
     * 
     * @param namespaceId 命名空间ID
     * @param groupName 分组名
     * @param configDataId 配置ID
     * @param listener 监听器
     * @return 监听ID
     */
    @Override
    public String watchConfig(String namespaceId, String groupName, 
                             String configDataId, ConfigChangeListener listener) {
        return watchConfig(namespaceId, groupName, Arrays.asList(configDataId), listener);
    }
    
    /**
     * 取消配置监听（接口方法）
     * 
     * @param watchId 监听ID
     * @return 操作结果
     */
    @Override
    public OperationResult unwatch(String watchId) {
        configCenterManager.unwatch(watchId);
        OperationResult result = new OperationResult();
        result.setSuccess(true);
        result.setMessage("取消监听成功");
        return result;
    }
    
    /**
     * 取消配置监听（内部方法）
     * 
     * @param subscriptionId 订阅ID
     */
    public void unwatchConfig(String subscriptionId) {
        configCenterManager.unwatch(subscriptionId);
    }
    
    /**
     * 获取所有活跃的配置监听ID列表（接口方法）
     * 
     * @return 监听ID列表
     */
    @Override
    public List<String> getActiveWatches() {
        // TODO: 实现获取活跃配置监听列表的逻辑
        return new ArrayList<>();
    }
    
    /**
     * 获取配置历史（接口方法 - 带分页）
     * 
     * @param namespaceId 命名空间ID
     * @param groupName 分组名
     * @param configDataId 配置ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 配置历史列表
     */
    @Override
    public List<ConfigHistory> getConfigHistory(String namespaceId, String groupName, 
                                               String configDataId, int pageNum, int pageSize) {
        // 使用 limit 参数调用简化版本
        return getConfigHistory(namespaceId, groupName, configDataId, pageSize);
    }
    
    /**
     * 获取配置历史（简化版本）
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置标识，不能为空
     * @param limit 限制返回数量，如果 <= 0 则使用默认值 100
     * @return 配置历史列表（领域对象），如果失败则返回空列表
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果获取失败
     */
    public List<ConfigHistory> getConfigHistory(String namespaceId, String groupName, 
                                                String configDataId, int limit) {
        return configCenterManager.getConfigHistory(namespaceId, groupName, configDataId, limit);
    }
    
    /**
     * 回滚配置（接口方法 - 使用 historyId）
     * 
     * @param namespaceId 命名空间ID
     * @param groupName 分组名
     * @param configDataId 配置ID
     * @param historyId 历史记录ID
     * @return 回滚结果
     */
    @Override
    public RollbackConfigResult rollbackConfig(String namespaceId, String groupName,
                                               String configDataId, String historyId) {
        // 将 historyId 转换为版本号（如果需要）
        // 这里简化处理，假设 historyId 可以直接作为版本号使用
        long targetVersion;
        try {
            targetVersion = Long.parseLong(historyId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的历史记录ID: " + historyId);
        }
        return rollbackConfig(namespaceId, groupName, configDataId, targetVersion, "system", "rollback");
    }
    
    /**
     * 回滚配置（内部方法 - 使用版本号）
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
        return configCenterManager.rollbackConfig(namespaceId, groupName, configDataId, targetVersion, 
                changedBy, changeReason);
    }
    
    // ========== 连接状态检查 ==========
    
    /**
     * 检查客户端是否已连接
     * 
     * @return true 如果已连接，false 否则
     */
    @Override
    public boolean isConnected() {
        return connectionManager.isConnected();
    }
    
    /**
     * 检查服务中心连接健康状态
     * 
     * <p>通过发送健康检查请求到服务端，验证连接是否正常。
     * 此方法可用于应用自身的健康检查逻辑。</p>
     * 
     * @return 如果连接健康返回 true，否则返回 false
     */
    @Override
    public boolean checkHealth() {
        try {
            // 检查连接状态
            if (!isConnected()) {
                return false;
            }
            
            // TODO: 实现真正的健康检查逻辑
            // 可以通过发送一个简单的心跳或查询请求来验证连接
            return true;
        } catch (Exception e) {
            logger.warn("健康检查失败", e);
            return false;
        }
    }
    
    // ========== 工具方法 ==========
    
    /**
     * 获取最后一次错误
     * 
     * @return 最后一次错误，如果没有则返回 null
     */
    public Throwable getLastError() {
        return connectionManager.getLastError();
    }
}
