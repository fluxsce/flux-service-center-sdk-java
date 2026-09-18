package com.flux.servicecenter.model;

import java.util.List;
import java.util.Objects;

/**
 * 获取服务结果
 * 
 * <p>封装获取服务的响应信息，使用领域对象而非 Proto 对象。</p>
 * 
 * @author shangjian
 */
public class GetServiceResult {
    /** 是否成功 */
    private boolean success;
    
    /** 响应消息 */
    private String message;
    
    /** 服务信息 */
    private ServiceInfo service;
    
    /** 节点列表 */
    private List<NodeInfo> nodes;
    
    public GetServiceResult() {
    }
    
    public GetServiceResult(boolean success, String message, ServiceInfo service, List<NodeInfo> nodes) {
        this.success = success;
        this.message = message;
        this.service = service;
        this.nodes = nodes;
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
    
    public ServiceInfo getService() {
        return service;
    }
    
    public void setService(ServiceInfo service) {
        this.service = service;
    }
    
    public List<NodeInfo> getNodes() {
        return nodes;
    }

    /**
     * 当前节点里健康且未下线的子集，便于升级后的负载均衡调用。
     */
    public List<NodeInfo> getHealthyNodes() {
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream()
                .filter(GetServiceResult::isHealthy)
                .toList();
    }
    
    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

    private static boolean isHealthy(NodeInfo node) {
        if (node == null) {
            return false;
        }
        String healthy = node.getHealthyStatus();
        String status = node.getInstanceStatus();
        boolean healthyOk = healthy == null || healthy.isBlank() || "HEALTHY".equalsIgnoreCase(healthy);
        boolean up = status == null || status.isBlank() || "UP".equalsIgnoreCase(status);
        return healthyOk && up;
    }
    
    @Override
    public String toString() {
        return "GetServiceResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", service=" + service +
                ", nodes=" + nodes +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetServiceResult that = (GetServiceResult) o;
        return success == that.success &&
                Objects.equals(message, that.message) &&
                Objects.equals(service, that.service) &&
                Objects.equals(nodes, that.nodes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, message, service, nodes);
    }
}

