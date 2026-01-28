package com.flux.servicecenter.model;

import java.util.Objects;

/**
 * 注册服务结果
 * 
 * <p>封装注册服务的响应信息，使用领域对象而非 Proto 对象。</p>
 * 
 * @author shangjian
 */
public class RegisterServiceResult {
    /** 是否成功 */
    private boolean success;
    
    /** 响应消息 */
    private String message;
    
    /** 业务错误码（可选） */
    private String code;
    
    /** 服务端生成的节点ID（如果注册时携带了 node，则返回生成的 nodeId；否则为空） */
    private String nodeId;
    
    public RegisterServiceResult() {
    }
    
    public RegisterServiceResult(boolean success, String message, String nodeId) {
        this.success = success;
        this.message = message;
        this.nodeId = nodeId;
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
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    @Override
    public String toString() {
        return "RegisterServiceResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", code='" + code + '\'' +
                ", nodeId='" + nodeId + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisterServiceResult that = (RegisterServiceResult) o;
        return success == that.success &&
                Objects.equals(message, that.message) &&
                Objects.equals(code, that.code) &&
                Objects.equals(nodeId, that.nodeId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, message, code, nodeId);
    }
}

