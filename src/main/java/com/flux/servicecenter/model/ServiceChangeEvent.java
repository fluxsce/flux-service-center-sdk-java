package com.flux.servicecenter.model;

import java.util.List;

/**
 * 服务变更事件领域对象
 * 
 * <p>用于在业务层表示服务变更事件，与 Proto 对象解耦。</p>
 * 
 * @author shangjian
 */
public class ServiceChangeEvent {
    /**
     * 事件类型枚举
     */
    public enum EventType {
        /** 服务添加 */
        SERVICE_ADDED,
        /** 服务更新 */
        SERVICE_UPDATED,
        /** 服务删除 */
        SERVICE_DELETED,
        /** 节点添加 */
        NODE_ADDED,
        /** 节点更新 */
        NODE_UPDATED,
        /** 节点移除 */
        NODE_REMOVED
    }
    
    /** 事件类型，标识此次变更的具体类型 */
    private EventType eventType;
    
    /** 事件时间戳（字符串格式） */
    private String timestamp;
    
    /** 命名空间ID，用于服务隔离 */
    private String namespaceId;
    
    /** 分组名，用于服务分组管理，默认为 "DEFAULT_GROUP" */
    private String groupName;
    
    /** 服务名称，唯一标识一个服务 */
    private String serviceName;
    
    /** 服务信息，包含服务的基本信息（如命名空间、分组、服务名等） */
    private ServiceInfo service;
    
    /** 当前所有节点列表，包含服务变更后的完整节点列表 */
    private List<NodeInfo> allNodes;
    
    /** 变更的节点，标识此次变更涉及的具体节点（添加、更新或删除的节点） */
    private NodeInfo changedNode;
    
    public ServiceChangeEvent() {
    }
    
    public ServiceChangeEvent(EventType eventType, ServiceInfo service, List<NodeInfo> allNodes) {
        this.eventType = eventType;
        this.service = service;
        this.allNodes = allNodes;
    }
    
    public EventType getEventType() {
        return eventType;
    }
    
    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }
    
    public ServiceInfo getService() {
        return service;
    }
    
    public void setService(ServiceInfo service) {
        this.service = service;
    }
    
    public List<NodeInfo> getAllNodes() {
        return allNodes;
    }
    
    public void setAllNodes(List<NodeInfo> allNodes) {
        this.allNodes = allNodes;
    }
    
    public NodeInfo getChangedNode() {
        return changedNode;
    }
    
    public void setChangedNode(NodeInfo changedNode) {
        this.changedNode = changedNode;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}

