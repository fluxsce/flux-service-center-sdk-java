package com.flux.servicecenter.client.internal;

import com.flux.servicecenter.config.ConfigProto;
import com.flux.servicecenter.registry.RegistryProto;
import com.flux.servicecenter.stream.StreamProto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * 双向流业务操作助手
 * 
 * <p>封装双向流的业务操作，将复杂的消息构建和发送逻辑封装成简单的方法调用。</p>
 */
public class StreamBusinessHelper {
    private static final Logger logger = LoggerFactory.getLogger(StreamBusinessHelper.class);
    
    private final StreamConnectionManager connectionManager;
    
    public StreamBusinessHelper(StreamConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 检查服务端响应是否为错误
     */
    private boolean isErrorResponse(ServerMessage response) {
        return response.getMessageType() == ServerMessageType.SERVER_ERROR;
    }
    
    /**
     * 从错误响应中提取错误消息
     */
    private String getErrorMessage(ServerMessage response) {
        if (response.hasError()) {
            ErrorResponse error = response.getError();
            return String.format("[%s] %s", error.getCode(), error.getMessage());
        }
        return "未知错误";
    }
    
    // ========== 服务注册发现 ==========
    
    /**
     * 注册服务
     */
    public RegistryProto.RegisterServiceResponse registerService(RegistryProto.Service service) throws TimeoutException {
        ClientMessage request = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_REGISTER_SERVICE)
            .setRegisterService(service)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(request);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("注册服务失败: {}", errorMsg);
            return RegistryProto.RegisterServiceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getRegisterService();
    }
    
