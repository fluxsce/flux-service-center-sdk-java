package com.flux.servicecenter.client.internal;

import com.flux.servicecenter.config.ConfigProto;
import com.flux.servicecenter.model.ConfigChangeEvent;
import com.flux.servicecenter.model.ConfigHistory;
import com.flux.servicecenter.model.ConfigInfo;
import com.flux.servicecenter.model.NodeInfo;
import com.flux.servicecenter.model.ServiceChangeEvent;
import com.flux.servicecenter.model.ServiceInfo;
import com.flux.servicecenter.registry.RegistryProto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Converters {
    private Converters() {
    }

    public static RegistryProto.Service toProto(ServiceInfo in, RegistryProto.Node node) {
        RegistryProto.Service.Builder b = RegistryProto.Service.newBuilder();
        if (in != null) {
            if (in.getNamespaceId() != null) {
                b.setNamespaceId(in.getNamespaceId());
            }
            if (in.getGroupName() != null) {
                b.setGroupName(in.getGroupName());
            }
            if (in.getServiceName() != null) {
                b.setServiceName(in.getServiceName());
            }
            if (in.getServiceType() != null) {
                b.setServiceType(in.getServiceType());
            }
            if (in.getServiceVersion() != null) {
                b.setServiceVersion(in.getServiceVersion());
            }
            if (in.getServiceDescription() != null) {
                b.setServiceDescription(in.getServiceDescription());
            }
            b.setProtectThreshold(in.getProtectThreshold());
            if (in.getMetadata() != null) {
                b.putAllMetadata(in.getMetadata());
            }
            if (in.getTags() != null) {
                b.putAllTags(in.getTags());
            }
        }
        if (node != null) {
            b.setNode(node);
        }
        return b.build();
    }

    public static RegistryProto.Node toProto(NodeInfo in) {
        RegistryProto.Node.Builder b = RegistryProto.Node.newBuilder();
        if (in == null) {
            return b.build();
        }
        if (in.getNodeId() != null) {
            b.setNodeId(in.getNodeId());
        }
        if (in.getNamespaceId() != null) {
            b.setNamespaceId(in.getNamespaceId());
        }
        if (in.getGroupName() != null) {
            b.setGroupName(in.getGroupName());
        }
        if (in.getServiceName() != null) {
            b.setServiceName(in.getServiceName());
        }
        if (in.getIpAddress() != null) {
            b.setIpAddress(in.getIpAddress());
        }
        b.setPortNumber(in.getPortNumber());
        b.setWeight(in.getWeight());
        b.setEphemeral(isEphemeral(in.getEphemeral()));
        b.setInstanceStatus(toInstanceStatus(in.getInstanceStatus()));
        b.setHealthyStatus(toHealthyStatus(in.getHealthyStatus()));
        if (in.getMetadata() != null) {
            b.putAllMetadata(in.getMetadata());
        }
        return b.build();
    }

    public static ServiceInfo toModel(RegistryProto.Service in) {
        if (in == null || in.getServiceName().isEmpty() && in.getNamespaceId().isEmpty()) {
            return null;
        }
        ServiceInfo out = new ServiceInfo();
        out.setNamespaceId(in.getNamespaceId());
        out.setGroupName(in.getGroupName());
        out.setServiceName(in.getServiceName());
        out.setServiceType(in.getServiceType());
        out.setServiceVersion(in.getServiceVersion());
        out.setServiceDescription(in.getServiceDescription());
        out.setProtectThreshold(in.getProtectThreshold());
        out.setMetadata(in.getMetadataMap());
        out.setTags(in.getTagsMap());
        return out;
    }

    public static NodeInfo toModel(RegistryProto.Node in) {
        if (in == null) {
            return null;
        }
        NodeInfo out = new NodeInfo();
        out.setNodeId(in.getNodeId());
        out.setNamespaceId(in.getNamespaceId());
        out.setGroupName(in.getGroupName());
        out.setServiceName(in.getServiceName());
        out.setIpAddress(in.getIpAddress());
        out.setPortNumber(in.getPortNumber());
        out.setWeight(in.getWeight());
        out.setEphemeral(in.getEphemeral() ? "Y" : "N");
        out.setInstanceStatus(fromInstanceStatus(in.getInstanceStatus()));
        out.setHealthyStatus(fromHealthyStatus(in.getHealthyStatus()));
        out.setMetadata(in.getMetadataMap());
        return out;
    }

    public static List<NodeInfo> toNodes(List<RegistryProto.Node> nodes) {
        List<NodeInfo> out = new ArrayList<>();
        if (nodes == null) {
            return out;
        }
        for (RegistryProto.Node n : nodes) {
            out.add(toModel(n));
        }
        return out;
    }

    public static ConfigProto.ConfigData toProto(ConfigInfo in) {
        ConfigProto.ConfigData.Builder b = ConfigProto.ConfigData.newBuilder();
        if (in == null) {
            return b.build();
        }
        if (in.getNamespaceId() != null) {
            b.setNamespaceId(in.getNamespaceId());
        }
        if (in.getGroupName() != null) {
            b.setGroupName(in.getGroupName());
        }
        if (in.getConfigDataId() != null) {
            b.setConfigDataId(in.getConfigDataId());
        }
        if (in.getContentType() != null) {
            b.setContentType(in.getContentType());
        }
        if (in.getConfigContent() != null) {
            b.setConfigContent(in.getConfigContent());
        }
        if (in.getContentMd5() != null) {
            b.setContentMd5(in.getContentMd5());
        }
        if (in.getConfigDesc() != null) {
            b.setConfigDesc(in.getConfigDesc());
        }
        b.setConfigVersion(in.getConfigVersion());
        return b.build();
    }

    public static ConfigInfo toModel(ConfigProto.ConfigData in) {
        if (in == null) {
            return null;
        }
        ConfigInfo out = new ConfigInfo();
        out.setNamespaceId(in.getNamespaceId());
        out.setGroupName(in.getGroupName());
        out.setConfigDataId(in.getConfigDataId());
        out.setContentType(in.getContentType());
        out.setConfigContent(in.getConfigContent());
        out.setContentMd5(in.getContentMd5());
        out.setConfigDesc(in.getConfigDesc());
        out.setConfigVersion(in.getConfigVersion());
        return out;
    }

    public static ConfigHistory toHistory(ConfigProto.ConfigHistory in) {
        ConfigHistory out = new ConfigHistory();
        out.setConfigHistoryId(in.getConfigHistoryId());
        out.setNamespaceId(in.getNamespaceId());
        out.setGroupName(in.getGroupName());
        out.setConfigDataId(in.getConfigDataId());
        out.setContentType(in.getContentType());
        out.setConfigContent(in.getConfigContent());
        out.setContentMd5(in.getContentMd5());
        out.setConfigVersion(in.getConfigVersion());
        out.setChangeType(fromChangeType(in.getChangeType()));
        out.setChangeReason(in.getChangeReason());
        out.setChangedBy(in.getChangedBy());
        out.setChangeTime(in.getChangeTime() == 0 ? "" : Instant.ofEpochMilli(in.getChangeTime()).toString());
        return out;
    }

    public static ServiceChangeEvent toModel(RegistryProto.ServiceChangeEvent in) {
        ServiceChangeEvent out = new ServiceChangeEvent();
        out.setEventType(mapNaming(in.getEventType()));
        out.setTimestamp(Long.toString(in.getTimestamp()));
        out.setNamespaceId(in.getNamespaceId());
        out.setGroupName(in.getGroupName());
        out.setServiceName(in.getServiceName());
        out.setService(toModel(in.getService()));
        out.setAllNodes(toNodes(in.getNodesList()));
        if (in.hasChangedNode()) {
            out.setChangedNode(toModel(in.getChangedNode()));
        }
        return out;
    }

    public static ConfigChangeEvent toModel(ConfigProto.ConfigChangeEvent in) {
        ConfigChangeEvent out = new ConfigChangeEvent();
        out.setEventType(mapConfig(in.getEventType()));
        out.setTimestamp(Long.toString(in.getTimestamp()));
        out.setNamespaceId(in.getNamespaceId());
        out.setGroupName(in.getGroupName());
        out.setConfigDataId(in.getConfigDataId());
        out.setContentMd5(in.getContentMd5());
        if (in.hasConfig()) {
            out.setConfig(toModel(in.getConfig()));
        }
        return out;
    }

    public static ServiceChangeEvent.EventType mapNaming(RegistryProto.NamingEventType type) {
        if (type == null) {
            return ServiceChangeEvent.EventType.SERVICE_UPDATED;
        }
        return switch (type) {
            case NAMING_EVENT_SERVICE_ADDED -> ServiceChangeEvent.EventType.SERVICE_ADDED;
            case NAMING_EVENT_SERVICE_DELETED -> ServiceChangeEvent.EventType.SERVICE_DELETED;
            case NAMING_EVENT_NODE_REGISTERED -> ServiceChangeEvent.EventType.NODE_ADDED;
            case NAMING_EVENT_NODE_UPDATED -> ServiceChangeEvent.EventType.NODE_UPDATED;
            case NAMING_EVENT_NODE_DEREGISTERED, NAMING_EVENT_NODE_EVICTED, NAMING_EVENT_NODE_OFFLINE
                    -> ServiceChangeEvent.EventType.NODE_REMOVED;
            default -> ServiceChangeEvent.EventType.SERVICE_UPDATED;
        };
    }

    public static ConfigChangeEvent.EventType mapConfig(ConfigProto.ConfigEventType type) {
        if (type == ConfigProto.ConfigEventType.CONFIG_EVENT_DELETED) {
            return ConfigChangeEvent.EventType.CONFIG_DELETED;
        }
        return ConfigChangeEvent.EventType.CONFIG_UPDATED;
    }

    public static String ns(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String group(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null || fallback.isBlank() ? "DEFAULT_GROUP" : fallback;
        }
        return value;
    }

    static boolean isEphemeral(String value) {
        return value == null || value.isBlank() || !"N".equalsIgnoreCase(value);
    }

    static RegistryProto.InstanceStatus toInstanceStatus(String value) {
        if (value == null) {
            return RegistryProto.InstanceStatus.INSTANCE_STATUS_UP;
        }
        return switch (value.toUpperCase()) {
            case "DOWN" -> RegistryProto.InstanceStatus.INSTANCE_STATUS_DOWN;
            case "STARTING" -> RegistryProto.InstanceStatus.INSTANCE_STATUS_STARTING;
            case "OUT_OF_SERVICE" -> RegistryProto.InstanceStatus.INSTANCE_STATUS_OUT_OF_SERVICE;
            default -> RegistryProto.InstanceStatus.INSTANCE_STATUS_UP;
        };
    }

    static String fromInstanceStatus(RegistryProto.InstanceStatus value) {
        if (value == null) {
            return "UP";
        }
        return switch (value) {
            case INSTANCE_STATUS_DOWN -> "DOWN";
            case INSTANCE_STATUS_STARTING -> "STARTING";
            case INSTANCE_STATUS_OUT_OF_SERVICE -> "OUT_OF_SERVICE";
            default -> "UP";
        };
    }

    static RegistryProto.HealthyStatus toHealthyStatus(String value) {
        if (value == null) {
            return RegistryProto.HealthyStatus.HEALTHY_STATUS_HEALTHY;
        }
        return switch (value.toUpperCase()) {
            case "UNHEALTHY" -> RegistryProto.HealthyStatus.HEALTHY_STATUS_UNHEALTHY;
            case "UNKNOWN" -> RegistryProto.HealthyStatus.HEALTHY_STATUS_UNKNOWN;
            default -> RegistryProto.HealthyStatus.HEALTHY_STATUS_HEALTHY;
        };
    }

    static String fromHealthyStatus(RegistryProto.HealthyStatus value) {
        if (value == null) {
            return "HEALTHY";
        }
        return switch (value) {
            case HEALTHY_STATUS_UNHEALTHY -> "UNHEALTHY";
            case HEALTHY_STATUS_UNKNOWN -> "UNKNOWN";
            default -> "HEALTHY";
        };
    }

    static String fromChangeType(ConfigProto.ConfigChangeType value) {
        if (value == null) {
            return "UPDATE";
        }
        return switch (value) {
            case CONFIG_CHANGE_TYPE_ADD -> "ADD";
            case CONFIG_CHANGE_TYPE_DELETE -> "DELETE";
            default -> "UPDATE";
        };
    }
}
