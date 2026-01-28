package com.flux.servicecenter.model;

import com.flux.servicecenter.config.ConfigProto;
import com.flux.servicecenter.registry.RegistryProto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Proto 对象与领域对象转换器
 * 
 * <p>负责将 Proto 对象转换为领域对象，实现业务层与 Proto 层的解耦。
 * 转换器位于 model 层，与领域对象放在一起，便于维护和管理。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 将 Proto Service 转换为 ServiceInfo
 * RegistryProto.Service protoService = ...;
 * ServiceInfo serviceInfo = ProtoConverter.toServiceInfo(protoService);
 * 
 * // 将 Proto Node 转换为 NodeInfo
 * RegistryProto.Node protoNode = ...;
 * NodeInfo nodeInfo = ProtoConverter.toNodeInfo(protoNode);
 * 
 * // 将 Proto ServiceChangeEvent 转换为 ServiceChangeEvent
 * RegistryProto.ServiceChangeEvent protoEvent = ...;
 * ServiceChangeEvent event = ProtoConverter.toServiceChangeEvent(protoEvent);
 * 
 * // 将 Proto ConfigData 转换为 ConfigInfo
 * ConfigProto.ConfigData protoConfig = ...;
 * ConfigInfo configInfo = ProtoConverter.toConfigInfo(protoConfig);
 * 
 * // 将 Proto ConfigChangeEvent 转换为 ConfigChangeEvent
 * ConfigProto.ConfigChangeEvent protoEvent = ...;
 * ConfigChangeEvent event = ProtoConverter.toConfigChangeEvent(protoEvent);
 * }</pre>
 * 
 * @author shangjian
 */
public class ProtoConverter {
    
    /**
     * 将 Proto Service 转换为 ServiceInfo
     * 
     * @param proto Proto Service 对象，如果为 null 则返回 null
     * @return ServiceInfo 领域对象
     */
    public static ServiceInfo toServiceInfo(RegistryProto.Service proto) {
        if (proto == null) {
            return null;
        }
        
        ServiceInfo info = new ServiceInfo();
        info.setNamespaceId(proto.getNamespaceId());
        info.setGroupName(proto.getGroupName());
        info.setServiceName(proto.getServiceName());
        info.setServiceType(proto.getServiceType());
        info.setServiceVersion(proto.getServiceVersion());
        info.setServiceDescription(proto.getServiceDescription());
        info.setProtectThreshold(proto.getProtectThreshold());
        
        // 转换元数据（如果存在）
        if (proto.getMetadataCount() > 0) {
            Map<String, String> metadata = new HashMap<>();
            proto.getMetadataMap().forEach(metadata::put);
            info.setMetadata(metadata);
        } else {
            info.setMetadata(new HashMap<>());
        }
        
        // 转换标签（如果存在）
        if (proto.getTagsCount() > 0) {
            Map<String, String> tags = new HashMap<>();
            proto.getTagsMap().forEach(tags::put);
            info.setTags(tags);
        } else {
            info.setTags(new HashMap<>());
        }
        
        return info;
    }
    
    /**
     * 将 Proto Node 转换为 NodeInfo
     * 
     * @param proto Proto Node 对象，如果为 null 则返回 null
     * @return NodeInfo 领域对象
     */
    public static NodeInfo toNodeInfo(RegistryProto.Node proto) {
        if (proto == null) {
            return null;
        }
        
        NodeInfo info = new NodeInfo();
        info.setNodeId(proto.getNodeId());
        info.setNamespaceId(proto.getNamespaceId());
        info.setGroupName(proto.getGroupName());
        info.setServiceName(proto.getServiceName());
        info.setIpAddress(proto.getIpAddress());
        info.setPortNumber(proto.getPortNumber());
        info.setWeight(proto.getWeight());
        info.setEphemeral(proto.getEphemeral());
        info.setInstanceStatus(proto.getInstanceStatus());
        info.setHealthyStatus(proto.getHealthyStatus());
        
        // 转换元数据（如果存在）
        if (proto.getMetadataCount() > 0) {
            Map<String, String> metadata = new HashMap<>();
            proto.getMetadataMap().forEach(metadata::put);
            info.setMetadata(metadata);
        } else {
            info.setMetadata(new HashMap<>());
        }
        
        return info;
    }
    
