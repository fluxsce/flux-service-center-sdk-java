package com.flux.servicecenter.model;

import com.flux.servicecenter.config.ConfigProto;
import com.flux.servicecenter.registry.RegistryProto;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProtoConverter 测试类
 * 
 * @author shangjian
 */
public class ProtoConverterTest {

    @Test
    public void testToServiceInfo_Null() {
        ServiceInfo result = ProtoConverter.toServiceInfo(null);
        assertNull(result);
    }

    @Test
    public void testToServiceInfo() {
        RegistryProto.Service proto = RegistryProto.Service.newBuilder()
                .setNamespaceId("ns1")
                .setGroupName("g1")
                .setServiceName("service1")
                .setServiceType("INTERNAL")
                .setServiceVersion("1.0.0")
                .setServiceDescription("Test service")
                .setProtectThreshold(0.8)
                .putMetadata("key1", "value1")
                .putTags("env", "prod")
                .build();
        
        ServiceInfo info = ProtoConverter.toServiceInfo(proto);
        assertNotNull(info);
        assertEquals("ns1", info.getNamespaceId());
        assertEquals("g1", info.getGroupName());
        assertEquals("service1", info.getServiceName());
        assertEquals("INTERNAL", info.getServiceType());
        assertEquals("1.0.0", info.getServiceVersion());
        assertEquals("Test service", info.getServiceDescription());
        assertEquals(0.8, info.getProtectThreshold(), 0.001);
        assertEquals("value1", info.getMetadata().get("key1"));
        assertEquals("prod", info.getTags().get("env"));
    }

    @Test
    public void testToServiceInfo_EmptyMetadata() {
        RegistryProto.Service proto = RegistryProto.Service.newBuilder()
                .setNamespaceId("ns1")
                .setGroupName("g1")
                .setServiceName("service1")
                .build();
        
        ServiceInfo info = ProtoConverter.toServiceInfo(proto);
        assertNotNull(info);
        assertNotNull(info.getMetadata());
        assertTrue(info.getMetadata().isEmpty());
        assertNotNull(info.getTags());
        assertTrue(info.getTags().isEmpty());
    }

    @Test
    public void testToNodeInfo_Null() {
        NodeInfo result = ProtoConverter.toNodeInfo(null);
        assertNull(result);
    }

    @Test
    public void testToNodeInfo() {
        RegistryProto.Node proto = RegistryProto.Node.newBuilder()
                .setNodeId("node1")
                .setNamespaceId("ns1")
                .setGroupName("g1")
                .setServiceName("service1")
                .setIpAddress("192.168.1.1")
                .setPortNumber(8080)
                .setWeight(1.5)
                .setEphemeral("Y")
                .setInstanceStatus("UP")
                .setHealthyStatus("HEALTHY")
                .putMetadata("zone", "zone1")
                .build();
        
        NodeInfo info = ProtoConverter.toNodeInfo(proto);
        assertNotNull(info);
        assertEquals("node1", info.getNodeId());
        assertEquals("ns1", info.getNamespaceId());
        assertEquals("g1", info.getGroupName());
        assertEquals("service1", info.getServiceName());
        assertEquals("192.168.1.1", info.getIpAddress());
        assertEquals(8080, info.getPortNumber());
        assertEquals(1.5, info.getWeight(), 0.001);
        assertEquals("Y", info.getEphemeral());
        assertEquals("UP", info.getInstanceStatus());
        assertEquals("HEALTHY", info.getHealthyStatus());
        assertEquals("zone1", info.getMetadata().get("zone"));
    }

