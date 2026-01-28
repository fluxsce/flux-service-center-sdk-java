package com.flux.servicecenter.model;

import java.util.Map;

/**
 * 节点信息领域对象
 * 
 * <p>用于在业务层表示服务节点信息，与 Proto 对象解耦。</p>
 * 
 * @author shangjian
 */
public class NodeInfo {
    /** 节点ID，由服务端自动生成，唯一标识一个节点 */
    private String nodeId;
    
    /** 命名空间ID，用于服务隔离 */
    private String namespaceId;
    
    /** 分组名，用于服务分组管理，默认为 "DEFAULT_GROUP" */
    private String groupName;
    
    /** 服务名称，唯一标识一个服务 */
    private String serviceName;
    
    /** 节点IP地址 */
    private String ipAddress;
    
    /** 节点端口号 */
    private int portNumber;
    
    /** 权重值（0.01-10000.00），用于负载均衡 */
    private double weight;
    
    /** 是否临时节点，"Y" 表示临时节点（服务下线后自动删除），"N" 表示持久节点 */
    private String ephemeral;
    
    /** 实例状态，可选值：UP（上线）、DOWN（下线）、STARTING（启动中）、OUT_OF_SERVICE（停用） */
    private String instanceStatus;
    
    /** 健康状态，可选值：HEALTHY（健康）、UNHEALTHY（不健康）、UNKNOWN（未知） */
    private String healthyStatus;
    
    /** 节点元数据，键值对形式，用于存储节点的扩展信息 */
    private Map<String, String> metadata;
    
    public NodeInfo() {
    }
    
    public NodeInfo(String ipAddress, int portNumber) {
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public int getPortNumber() {
        return portNumber;
    }
    
    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
    }
    
    public String getEphemeral() {
        return ephemeral;
    }
    
    public void setEphemeral(String ephemeral) {
        this.ephemeral = ephemeral;
    }
    
    public String getInstanceStatus() {
        return instanceStatus;
    }
    
    public void setInstanceStatus(String instanceStatus) {
        this.instanceStatus = instanceStatus;
    }
    
    public String getHealthyStatus() {
        return healthyStatus;
    }
    
    public void setHealthyStatus(String healthyStatus) {
        this.healthyStatus = healthyStatus;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
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
    
    public double getWeight() {
        return weight;
    }
    
    public void setWeight(double weight) {
        this.weight = weight;
    }
}

