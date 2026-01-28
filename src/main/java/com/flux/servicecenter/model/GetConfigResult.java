package com.flux.servicecenter.model;

import java.util.Objects;

/**
 * 获取配置结果
 * 
 * <p>封装获取配置的响应信息，使用领域对象而非 Proto 对象。</p>
 * 
 * @author shangjian
 */
public class GetConfigResult {
    /** 是否成功 */
    private boolean success;
    
    /** 响应消息 */
    private String message;
    
    /** 配置信息（如果成功） */
    private ConfigInfo config;
    
    public GetConfigResult() {
    }
    
    public GetConfigResult(boolean success, String message, ConfigInfo config) {
        this.success = success;
        this.message = message;
        this.config = config;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public ConfigInfo getConfig() {
        return config;
    }
    
    public void setConfig(ConfigInfo config) {
        this.config = config;
    }
    
    @Override
    public String toString() {
        return "GetConfigResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", config=" + config +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetConfigResult that = (GetConfigResult) o;
        return success == that.success &&
                Objects.equals(message, that.message) &&
                Objects.equals(config, that.config);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, message, config);
    }
}

