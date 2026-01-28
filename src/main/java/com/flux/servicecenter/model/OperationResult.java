package com.flux.servicecenter.model;

import java.util.Objects;

/**
 * 操作结果（通用响应）
 * 
 * <p>封装通用操作的响应信息，使用领域对象而非 Proto 对象。</p>
 * 
 * @author shangjian
 */
public class OperationResult {
    /** 是否成功 */
    private boolean success;
    
    /** 响应消息 */
    private String message;
    
    /** 业务错误码（可选） */
    private String code;
    
    public OperationResult() {
    }
    
    public OperationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public OperationResult(boolean success, String message, String code) {
        this.success = success;
        this.message = message;
        this.code = code;
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
    
    @Override
    public String toString() {
        return "OperationResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", code='" + code + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationResult that = (OperationResult) o;
        return success == that.success &&
                Objects.equals(message, that.message) &&
                Objects.equals(code, that.code);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, message, code);
    }
}