    /**
     * 将 Proto Node 列表转换为 NodeInfo 列表
     * 
     * @param protoList Proto Node 列表，如果为 null 或空则返回空列表
     * @return NodeInfo 列表
     */
    public static List<NodeInfo> toNodeInfoList(List<RegistryProto.Node> protoList) {
        if (protoList == null || protoList.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<NodeInfo> result = new ArrayList<>(protoList.size());
        for (RegistryProto.Node proto : protoList) {
            NodeInfo info = toNodeInfo(proto);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }
    
    /**
     * 将 Proto ServiceChangeEvent 转换为 ServiceChangeEvent
     * 
     * <p>自动处理事件类型的转换，如果事件类型不匹配，会尝试映射或使用默认值。</p>
     * 
     * @param proto Proto ServiceChangeEvent 对象，如果为 null 则返回 null
     * @return ServiceChangeEvent 领域对象
     */
    public static ServiceChangeEvent toServiceChangeEvent(RegistryProto.ServiceChangeEvent proto) {
        if (proto == null) {
            return null;
        }
        
        ServiceChangeEvent event = new ServiceChangeEvent();
        
        // 转换时间戳和服务标识（先设置这些基础字段）
        event.setTimestamp(proto.getTimestamp());
        event.setNamespaceId(proto.getNamespaceId());
        event.setGroupName(proto.getGroupName());
        event.setServiceName(proto.getServiceName());
        
        // 转换事件类型
        String eventTypeStr = proto.getEventType();
        if (eventTypeStr != null && !eventTypeStr.isEmpty()) {
            try {
                event.setEventType(ServiceChangeEvent.EventType.valueOf(eventTypeStr));
            } catch (IllegalArgumentException e) {
                // 如果事件类型不匹配，尝试映射
                switch (eventTypeStr) {
                    case "SERVICE_ADDED":
                        event.setEventType(ServiceChangeEvent.EventType.SERVICE_ADDED);
                        break;
                    case "SERVICE_UPDATED":
                        event.setEventType(ServiceChangeEvent.EventType.SERVICE_UPDATED);
                        break;
                    case "SERVICE_DELETED":
                        event.setEventType(ServiceChangeEvent.EventType.SERVICE_DELETED);
                        break;
                    case "NODE_ADDED":
                        event.setEventType(ServiceChangeEvent.EventType.NODE_ADDED);
                        break;
                    case "NODE_UPDATED":
                        event.setEventType(ServiceChangeEvent.EventType.NODE_UPDATED);
                        break;
                    case "NODE_REMOVED":
                        event.setEventType(ServiceChangeEvent.EventType.NODE_REMOVED);
                        break;
                    default:
                        // 未知类型，使用 SERVICE_UPDATED 作为默认值
                        event.setEventType(ServiceChangeEvent.EventType.SERVICE_UPDATED);
                }
            }
        } else {
            // 如果事件类型为空，使用默认值
            event.setEventType(ServiceChangeEvent.EventType.SERVICE_UPDATED);
        }
        
        // 转换服务信息
        event.setService(toServiceInfo(proto.getService()));
        
        // 转换节点列表
        event.setAllNodes(toNodeInfoList(proto.getNodesList()));
        
        // 转换变更的节点
        if (proto.hasChangedNode()) {
            event.setChangedNode(toNodeInfo(proto.getChangedNode()));
        }
        
        return event;
    }
    
    // ========== 配置中心转换方法 ==========
    
    /**
     * 将 Proto ConfigData 转换为 ConfigInfo
     * 
     * @param proto Proto ConfigData 对象，如果为 null 则返回 null
     * @return ConfigInfo 领域对象
     */
    public static ConfigInfo toConfigInfo(ConfigProto.ConfigData proto) {
        if (proto == null) {
            return null;
        }
        
        ConfigInfo info = new ConfigInfo();
        info.setNamespaceId(proto.getNamespaceId());
        info.setGroupName(proto.getGroupName());
        info.setConfigDataId(proto.getConfigDataId());
        info.setContentType(proto.getContentType());
        info.setConfigContent(proto.getConfigContent());
        info.setContentMd5(proto.getContentMd5());
        info.setConfigDesc(proto.getConfigDesc());
        info.setConfigVersion(proto.getConfigVersion());
        info.setChangeType(proto.getChangeType());
        info.setChangeReason(proto.getChangeReason());
        info.setChangedBy(proto.getChangedBy());
        
        return info;
    }
    
    /**
     * 将 Proto ConfigData 列表转换为 ConfigInfo 列表
     * 
     * @param protoList Proto ConfigData 列表，如果为 null 或空则返回空列表
     * @return ConfigInfo 列表
     */
    public static List<ConfigInfo> toConfigInfoList(List<ConfigProto.ConfigData> protoList) {
        if (protoList == null || protoList.isEmpty()) {
            return new ArrayList<>();
        }
        
        return protoList.stream()
                .map(ProtoConverter::toConfigInfo)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 将 Proto ConfigChangeEvent 转换为 ConfigChangeEvent
     * 
     * <p>自动处理事件类型的转换，如果事件类型不匹配，会尝试映射或使用默认值。</p>
     * 
     * @param proto Proto ConfigChangeEvent 对象，如果为 null 则返回 null
     * @return ConfigChangeEvent 领域对象
     */
    public static ConfigChangeEvent toConfigChangeEvent(ConfigProto.ConfigChangeEvent proto) {
        if (proto == null) {
            return null;
        }
        
        ConfigChangeEvent event = new ConfigChangeEvent();
        
        // 转换时间戳和配置标识
        event.setTimestamp(proto.getTimestamp());
        event.setNamespaceId(proto.getNamespaceId());
        event.setGroupName(proto.getGroupName());
        event.setConfigDataId(proto.getConfigDataId());
        event.setContentMd5(proto.getContentMd5());
        
        // 转换事件类型
        String eventTypeStr = proto.getEventType();
        if (eventTypeStr != null && !eventTypeStr.isEmpty()) {
            try {
                event.setEventType(ConfigChangeEvent.EventType.valueOf(eventTypeStr));
            } catch (IllegalArgumentException e) {
                // 如果事件类型不匹配，尝试映射
                switch (eventTypeStr) {
                    case "CONFIG_UPDATED":
                        event.setEventType(ConfigChangeEvent.EventType.CONFIG_UPDATED);
                        break;
                    case "CONFIG_DELETED":
                        event.setEventType(ConfigChangeEvent.EventType.CONFIG_DELETED);
                        break;
                    default:
                        // 未知类型，使用 CONFIG_UPDATED 作为默认值
                        event.setEventType(ConfigChangeEvent.EventType.CONFIG_UPDATED);
                }
            }
        } else {
            // 如果事件类型为空，使用默认值
            event.setEventType(ConfigChangeEvent.EventType.CONFIG_UPDATED);
        }
        
        // 转换配置数据（如果存在）
        if (proto.hasConfig()) {
            event.setConfig(toConfigInfo(proto.getConfig()));
        }
        
        return event;
    }
    
    /**
     * 将 Proto ConfigHistory 转换为 ConfigHistory
     * 
     * @param proto Proto ConfigHistory 对象，如果为 null 则返回 null
     * @return ConfigHistory 领域对象
     */
    public static ConfigHistory toConfigHistory(ConfigProto.ConfigHistory proto) {
        if (proto == null) {
            return null;
        }
        
        ConfigHistory history = new ConfigHistory();
        history.setConfigHistoryId(proto.getConfigHistoryId());
        history.setNamespaceId(proto.getNamespaceId());
        history.setGroupName(proto.getGroupName());
        history.setConfigDataId(proto.getConfigDataId());
        history.setContentType(proto.getContentType());
        history.setConfigContent(proto.getConfigContent());
        history.setContentMd5(proto.getContentMd5());
        history.setConfigVersion(proto.getConfigVersion());
        history.setChangeType(proto.getChangeType());
        history.setChangeReason(proto.getChangeReason());
        history.setChangedBy(proto.getChangedBy());
        history.setChangeTime(proto.getChangeTime());
        
        return history;
    }
    
    /**
     * 将 Proto ConfigHistory 列表转换为 ConfigHistory 列表
     * 
     * @param protoList Proto ConfigHistory 列表，如果为 null 或空则返回空列表
     * @return ConfigHistory 列表
     */
    public static List<ConfigHistory> toConfigHistoryList(List<ConfigProto.ConfigHistory> protoList) {
        if (protoList == null || protoList.isEmpty()) {
            return new ArrayList<>();
        }
        
        return protoList.stream()
                .map(ProtoConverter::toConfigHistory)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    // ========== 领域对象转 Proto 对象（用于发送请求）==========
    
    /**
     * 将 ConfigInfo 转换为 Proto ConfigData
     * 
     * @param info ConfigInfo 领域对象，如果为 null 则返回 null
     * @return Proto ConfigData 对象
     */
    public static ConfigProto.ConfigData toProtoConfigData(ConfigInfo info) {
        if (info == null) {
            return null;
        }
        
        ConfigProto.ConfigData.Builder builder = ConfigProto.ConfigData.newBuilder()
                .setNamespaceId(info.getNamespaceId())
                .setGroupName(info.getGroupName())
                .setConfigDataId(info.getConfigDataId());
        
        if (info.getContentType() != null) {
            builder.setContentType(info.getContentType());
        }
        if (info.getConfigContent() != null) {
            builder.setConfigContent(info.getConfigContent());
        }
        if (info.getContentMd5() != null) {
            builder.setContentMd5(info.getContentMd5());
        }
        if (info.getConfigDesc() != null) {
            builder.setConfigDesc(info.getConfigDesc());
        }
        if (info.getConfigVersion() > 0) {
            builder.setConfigVersion(info.getConfigVersion());
        }
        if (info.getChangeType() != null) {
            builder.setChangeType(info.getChangeType());
        }
        if (info.getChangeReason() != null) {
            builder.setChangeReason(info.getChangeReason());
        }
        if (info.getChangedBy() != null) {
            builder.setChangedBy(info.getChangedBy());
        }
        
        return builder.build();
    }
    
    // ========== 响应对象转换方法 ==========
    
    /**
     * 将 Proto GetConfigResponse 转换为 GetConfigResult
     */
    public static GetConfigResult toGetConfigResult(ConfigProto.GetConfigResponse proto) {
        if (proto == null) {
            return new GetConfigResult(false, "响应为 null", null);
        }
        return new GetConfigResult(
                proto.getSuccess(),
                proto.getMessage(),
                toConfigInfo(proto.getConfig())
        );
    }
    
    /**
     * 将 Proto SaveConfigResponse 转换为 SaveConfigResult
     */
    public static SaveConfigResult toSaveConfigResult(ConfigProto.SaveConfigResponse proto) {
        if (proto == null) {
            return new SaveConfigResult(false, "响应为 null", 0, null);
        }
        SaveConfigResult result = new SaveConfigResult(
                proto.getSuccess(),
                proto.getMessage(),
                proto.getVersion(),
                proto.getContentMd5()
        );
        result.setCode(proto.getCode());
        return result;
    }
    
    /**
     * 将 Proto ConfigResponse 转换为 OperationResult
     */
    public static OperationResult toOperationResult(ConfigProto.ConfigResponse proto) {
        if (proto == null) {
            return new OperationResult(false, "响应为 null");
        }
        return new OperationResult(proto.getSuccess(), proto.getMessage(), proto.getCode());
    }
    
    /**
     * 将 Proto RegistryResponse 转换为 OperationResult
     */
    public static OperationResult toOperationResult(RegistryProto.RegistryResponse proto) {
        if (proto == null) {
            return new OperationResult(false, "响应为 null");
        }
        return new OperationResult(proto.getSuccess(), proto.getMessage(), proto.getCode());
    }
    
    /**
     * 将 Proto RegisterServiceResponse 转换为 RegisterServiceResult
     */
    public static RegisterServiceResult toRegisterServiceResult(RegistryProto.RegisterServiceResponse proto) {
        if (proto == null) {
            return new RegisterServiceResult(false, "响应为 null", null);
        }
        RegisterServiceResult result = new RegisterServiceResult(
                proto.getSuccess(),
                proto.getMessage(),
                proto.getNodeId()
        );
        result.setCode(proto.getCode());
        return result;
    }
    
    /**
     * 将 Proto RegisterNodeResponse 转换为 RegisterNodeResult
     */
    public static RegisterNodeResult toRegisterNodeResult(RegistryProto.RegisterNodeResponse proto) {
        if (proto == null) {
            return new RegisterNodeResult(false, "响应为 null", null);
        }
        return new RegisterNodeResult(
                proto.getSuccess(),
                proto.getMessage(),
                proto.getNodeId()
        );
    }
    
    /**
     * 将 Proto GetServiceResponse 转换为 GetServiceResult
     */
    public static GetServiceResult toGetServiceResult(RegistryProto.GetServiceResponse proto) {
        if (proto == null) {
            return new GetServiceResult(false, "响应为 null", null, new ArrayList<>());
        }
        return new GetServiceResult(
                proto.getSuccess(),
                proto.getMessage(),
                toServiceInfo(proto.getService()),
                toNodeInfoList(proto.getNodesList())
        );
    }
    
    /**
     * 将 Proto RollbackConfigResponse 转换为 RollbackConfigResult
     */
    public static RollbackConfigResult toRollbackConfigResult(ConfigProto.RollbackConfigResponse proto) {
        if (proto == null) {
            return new RollbackConfigResult(false, "响应为 null", 0, null);
        }
        RollbackConfigResult result = new RollbackConfigResult(
                proto.getSuccess(),
                proto.getMessage(),
                proto.getNewVersion(),
                proto.getContentMd5()
        );
        result.setCode(proto.getCode());
        return result;
    }
    
    // ========== 领域对象转 Proto 对象（用于发送请求）==========
    
    /**
     * 将 ServiceInfo 转换为 Proto Service
     */
    public static RegistryProto.Service toProtoService(ServiceInfo info) {
        if (info == null) {
            return null;
        }
        
        RegistryProto.Service.Builder builder = RegistryProto.Service.newBuilder()
                .setNamespaceId(info.getNamespaceId())
                .setGroupName(info.getGroupName())
                .setServiceName(info.getServiceName());
        
        if (info.getServiceType() != null) {
            builder.setServiceType(info.getServiceType());
        }
        if (info.getServiceVersion() != null) {
            builder.setServiceVersion(info.getServiceVersion());
        }
        if (info.getServiceDescription() != null) {
            builder.setServiceDescription(info.getServiceDescription());
        }
        builder.setProtectThreshold(info.getProtectThreshold());
        
        if (info.getMetadata() != null && !info.getMetadata().isEmpty()) {
            builder.putAllMetadata(info.getMetadata());
        }
        if (info.getTags() != null && !info.getTags().isEmpty()) {
            builder.putAllTags(info.getTags());
        }
        
        return builder.build();
    }
    
    /**
     * 将 NodeInfo 转换为 Proto Node
     */
    public static RegistryProto.Node toProtoNode(NodeInfo info) {
        if (info == null) {
            return null;
        }
        
        RegistryProto.Node.Builder builder = RegistryProto.Node.newBuilder();
        
        // 必需字段，如果为 null 则使用空字符串
        if (info.getNamespaceId() != null) {
            builder.setNamespaceId(info.getNamespaceId());
        }
        if (info.getGroupName() != null) {
            builder.setGroupName(info.getGroupName());
        }
        if (info.getServiceName() != null) {
            builder.setServiceName(info.getServiceName());
        }
        if (info.getIpAddress() != null) {
            builder.setIpAddress(info.getIpAddress());
        }
        builder.setPortNumber(info.getPortNumber()); // int32 类型，有默认值 0
        builder.setWeight(info.getWeight()); // double 类型，有默认值 0.0
        
        // 可选字段
        if (info.getNodeId() != null && !info.getNodeId().isEmpty()) {
            builder.setNodeId(info.getNodeId());
        }
        if (info.getEphemeral() != null && !info.getEphemeral().isEmpty()) {
            builder.setEphemeral(info.getEphemeral());
        }
        if (info.getInstanceStatus() != null && !info.getInstanceStatus().isEmpty()) {
            builder.setInstanceStatus(info.getInstanceStatus());
        }
        if (info.getHealthyStatus() != null && !info.getHealthyStatus().isEmpty()) {
            builder.setHealthyStatus(info.getHealthyStatus());
        }
        if (info.getMetadata() != null && !info.getMetadata().isEmpty()) {
            builder.putAllMetadata(info.getMetadata());
        }
        
        return builder.build();
    }
}

