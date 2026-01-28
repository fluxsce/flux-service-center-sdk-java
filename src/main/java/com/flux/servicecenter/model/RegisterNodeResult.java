package com.flux.servicecenter.model;

import java.util.Objects;

/**
 * 注册节点结果
 * 
 * <p>封装注册节点的响应信息，使用领域对象而非 Proto 对象。</p>
 * 
 * @author shangjian
 */
public class RegisterNodeResult {
    /** 是否成功 */
    private boolean success;
    
    /** 响应消息 */
    private String message;
    
    /** 服务端生成的节点ID */
    private String nodeId;
    
    public RegisterNodeResult() {
    }
    
    public RegisterNodeResult(boolean success, String message, String nodeId) {
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
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    @Override
    public String toString() {
        return "RegisterNodeResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", nodeId='" + nodeId + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisterNodeResult that = (RegisterNodeResult) o;
        return success == that.success &&
                Objects.equals(message, that.message) &&
                Objects.equals(nodeId, that.nodeId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, message, nodeId);
    }
}

