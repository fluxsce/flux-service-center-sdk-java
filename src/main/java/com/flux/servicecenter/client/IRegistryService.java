package com.flux.servicecenter.client;

import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.*;

import java.util.List;

/**
 * 服务注册发现接口
 * 
 * <p>提供类似 Nacos 的服务注册发现功能，包括：</p>
 * <ul>
 *   <li><b>服务管理</b> - 注册/注销服务、查询服务列表</li>
 *   <li><b>节点管理</b> - 注册/注销节点、节点心跳、查询节点列表</li>
 *   <li><b>服务发现</b> - 查询服务节点、获取健康节点</li>
 *   <li><b>服务订阅</b> - 监听服务变更事件（节点上下线、权重变更等）</li>
 * </ul>
 * 
 * <p><b>核心概念：</b></p>
 * <ul>
 *   <li><b>命名空间（Namespace）</b>：用于环境隔离（如：开发、测试、生产）</li>
 *   <li><b>分组（Group）</b>：用于服务分类（如：DEFAULT_GROUP、ORDER_GROUP）</li>
 *   <li><b>服务名（ServiceName）</b>：服务的唯一标识</li>
 *   <li><b>节点（Node）</b>：服务的实例，包含 IP、端口、权重等信息</li>
 * </ul>
 * 
 * <p><b>服务注册示例：</b></p>
 * <pre>{@code
 * // 方式1：同时注册服务和节点（推荐）
 * ServiceInfo service = new ServiceInfo()
 *     .setServiceName("user-service")
 *     .setProtocolType("HTTP")
 *     .addMetadata("version", "1.0.0")
 *     .addMetadata("region", "cn-hangzhou");
 * 
 * NodeInfo node = new NodeInfo()
 *     .setIpAddress("192.168.1.100")
 *     .setPortNumber(8080)
 *     .setWeight(100)
 *     .setHealthyStatus("HEALTHY")
 *     .addMetadata("zone", "zone-a");
 * 
 * RegisterServiceResult result = client.registerService(service, node);
 * String nodeId = result.getNodeId(); // 保存 nodeId，用于后续注销
 * 
 * // 方式2：先注册服务，再注册节点
 * RegisterServiceResult serviceResult = client.registerService(service, null);
 * RegisterNodeResult nodeResult = client.registerNode(node);
 * }</pre>
 * 
 * <p><b>服务发现示例：</b></p>
 * <pre>{@code
 * // 获取服务的所有节点
 * GetServiceResult result = client.getService("my-namespace", "DEFAULT_GROUP", "user-service");
 * List<NodeInfo> allNodes = result.getNodes();
 * 
 * // 只获取健康的节点
 * List<NodeInfo> healthyNodes = result.getHealthyNodes();
 * 
 * // 从健康节点中选择一个（负载均衡）
 * NodeInfo selectedNode = selectNodeByWeight(healthyNodes);
 * }</pre>
 * 
 * <p><b>服务订阅示例：</b></p>
 * <pre>{@code
 * // 订阅服务变更
 * String subscriptionId = client.subscribeService(
 *     "my-namespace", 
 *     "DEFAULT_GROUP", 
 *     "user-service",
 *     event -> {
 *         System.out.println("服务发生变更: " + event.getEventType());
 *         List<NodeInfo> updatedNodes = event.getNodes();
 *         // 更新本地缓存的服务节点列表
 *         updateLocalCache(updatedNodes);
 *     }
 * );
 * 
 * // 取消订阅
 * client.unsubscribe(subscriptionId);
 * }</pre>
 * 
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>注册节点后会自动启动心跳，保持节点活跃状态</li>
 *   <li>客户端关闭时会自动注销所有已注册的节点</li>
 *   <li>服务订阅支持断线重连，自动恢复订阅状态</li>
 *   <li>所有方法都是线程安全的</li>
 * </ul>
 * 
 * @author shangjian
 * @version 1.0.0
 * @see ServiceInfo
 * @see NodeInfo
 * @see RegisterServiceResult
 * @see GetServiceResult
 */
public interface IRegistryService {
    
    // ========================================
    // 服务注册
    // ========================================
    
