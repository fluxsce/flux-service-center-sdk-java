package com.flux.servicecenter.model;

import java.util.Map;

/**
 * 服务信息领域对象
 * 
 * <p>用于在业务层表示服务信息，与 Proto 对象解耦。</p>
 * 
 * @author shangjian
 */
public class ServiceInfo {
    /** 命名空间ID，用于服务隔离 */
    private String namespaceId;
    
    /** 分组名，用于服务分组管理，默认为 "DEFAULT_GROUP" */
    private String groupName;
    
    /** 服务名称，唯一标识一个服务 */
    private String serviceName;
    
    /** 服务类型，可选值：INTERNAL（内部服务）、NACOS（Nacos服务）、CONSUL（Consul服务）等 */
    private String serviceType;
    
    /** 服务版本号 */
    private String serviceVersion;
    
    /** 服务描述 */
    private String serviceDescription;
    
    /** 保护阈值（0.0-1.0），用于服务降级保护 */
    private double protectThreshold;
    
    /** 服务元数据，键值对形式，用于存储服务的扩展信息 */
    private Map<String, String> metadata;
    
    /** 服务标签，键值对形式，用于服务分类和筛选 */
    private Map<String, String> tags;
    
    public ServiceInfo() {
    }
    
    public ServiceInfo(String namespaceId, String groupName, String serviceName) {
        this.namespaceId = namespaceId;
        this.groupName = groupName;
        this.serviceName = serviceName;
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
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public String getServiceType() {
        return serviceType;
    }
    
    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }
    
    public String getServiceVersion() {
        return serviceVersion;
    }
    
    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }
    
    public String getServiceDescription() {
        return serviceDescription;
    }
    
    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
    }
    
    public double getProtectThreshold() {
        return protectThreshold;
    }
    
    public void setProtectThreshold(double protectThreshold) {
        this.protectThreshold = protectThreshold;
    }
    
    public Map<String, String> getTags() {
        return tags;
    }
    
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}

