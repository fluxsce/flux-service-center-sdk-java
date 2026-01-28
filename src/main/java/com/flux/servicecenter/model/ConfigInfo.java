package com.flux.servicecenter.model;

import java.util.Objects;

/**
 * 配置信息领域对象
 * 
 * <p>用于在业务层表示配置信息，与 Proto 对象解耦。</p>
 * 
 * @author shangjian
 */
public class ConfigInfo {
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
    
    /** 配置描述 */
    private String configDesc;
    
    /** 配置版本号，由服务端自动生成 */
    private long configVersion;
    
    /** 变更类型，可选值：ADD（新增）、UPDATE（更新）、DELETE（删除） */
    private String changeType;
    
    /** 变更原因 */
    private String changeReason;
    
    /** 变更人 */
    private String changedBy;
    
    public ConfigInfo() {
    }
    
    public ConfigInfo(String namespaceId, String groupName, String configDataId) {
        this.namespaceId = namespaceId;
        this.groupName = groupName;
        this.configDataId = configDataId;
    }
    
    // Getters and Setters
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
    
    public String getConfigDesc() {
        return configDesc;
    }
    
    public void setConfigDesc(String configDesc) {
        this.configDesc = configDesc;
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
    
    @Override
    public String toString() {
        return "ConfigInfo{" +
                "namespaceId='" + namespaceId + '\'' +
                ", groupName='" + groupName + '\'' +
                ", configDataId='" + configDataId + '\'' +
                ", contentType='" + contentType + '\'' +
                ", configVersion=" + configVersion +
                ", contentMd5='" + contentMd5 + '\'' +
                ", changeType='" + changeType + '\'' +
                ", changedBy='" + changedBy + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigInfo that = (ConfigInfo) o;
        return configVersion == that.configVersion &&
                Objects.equals(namespaceId, that.namespaceId) &&
                Objects.equals(groupName, that.groupName) &&
                Objects.equals(configDataId, that.configDataId) &&
                Objects.equals(contentType, that.contentType) &&
                Objects.equals(configContent, that.configContent) &&
                Objects.equals(contentMd5, that.contentMd5);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespaceId, groupName, configDataId, contentType, configContent, contentMd5, configVersion);
    }
}