    /**
     * 注册服务（可选同时注册节点）
     * 
     * <p>这是最常用的注册方法，支持一次性注册服务和节点。
     * 如果 nodeInfo 不为 null，则同时注册服务和节点，并自动启动心跳。</p>
     * 
     * <p><b>参数说明：</b></p>
     * <ul>
     *   <li><b>serviceInfo</b>：服务信息，必填字段包括 serviceName 和 protocolType</li>
     *   <li><b>nodeInfo</b>：节点信息，必填字段包括 ipAddress 和 portNumber</li>
     * </ul>
     * 
     * <p><b>自动填充：</b></p>
     * <ul>
     *   <li>如果 namespaceId 为空，使用配置中的默认值</li>
     *   <li>如果 groupName 为空，使用配置中的默认值（通常是 "DEFAULT_GROUP"）</li>
     *   <li>如果 nodeInfo.serviceName 为空，自动填充为 serviceInfo.serviceName</li>
     * </ul>
     * 
     * @param serviceInfo 服务信息，不能为 null
     * @param nodeInfo 节点信息，可以为 null（仅注册服务不注册节点）
     * @return 注册结果，包含是否成功、消息、nodeId（如果注册了节点）
     * @throws IllegalArgumentException 如果必填字段为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果注册失败
     */
    RegisterServiceResult registerService(ServiceInfo serviceInfo, NodeInfo nodeInfo);
    
    /**
     * 注销服务或节点
     * 
     * <p>根据 nodeId 参数决定注销范围：</p>
     * <ul>
     *   <li>如果 nodeId 不为 null：只注销指定节点</li>
     *   <li>如果 nodeId 为 null：注销整个服务及其所有节点</li>
     * </ul>
     * 
     * <p><b>注意事项：</b></p>
     * <ul>
     *   <li>注销节点后会自动停止该节点的心跳任务</li>
     *   <li>注销服务会停止所有节点的心跳任务</li>
     *   <li>客户端关闭时会自动注销所有节点，无需手动调用</li>
     * </ul>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param serviceName 服务名，不能为空
     * @param nodeId 节点ID，如果为 null 则注销整个服务
     * @return 操作结果，包含是否成功和消息
     * @throws IllegalArgumentException 如果必填参数为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果注销失败
     */
    OperationResult unregisterService(String namespaceId, String groupName, 
                                      String serviceName, String nodeId);
    
    // ========================================
    // 节点注册
    // ========================================
    
    /**
     * 注册服务节点
     * 
     * <p>将一个服务实例（节点）注册到指定的服务下。
     * 注册成功后会自动启动心跳任务，保持节点活跃状态。</p>
     * 
     * <p><b>必填字段：</b></p>
     * <ul>
     *   <li>serviceName - 服务名称</li>
     *   <li>ipAddress - 节点IP地址</li>
     *   <li>portNumber - 节点端口号</li>
     * </ul>
     * 
     * <p><b>可选字段：</b></p>
     * <ul>
     *   <li>weight - 权重（默认100），用于负载均衡</li>
     *   <li>healthyStatus - 健康状态（默认 HEALTHY）</li>
     *   <li>instanceStatus - 实例状态（默认 UP）</li>
     *   <li>metadata - 自定义元数据</li>
     * </ul>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * NodeInfo node = new NodeInfo()
     *     .setServiceName("user-service")
     *     .setIpAddress("192.168.1.100")
     *     .setPortNumber(8080)
     *     .setWeight(100)
     *     .addMetadata("zone", "zone-a")
     *     .addMetadata("version", "1.0.0");
     * 
     * RegisterNodeResult result = client.registerNode(node);
     * String nodeId = result.getNodeId(); // 保存用于后续注销
     * }</pre>
     * 
     * @param nodeInfo 节点信息，不能为 null
     * @return 注册结果，包含生成的 nodeId
     * @throws IllegalArgumentException 如果必填字段为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果注册失败
     */
    RegisterNodeResult registerNode(NodeInfo nodeInfo);
    
    /**
     * 注销服务节点
     * 
     * <p>从服务中心注销指定的节点，自动停止该节点的心跳任务。</p>
     * 
     * @param nodeId 节点ID（注册节点时返回），不能为空
     * @return 操作结果
     * @throws IllegalArgumentException 如果 nodeId 为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果注销失败
     */
    OperationResult unregisterNode(String nodeId);
    
    // ========================================
    // 服务发现
    // ========================================
    
