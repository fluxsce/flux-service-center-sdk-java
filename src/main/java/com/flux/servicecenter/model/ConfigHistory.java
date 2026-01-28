package com.flux.servicecenter.model;

import java.util.Objects;

/**
 * 配置历史领域对象
 * 
 * <p>用于在业务层表示配置历史信息，与 Proto 对象解耦。</p>
 * 
 * @author shangjian
 */
public class ConfigHistory {
    /** 配置历史ID */
    private long configHistoryId;
    
    /** 命名空间ID，用于配置隔离 */
    private String namespaceId;
    
    /** 分组名，用于配置分组管理，默认为 "DEFAULT_GROUP" */
    private String groupName;
    
    /** 配置标识，唯一标识一个配置 */
    private String configDataId;
    
    /** 配置内容类型，可选值：JSON、YAML、PROPERTIES、XML、TEXT */
    private String contentType;
    
    /** 配置内容 */
    private String configContent;
    
    /** 配置内容的 MD5 校验值 */
    private String contentMd5;
    
    /** 配置版本号 */
    private long configVersion;
    
    /** 变更类型，可选值：ADD（新增）、UPDATE（更新）、DELETE（删除） */
    private String changeType;
    
    /** 变更原因 */
    private String changeReason;
    
    /** 变更人 */
    private String changedBy;
    
    /** 变更时间 */
    private String changeTime;
    
    public ConfigHistory() {
    }
    
    // Getters and Setters
    public long getConfigHistoryId() {
        return configHistoryId;
    }
    
    public void setConfigHistoryId(long configHistoryId) {
        this.configHistoryId = configHistoryId;
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
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public String getConfigContent() {
        return configContent;
    }
    
    public void setConfigContent(String configContent) {
        this.configContent = configContent;
    }
    
    public String getContentMd5() {
        return contentMd5;
    }
    
    public void setContentMd5(String contentMd5) {
        this.contentMd5 = contentMd5;
    }
    
    public long getConfigVersion() {
        return configVersion;
    }
    
    public void setConfigVersion(long configVersion) {
        this.configVersion = configVersion;
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
    
    public String getChangeReason() {
        return changeReason;
    }
    
    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }
    
    public String getChangedBy() {
        return changedBy;
    }
    
    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }
    
    public String getChangeTime() {
        return changeTime;
    }
    
    public void setChangeTime(String changeTime) {
        this.changeTime = changeTime;
    }
    
    @Override
    public String toString() {
        return "ConfigHistory{" +
                "configHistoryId=" + configHistoryId +
                ", namespaceId='" + namespaceId + '\'' +
                ", groupName='" + groupName + '\'' +
                ", configDataId='" + configDataId + '\'' +
                ", configVersion=" + configVersion +
                ", changeType='" + changeType + '\'' +
                ", changedBy='" + changedBy + '\'' +
                ", changeTime='" + changeTime + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigHistory that = (ConfigHistory) o;
        return configHistoryId == that.configHistoryId &&
                configVersion == that.configVersion &&
                Objects.equals(namespaceId, that.namespaceId) &&
                Objects.equals(groupName, that.groupName) &&
                Objects.equals(configDataId, that.configDataId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(configHistoryId, namespaceId, groupName, configDataId, configVersion);
    }
}

