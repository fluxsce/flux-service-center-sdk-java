package com.flux.servicecenter.listener;

import com.flux.servicecenter.model.ServiceChangeEvent;
import com.flux.servicecenter.model.ServiceInfo;
import com.flux.servicecenter.model.NodeInfo;

import java.util.List;

/**
 * 服务变更监听器接口
 * 
 * <p>用于接收服务节点变更事件。使用领域对象而非 Proto 对象，实现业务层与 Proto 层的解耦。</p>
 * 
 * <p>使用方式：</p>
 * <ul>
 *   <li>实现接口：实现 {@link #onServiceChange(ServiceChangeEvent)} 方法处理所有事件</li>
 *   <li>使用适配器：继承 {@link ServiceChangeListenerAdapter}，只实现需要的方法</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 方式1：实现接口
 * ServiceChangeListener listener = event -> {
 *     System.out.println("事件类型: " + event.getEventType());
 *     System.out.println("服务: " + event.getService().getServiceName());
 * };
 * 
 * // 方式2：使用适配器
 * ServiceChangeListenerAdapter adapter = new ServiceChangeListenerAdapter() {
 *     @Override
 *     public void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
 *         System.out.println("节点添加: " + node.getIpAddress());
 *     }
 * };
 * }</pre>
 * 
 * @author shangjian
 */
public interface ServiceChangeListener {
    
    /**
     * 处理服务变更事件
     * 
     * <p>这是唯一必须实现的方法。所有服务变更事件都会通过此方法传递。</p>
     * 
     * <p>如果使用 {@link ServiceChangeListenerAdapter}，此方法会自动根据事件类型
     * 调用对应的具体方法（如 onNodeAdded、onNodeUpdated 等）。</p>
     * 
     * @param event 服务变更事件（领域对象，非 Proto 对象）
     */
    void onServiceChange(ServiceChangeEvent event);
    
    /**
     * 服务添加事件
     * 
     * <p>当新的服务被添加时触发。</p>
     * 
     * @param service 服务信息
     * @param allNodes 当前所有节点列表
     */
    default void onServiceAdded(ServiceInfo service, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 服务更新事件
     * 
     * <p>当服务信息发生更新时触发。</p>
     * 
     * @param service 服务信息
     * @param allNodes 当前所有节点列表
     */
    default void onServiceUpdated(ServiceInfo service, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 服务删除事件
     * 
     * <p>当服务被删除时触发。</p>
     * 
     * @param service 服务信息
     * @param allNodes 当前所有节点列表（删除前的节点列表）
     */
    default void onServiceDeleted(ServiceInfo service, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 节点添加事件
     * 
     * <p>当有新的节点添加到服务时触发。</p>
     * 
     * @param service 服务信息
     * @param node 新增的节点
     * @param allNodes 当前所有节点列表
     */
    default void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 节点更新事件
     * 
     * <p>当服务节点信息发生更新时触发。</p>
     * 
     * @param service 服务信息
     * @param node 更新的节点
     * @param allNodes 当前所有节点列表
     */
    default void onNodeUpdated(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 节点移除事件
     * 
     * <p>当节点从服务中移除时触发。</p>
     * 
     * @param service 服务信息
     * @param node 移除的节点
     * @param allNodes 当前所有节点列表
     */
    default void onNodeRemoved(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 订阅连接断开
     * 
     * <p>当订阅连接断开时触发，通常是由于网络问题或服务端关闭连接。</p>
     * 
     * @param cause 断开原因
     */
    default void onDisconnected(Throwable cause) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 订阅连接恢复
     * 
     * <p>当订阅连接重新建立时触发，通常在自动重连成功后调用。</p>
     */
    default void onReconnected() {
        // 默认空实现，子类可以覆盖
    }
}