    /**
     * 获取服务信息（包含所有节点列表）
     * 
     * <p>查询指定服务的详细信息，包括服务元数据和所有节点列表。
     * 结果包含所有节点（健康和不健康），可通过 {@link GetServiceResult#getHealthyNodes()} 过滤健康节点。</p>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * GetServiceResult result = client.getService("my-namespace", "DEFAULT_GROUP", "user-service");
     * 
     * if (result.isSuccess()) {
     *     ServiceInfo service = result.getService();
     *     List<NodeInfo> allNodes = result.getNodes();
     *     List<NodeInfo> healthyNodes = result.getHealthyNodes();
     *     
     *     // 从健康节点中选择一个
     *     NodeInfo node = selectByLoadBalance(healthyNodes);
     * }
     * }</pre>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param serviceName 服务名，不能为空
     * @return 获取服务结果，包含服务信息和节点列表
     * @throws IllegalArgumentException 如果必填参数为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果查询失败
     */
    GetServiceResult getService(String namespaceId, String groupName, String serviceName);
    
    /**
     * 发送节点心跳
     * 
     * <p><b>注意：</b>通常不需要手动调用此方法，注册节点后会自动启动心跳任务。
     * 此方法主要用于测试或特殊场景。</p>
     * 
     * @param nodeId 节点ID，不能为空
     * @return 操作结果
     * @throws IllegalArgumentException 如果 nodeId 为空
     * @throws IllegalStateException 如果客户端未连接
     */
    OperationResult sendHeartbeat(String nodeId);
    
    // ========================================
    // 服务订阅
    // ========================================
    
    /**
     * 订阅服务变更事件
     * 
     * <p>监听指定服务的变更事件，包括：</p>
     * <ul>
     *   <li>节点上线</li>
     *   <li>节点下线</li>
     *   <li>节点权重变更</li>
     *   <li>节点健康状态变更</li>
     *   <li>节点元数据变更</li>
     * </ul>
     * 
     * <p><b>使用场景：</b></p>
     * <ul>
     *   <li>客户端负载均衡：实时更新服务节点列表</li>
     *   <li>服务监控：监控服务节点数量和健康状态</li>
     *   <li>动态配置：根据服务变更调整路由策略</li>
     * </ul>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * String subscriptionId = client.subscribeService(
     *     "my-namespace",
     *     "DEFAULT_GROUP",
     *     "user-service",
     *     new ServiceChangeListener() {
     *         @Override
     *         public void onServiceChange(ServiceChangeEvent event) {
     *             System.out.println("服务变更: " + event.getEventType());
     *             
     *             // 更新本地缓存的节点列表
     *             List<NodeInfo> nodes = event.getNodes();
     *             updateLocalCache(nodes);
     *             
     *             // 重新计算负载均衡权重
     *             recalculateWeights(nodes);
     *         }
     *     }
     * );
     * 
     * // 不需要时取消订阅
     * client.unsubscribe(subscriptionId);
     * }</pre>
     * 
     * <p><b>注意事项：</b></p>
     * <ul>
     *   <li>监听器在独立的线程中执行，不会阻塞主流程</li>
     *   <li>支持断线重连，自动恢复订阅状态</li>
     *   <li>客户端关闭时会自动取消所有订阅</li>
     * </ul>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param serviceName 服务名，不能为空
     * @param listener 变更事件监听器，不能为 null
     * @return 订阅ID，用于后续取消订阅
     * @throws IllegalArgumentException 如果必填参数为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果订阅失败
     */
    String subscribeService(String namespaceId, String groupName, 
                           String serviceName, ServiceChangeListener listener);
    
    /**
     * 取消服务订阅
     * 
     * <p>停止监听指定服务的变更事件，关闭对应的订阅流。</p>
     * 
     * @param subscriptionId 订阅ID（由 subscribeService 返回）
     * @return 操作结果
     * @throws IllegalArgumentException 如果 subscriptionId 为空
     * @throws IllegalStateException 如果客户端未连接
     */
    OperationResult unsubscribe(String subscriptionId);
    
    // ========================================
    // 工具方法
    // ========================================
    
    /**
     * 获取所有已注册的节点ID列表
     * 
     * <p>返回当前客户端已注册的所有节点ID，可用于监控和管理。</p>
     * 
     * @return 节点ID列表（只读）
     */
    List<String> getRegisteredNodeIds();
    
    /**
     * 获取所有活跃的订阅ID列表
     * 
     * <p>返回当前客户端的所有活跃订阅，可用于监控和管理。</p>
     * 
     * @return 订阅ID列表（只读）
     */
    List<String> getActiveSubscriptions();
}