    @Test
    public void testToNodeInfoList_Null() {
        List<NodeInfo> result = ProtoConverter.toNodeInfoList(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testToNodeInfoList_Empty() {
        List<NodeInfo> result = ProtoConverter.toNodeInfoList(new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testToNodeInfoList() {
        List<RegistryProto.Node> protoList = new ArrayList<>();
        protoList.add(RegistryProto.Node.newBuilder()
                .setNodeId("node1")
                .setIpAddress("192.168.1.1")
                .setPortNumber(8080)
                .build());
        protoList.add(RegistryProto.Node.newBuilder()
                .setNodeId("node2")
                .setIpAddress("192.168.1.2")
                .setPortNumber(8081)
                .build());
        
        List<NodeInfo> result = ProtoConverter.toNodeInfoList(protoList);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("node1", result.get(0).getNodeId());
        assertEquals("node2", result.get(1).getNodeId());
    }

    @Test
    public void testToServiceChangeEvent_Null() {
        ServiceChangeEvent result = ProtoConverter.toServiceChangeEvent(null);
        assertNull(result);
    }

    @Test
    public void testToServiceChangeEvent() {
        RegistryProto.ServiceChangeEvent proto = RegistryProto.ServiceChangeEvent.newBuilder()
                .setEventType("NODE_ADDED")
                .setTimestamp("2024-01-01 12:00:00")
                .setNamespaceId("ns1")
                .setGroupName("g1")
                .setServiceName("service1")
                .setChangedNode(RegistryProto.Node.newBuilder()
                        .setNodeId("node1")
                        .setIpAddress("192.168.1.1")
                        .setPortNumber(8080)
                        .build())
                .build();
        
        ServiceChangeEvent event = ProtoConverter.toServiceChangeEvent(proto);
        assertNotNull(event);
        assertEquals(ServiceChangeEvent.EventType.NODE_ADDED, event.getEventType());
        assertEquals("2024-01-01 12:00:00", event.getTimestamp());
        assertEquals("ns1", event.getNamespaceId());
        assertEquals("g1", event.getGroupName());
        assertEquals("service1", event.getServiceName());
        assertNotNull(event.getChangedNode());
        assertEquals("node1", event.getChangedNode().getNodeId());
    }

    @Test
    public void testToConfigInfo_Null() {
        ConfigInfo result = ProtoConverter.toConfigInfo(null);
        assertNull(result);
    }

    @Test
    public void testToConfigInfo() {
        ConfigProto.ConfigData proto = ConfigProto.ConfigData.newBuilder()
                .setNamespaceId("ns1")
                .setGroupName("g1")
                .setConfigDataId("config1")
                .setContentType("JSON")
                .setConfigContent("{\"key\":\"value\"}")
                .setContentMd5("abc123")
                .setConfigDesc("Test config")
                .setConfigVersion(1L)
                .build();
        
        ConfigInfo info = ProtoConverter.toConfigInfo(proto);
        assertNotNull(info);
        assertEquals("ns1", info.getNamespaceId());
        assertEquals("g1", info.getGroupName());
        assertEquals("config1", info.getConfigDataId());
        assertEquals("JSON", info.getContentType());
        assertEquals("{\"key\":\"value\"}", info.getConfigContent());
        assertEquals("abc123", info.getContentMd5());
        assertEquals("Test config", info.getConfigDesc());
        assertEquals(1L, info.getConfigVersion());
    }

    @Test
    public void testToConfigInfoList_Null() {
        List<ConfigInfo> result = ProtoConverter.toConfigInfoList(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testToConfigInfoList_Empty() {
        List<ConfigInfo> result = ProtoConverter.toConfigInfoList(new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testToConfigInfoList() {
        List<ConfigProto.ConfigData> protoList = new ArrayList<>();
        protoList.add(ConfigProto.ConfigData.newBuilder()
                .setNamespaceId("ns1")
                .setGroupName("g1")
                .setConfigDataId("config1")
                .setConfigContent("content1")
                .build());
        protoList.add(ConfigProto.ConfigData.newBuilder()
                .setNamespaceId("ns1")
                .setGroupName("g1")
                .setConfigDataId("config2")
                .setConfigContent("content2")
                .build());
        
        List<ConfigInfo> result = ProtoConverter.toConfigInfoList(protoList);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("config1", result.get(0).getConfigDataId());
        assertEquals("config2", result.get(1).getConfigDataId());
    }

    @Test
    public void testToConfigChangeEvent_Null() {
        ConfigChangeEvent result = ProtoConverter.toConfigChangeEvent(null);
        assertNull(result);
    }

    @Test
    public void testToConfigChangeEvent() {
        ConfigProto.ConfigChangeEvent proto = ConfigProto.ConfigChangeEvent.newBuilder()
                .setEventType("CONFIG_UPDATED")
                .setTimestamp("2024-01-01 12:00:00")
                .setNamespaceId("ns1")
                .setGroupName("g1")
                .setConfigDataId("config1")
                .setConfig(ConfigProto.ConfigData.newBuilder()
                        .setNamespaceId("ns1")
                        .setGroupName("g1")
                        .setConfigDataId("config1")
                        .setConfigContent("content")
                        .build())
                .setContentMd5("abc123")
                .build();
        
        ConfigChangeEvent event = ProtoConverter.toConfigChangeEvent(proto);
        assertNotNull(event);
        assertEquals(ConfigChangeEvent.EventType.CONFIG_UPDATED, event.getEventType());
        assertEquals("2024-01-01 12:00:00", event.getTimestamp());
        assertEquals("ns1", event.getNamespaceId());
        assertEquals("g1", event.getGroupName());
        assertEquals("config1", event.getConfigDataId());
        assertNotNull(event.getConfig());
        assertEquals("abc123", event.getContentMd5());
    }

    @Test
    public void testToProtoService() {
        ServiceInfo info = new ServiceInfo("ns1", "g1", "service1");
        info.setServiceType("INTERNAL");
        info.setServiceVersion("1.0.0");
        info.setServiceDescription("Test service");
        info.setProtectThreshold(0.8);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        info.setMetadata(metadata);
        Map<String, String> tags = new HashMap<>();
        tags.put("env", "prod");
        info.setTags(tags);
        
        RegistryProto.Service proto = ProtoConverter.toProtoService(info);
        assertNotNull(proto);
        assertEquals("ns1", proto.getNamespaceId());
        assertEquals("g1", proto.getGroupName());
        assertEquals("service1", proto.getServiceName());
        assertEquals("INTERNAL", proto.getServiceType());
        assertEquals("1.0.0", proto.getServiceVersion());
        assertEquals("Test service", proto.getServiceDescription());
        assertEquals(0.8, proto.getProtectThreshold(), 0.001);
        assertEquals("value1", proto.getMetadataMap().get("key1"));
        assertEquals("prod", proto.getTagsMap().get("env"));
    }

    @Test
    public void testToProtoNode() {
        NodeInfo info = new NodeInfo("192.168.1.1", 8080);
        info.setNodeId("node1");
        info.setNamespaceId("ns1");
        info.setGroupName("g1");
        info.setServiceName("service1");
        info.setWeight(1.5);
        info.setEphemeral("Y");
        info.setInstanceStatus("UP");
        info.setHealthyStatus("HEALTHY");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("zone", "zone1");
        info.setMetadata(metadata);
        
        RegistryProto.Node proto = ProtoConverter.toProtoNode(info);
        assertNotNull(proto);
        assertEquals("node1", proto.getNodeId());
        assertEquals("ns1", proto.getNamespaceId());
        assertEquals("g1", proto.getGroupName());
        assertEquals("service1", proto.getServiceName());
        assertEquals("192.168.1.1", proto.getIpAddress());
        assertEquals(8080, proto.getPortNumber());
        assertEquals(1.5, proto.getWeight(), 0.001);
        assertEquals("Y", proto.getEphemeral());
        assertEquals("UP", proto.getInstanceStatus());
        assertEquals("HEALTHY", proto.getHealthyStatus());
        assertEquals("zone1", proto.getMetadataMap().get("zone"));
    }

    @Test
    public void testToProtoConfigData() {
        ConfigInfo info = new ConfigInfo("ns1", "g1", "config1");
        info.setContentType("JSON");
        info.setConfigContent("{\"key\":\"value\"}");
        info.setConfigDesc("Test config");
        
        ConfigProto.ConfigData proto = ProtoConverter.toProtoConfigData(info);
        assertNotNull(proto);
        assertEquals("ns1", proto.getNamespaceId());
        assertEquals("g1", proto.getGroupName());
        assertEquals("config1", proto.getConfigDataId());
        assertEquals("JSON", proto.getContentType());
        assertEquals("{\"key\":\"value\"}", proto.getConfigContent());
        assertEquals("Test config", proto.getConfigDesc());
    }
}

