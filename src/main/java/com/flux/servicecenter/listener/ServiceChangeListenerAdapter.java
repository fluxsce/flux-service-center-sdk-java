package com.flux.servicecenter.listener;

import com.flux.servicecenter.model.ServiceChangeEvent;
import com.flux.servicecenter.model.ServiceInfo;
import com.flux.servicecenter.model.NodeInfo;

import java.util.List;

/**
 * 服务变更监听器适配器
 * 
 * <p>提供默认实现，简化监听器的使用。用户只需要实现需要的方法即可。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * ServiceChangeListenerAdapter listener = new ServiceChangeListenerAdapter() {
 *     @Override
 *     public void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
 *         System.out.println("节点添加: " + node.getIpAddress() + ":" + node.getPortNumber());
 *     }
 * };
 * }</pre>
 * 
 * @author shangjian
 */
public abstract class ServiceChangeListenerAdapter implements ServiceChangeListener {
    
    /**
     * 处理服务变更事件（默认实现）
     * 
     * <p>根据事件类型自动调用对应的处理方法。</p>
     * 
     * @param event 服务变更事件
     */
    @Override
    public void onServiceChange(ServiceChangeEvent event) {
        if (event == null) {
            return;
        }
        
        ServiceChangeEvent.EventType eventType = event.getEventType();
        ServiceInfo service = event.getService();
        List<NodeInfo> allNodes = event.getAllNodes();
        NodeInfo changedNode = event.getChangedNode();
        
        switch (eventType) {
            case SERVICE_ADDED:
                onServiceAdded(service, allNodes);
                break;
            case SERVICE_UPDATED:
                onServiceUpdated(service, allNodes);
                break;
            case SERVICE_DELETED:
                onServiceDeleted(service, allNodes);
                break;
            case NODE_ADDED:
                if (changedNode != null) {
                    onNodeAdded(service, changedNode, allNodes);
                }
                break;
            case NODE_UPDATED:
                if (changedNode != null) {
                    onNodeUpdated(service, changedNode, allNodes);
                }
                break;
            case NODE_REMOVED:
                if (changedNode != null) {
                    onNodeRemoved(service, changedNode, allNodes);
                }
                break;
        }
    }
    
    /**
     * 服务添加事件（默认空实现）
     */
    @Override
    public void onServiceAdded(ServiceInfo service, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 服务更新事件（默认空实现）
     */
    @Override
    public void onServiceUpdated(ServiceInfo service, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 服务删除事件（默认空实现）
     */
    @Override
    public void onServiceDeleted(ServiceInfo service, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 节点添加事件（默认空实现）
     */
    @Override
    public void onNodeAdded(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 节点更新事件（默认空实现）
     */
    @Override
    public void onNodeUpdated(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 节点移除事件（默认空实现）
     */
    @Override
    public void onNodeRemoved(ServiceInfo service, NodeInfo node, List<NodeInfo> allNodes) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 订阅连接断开（默认空实现）
     */
    @Override
    public void onDisconnected(Throwable cause) {
        // 默认空实现，子类可以覆盖
    }
    
    /**
     * 订阅连接恢复（默认空实现）
     */
    @Override
    public void onReconnected() {
        // 默认空实现，子类可以覆盖
    }
}

