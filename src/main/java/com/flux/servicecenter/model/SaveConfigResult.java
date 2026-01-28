package com.flux.servicecenter.model;

import java.util.Objects;

/**
 * 保存配置结果
 * 
 * <p>封装保存配置的响应信息，使用领域对象而非 Proto 对象。</p>
 * 
 * @author shangjian
 */
public class SaveConfigResult {
    /** 是否成功 */
    private boolean success;
    
    /** 响应消息 */
    private String message;
    
    /** 业务错误码（可选） */
    private String code;
    
    /** 新版本号 */
    private long version;
    
    /** 服务端计算的 MD5 */
    private String contentMd5;
    
    public SaveConfigResult() {
    }
    
    public SaveConfigResult(boolean success, String message, long version, String contentMd5) {
        this.success = success;
        this.message = message;
        this.version = version;
        this.contentMd5 = contentMd5;
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
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public long getVersion() {
        return version;
    }
    
    public void setVersion(long version) {
        this.version = version;
    }
    
    public String getContentMd5() {
        return contentMd5;
    }
    
    public void setContentMd5(String contentMd5) {
        this.contentMd5 = contentMd5;
    }
    
    @Override
    public String toString() {
        return "SaveConfigResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", code='" + code + '\'' +
                ", version=" + version +
                ", contentMd5='" + contentMd5 + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SaveConfigResult that = (SaveConfigResult) o;
        return success == that.success &&
                version == that.version &&
                Objects.equals(message, that.message) &&
                Objects.equals(code, that.code) &&
                Objects.equals(contentMd5, that.contentMd5);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, message, code, version, contentMd5);
    }
}