    /**
     * 注销服务
     */
    public RegistryProto.RegistryResponse unregisterService(RegistryProto.ServiceKey serviceKey) throws TimeoutException {
        ClientMessage request = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_UNREGISTER_SERVICE)
            .setUnregisterService(serviceKey)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(request);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("注销服务失败: {}", errorMsg);
            return RegistryProto.RegistryResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getUnregisterService();
    }
    
    /**
     * 注册节点
     */
    public RegistryProto.RegisterNodeResponse registerNode(RegistryProto.Node node) throws TimeoutException {
        ClientMessage request = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_REGISTER_NODE)
            .setRegisterNode(node)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(request);
        
        // 检查是否是错误响应
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("注册节点失败: {}", errorMsg);
            return RegistryProto.RegisterNodeResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getRegisterNode();
    }
    
    /**
     * 注销节点
     */
    public RegistryProto.RegistryResponse unregisterNode(RegistryProto.NodeKey nodeKey) throws TimeoutException {
        ClientMessage request = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_UNREGISTER_NODE)
            .setUnregisterNode(nodeKey)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(request);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("注销节点失败: {}", errorMsg);
            return RegistryProto.RegistryResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getUnregisterNode();
    }
    
    /**
     * 发现节点
     */
    public RegistryProto.DiscoverNodesResponse discoverNodes(RegistryProto.DiscoverNodesRequest request) throws TimeoutException {
        ClientMessage clientMessage = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_DISCOVER_NODES)
            .setDiscoverNodes(request)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(clientMessage);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("发现节点失败: {}", errorMsg);
            return RegistryProto.DiscoverNodesResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getDiscoverNodes();
    }
    
    /**
     * 发送心跳
     */
    public RegistryProto.RegistryResponse heartbeat(RegistryProto.HeartbeatRequest request) throws TimeoutException {
        ClientMessage clientMessage = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_HEARTBEAT)
            .setHeartbeat(request)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(clientMessage);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("发送心跳失败: {}", errorMsg);
            return RegistryProto.RegistryResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getHeartbeat();
    }
    
    /**
     * 订阅服务（发送订阅请求，不等待响应）
     */
    public void subscribeServices(RegistryProto.SubscribeServicesRequest request) {
        ClientMessage clientMessage = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_SUBSCRIBE_SERVICES)
            .setSubscribeServices(request)
            .build();
        
        connectionManager.sendRequestAsync(clientMessage);
        logger.info("已发送服务订阅请求: {}/{}/{}", 
            request.getNamespaceId(), request.getGroupName(), request.getServiceNamesList());
    }
    
    /**
     * 订阅命名空间（发送订阅请求，不等待响应）
     */
    public void subscribeNamespace(RegistryProto.SubscribeNamespaceRequest request) {
        ClientMessage clientMessage = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_SUBSCRIBE_NAMESPACE)
            .setSubscribeNamespace(request)
            .build();
        
        connectionManager.sendRequestAsync(clientMessage);
        logger.info("已发送命名空间订阅请求: {}/{}", 
            request.getNamespaceId(), request.getGroupName());
    }
    
    // ========== 配置中心 ==========
    
    /**
     * 获取配置
     */
    public ConfigProto.GetConfigResponse getConfig(ConfigProto.ConfigKey configKey) throws TimeoutException {
        ClientMessage request = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_GET_CONFIG)
            .setGetConfig(configKey)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(request);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("获取配置失败: {}", errorMsg);
            return ConfigProto.GetConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getGetConfig();
    }
    
    /**
     * 保存配置
     */
    public ConfigProto.SaveConfigResponse saveConfig(ConfigProto.ConfigData configData) throws TimeoutException {
        ClientMessage request = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_SAVE_CONFIG)
            .setSaveConfig(configData)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(request);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("保存配置失败: {}", errorMsg);
            return ConfigProto.SaveConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getSaveConfig();
    }
    
    /**
     * 删除配置
     */
    public ConfigProto.ConfigResponse deleteConfig(ConfigProto.ConfigKey configKey) throws TimeoutException {
        ClientMessage request = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_DELETE_CONFIG)
            .setDeleteConfig(configKey)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(request);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("删除配置失败: {}", errorMsg);
            return ConfigProto.ConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getDeleteConfig();
    }
    
    /**
     * 列出配置
     */
    public ConfigProto.ListConfigsResponse listConfigs(ConfigProto.ListConfigsRequest request) throws TimeoutException {
        ClientMessage clientMessage = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_LIST_CONFIGS)
            .setListConfigs(request)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(clientMessage);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("列出配置失败: {}", errorMsg);
            return ConfigProto.ListConfigsResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getListConfigs();
    }
    
    /**
     * 监听配置（发送监听请求，不等待响应）
     */
    public void watchConfig(ConfigProto.WatchConfigRequest request) {
        ClientMessage clientMessage = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_WATCH_CONFIG)
            .setWatchConfig(request)
            .build();
        
        connectionManager.sendRequestAsync(clientMessage);
        logger.info("已发送配置监听请求: {}/{}/{}", 
            request.getNamespaceId(), request.getGroupName(), request.getConfigDataIdsList());
    }
    
    /**
     * 获取配置历史
     */
    public ConfigProto.GetConfigHistoryResponse getConfigHistory(ConfigProto.GetConfigHistoryRequest request) throws TimeoutException {
        ClientMessage clientMessage = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_GET_CONFIG_HISTORY)
            .setGetConfigHistory(request)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(clientMessage);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("获取配置历史失败: {}", errorMsg);
            return ConfigProto.GetConfigHistoryResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getGetConfigHistory();
    }
    
    /**
     * 回滚配置
     */
    public ConfigProto.RollbackConfigResponse rollbackConfig(ConfigProto.RollbackConfigRequest request) throws TimeoutException {
        ClientMessage clientMessage = ClientMessage.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setMessageType(ClientMessageType.CLIENT_ROLLBACK_CONFIG)
            .setRollbackConfig(request)
            .build();
        
        ServerMessage response = connectionManager.sendRequest(clientMessage);
        
        if (isErrorResponse(response)) {
            String errorMsg = getErrorMessage(response);
            logger.error("回滚配置失败: {}", errorMsg);
            return ConfigProto.RollbackConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMsg)
                    .build();
        }
        
        return response.getRollbackConfig();
    }
}

