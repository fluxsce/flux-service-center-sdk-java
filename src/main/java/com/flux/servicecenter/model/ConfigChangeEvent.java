package com.flux.servicecenter.model;

import java.util.Objects;

/**
 * 配置变更事件领域对象
 * 
 * <p>用于在业务层表示配置变更事件，与 Proto 对象解耦。</p>
 * 
 * @author shangjian
 */
public class ConfigChangeEvent {
    /**
     * 事件类型枚举
     */
    public enum EventType {
        /** 配置更新 */
        CONFIG_UPDATED,
        /** 配置删除 */
        CONFIG_DELETED
    }
    
    /** 事件类型，标识此次变更的具体类型 */
    private EventType eventType;
    
    /** 事件时间戳（字符串格式） */
    private String timestamp;
    
    /** 命名空间ID，用于配置隔离 */
    private String namespaceId;
    
    /** 分组名，用于配置分组管理，默认为 "DEFAULT_GROUP" */
    private String groupName;
    
    /** 配置标识，唯一标识一个配置 */
    private String configDataId;
    
    /** 配置数据（删除事件时可能为空） */
    private ConfigInfo config;
    
    /** 配置内容的 MD5 校验值 */
    private String contentMd5;
    
    public ConfigChangeEvent() {
    }
    
    public ConfigChangeEvent(EventType eventType, String namespaceId, String groupName, String configDataId) {
        this.eventType = eventType;
        this.namespaceId = namespaceId;
        this.groupName = groupName;
        this.configDataId = configDataId;
    }
    
    // Getters and Setters
    public EventType getEventType() {
        return eventType;
    }
    
    public void setEventType(EventType eventType) {
        this.eventType = eventType;
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
    
    public String getConfigDataId() {
        return configDataId;
    }
    
    public void setConfigDataId(String configDataId) {
        this.configDataId = configDataId;
    }
    
    public ConfigInfo getConfig() {
        return config;
    }
    
    public void setConfig(ConfigInfo config) {
        this.config = config;
    }
    
    public String getContentMd5() {
        return contentMd5;
    }
    
    public void setContentMd5(String contentMd5) {
        this.contentMd5 = contentMd5;
    }
    
    @Override
    public String toString() {
        return "ConfigChangeEvent{" +
                "eventType=" + eventType +
                ", timestamp='" + timestamp + '\'' +
                ", namespaceId='" + namespaceId + '\'' +
                ", groupName='" + groupName + '\'' +
                ", configDataId='" + configDataId + '\'' +
                ", contentMd5='" + contentMd5 + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigChangeEvent that = (ConfigChangeEvent) o;
        return eventType == that.eventType &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(namespaceId, that.namespaceId) &&
                Objects.equals(groupName, that.groupName) &&
                Objects.equals(configDataId, that.configDataId) &&
                Objects.equals(contentMd5, that.contentMd5);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventType, timestamp, namespaceId, groupName, configDataId, contentMd5);
    }
}

