package com.flux.servicecenter.client;

/**
 * Flux Service Center 客户端接口
 * 
 * <p>定义了服务中心客户端的所有核心功能，包括：</p>
 * <ul>
 *   <li><b>连接管理</b> - 建立连接、断开连接、健康检查</li>
 *   <li><b>服务注册发现</b> - 注册/注销服务节点、查询服务、订阅服务变更</li>
 *   <li><b>配置中心</b> - 增删改查配置、监听配置变更、配置历史、配置回滚</li>
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
 * // 2. 创建客户端
 * IServiceCenterClient client = new ServiceCenterClient(config);
 * client.connect();
 * 
 * // 3. 注册服务节点
 * ServiceInfo service = new ServiceInfo()
 *     .setServiceName("user-service")
 *     .setProtocolType("HTTP");
 * 
 * NodeInfo node = new NodeInfo()
 *     .setIpAddress("192.168.1.100")
 *     .setPortNumber(8080)
 *     .setWeight(100);
 * 
 * RegisterServiceResult result = client.registerService(service, node);
 * System.out.println("注册成功，nodeId: " + result.getNodeId());
 * 
 * // 4. 获取配置
 * GetConfigResult configResult = client.getConfig("my-namespace", "my-group", "app-config");
 * System.out.println("配置内容: " + configResult.getConfig().getConfigContent());
 * 
 * // 5. 关闭客户端
 * client.close();
 * }</pre>
 * 
 * <p><b>线程安全性：</b>此接口的实现类应该是线程安全的，可以在多线程环境中使用。</p>
 * 
 * <p><b>资源管理：</b>客户端使用完毕后必须调用 {@link #close()} 方法释放资源，建议使用 try-with-resources 语句。</p>
 * 
 * @author shangjian
 * @version 1.0.0
 * @see ServiceCenterClient
 * @see IRegistryService
 * @see IConfigService
 */
public interface IServiceCenterClient extends IRegistryService, IConfigService, AutoCloseable {
    
    // ========================================
    // 连接管理
    // ========================================
    
    /**
     * 连接到服务中心
     * 
     * <p>建立与服务中心的 gRPC 连接，初始化所有必要的通信通道。
     * 如果已经连接，则跳过重复连接。</p>
     * 
     * <p><b>注意事项：</b></p>
     * <ul>
     *   <li>此方法会阻塞直到连接建立成功或失败</li>
     *   <li>连接失败会抛出 RuntimeException</li>
     *   <li>客户端支持自动重连，无需手动处理连接断开</li>
     * </ul>
     * 
     * @throws RuntimeException 如果连接失败
     * @throws IllegalStateException 如果客户端已关闭
     */
    void connect();
    
    /**
     * 断开连接并释放所有资源
     * 
     * <p><b>优雅关闭流程（按顺序执行）：</b></p>
     * <ol>
     *   <li>注销所有已注册的服务节点</li>
     *   <li>停止所有心跳任务</li>
     *   <li>取消所有服务和配置订阅</li>
     *   <li>关闭 gRPC 连接</li>
     *   <li>关闭内部线程池</li>
     * </ol>
     * 
     * <p><b>注意事项：</b></p>
     * <ul>
     *   <li>此方法是幂等的，可以安全地多次调用</li>
     *   <li>关闭后的客户端无法重新使用，需要创建新实例</li>
     *   <li>建议使用 try-with-resources 自动关闭</li>
     * </ul>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * try (IServiceCenterClient client = new ServiceCenterClient(config)) {
     *     client.connect();
     *     // 使用客户端
     * } // 自动关闭
     * }</pre>
     */
    @Override
    void close();
    
    /**
     * 检查客户端是否已连接
     * 
     * @return 如果已连接返回 true，否则返回 false
     */
    boolean isConnected();
    
    // ========================================
    // 健康检查
    // ========================================
    
    /**
     * 检查服务中心连接健康状态
     * 
     * <p>通过发送健康检查请求到服务端，验证连接是否正常。
     * 此方法可用于应用自身的健康检查逻辑。</p>
     * 
     * @return 如果连接健康返回 true，否则返回 false
     */
    boolean checkHealth();
}

