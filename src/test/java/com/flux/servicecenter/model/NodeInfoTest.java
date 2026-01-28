package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * NodeInfo 测试类
 * 
 * @author shangjian
 */
public class NodeInfoTest {

    @Test
    public void testDefaultConstructor() {
        NodeInfo info = new NodeInfo();
        assertNull(info.getNodeId());
        assertNull(info.getIpAddress());
        assertEquals(0, info.getPortNumber());
    }

    @Test
    public void testConstructorWithParams() {
        NodeInfo info = new NodeInfo("127.0.0.1", 8080);
        assertEquals("127.0.0.1", info.getIpAddress());
        assertEquals(8080, info.getPortNumber());
    }

    @Test
    public void testGettersAndSetters() {
        NodeInfo info = new NodeInfo();
        
        info.setNodeId("node1");
        info.setNamespaceId("ns1");
        info.setGroupName("g1");
        info.setServiceName("s1");
        info.setIpAddress("192.168.1.1");
        info.setPortNumber(9090);
        info.setWeight(1.5);
        info.setEphemeral("Y");
        info.setInstanceStatus("UP");
        info.setHealthyStatus("HEALTHY");
        
        assertEquals("node1", info.getNodeId());
        assertEquals("ns1", info.getNamespaceId());
        assertEquals("g1", info.getGroupName());
        assertEquals("s1", info.getServiceName());
        assertEquals("192.168.1.1", info.getIpAddress());
        assertEquals(9090, info.getPortNumber());
        assertEquals(1.5, info.getWeight(), 0.001);
        assertEquals("Y", info.getEphemeral());
        assertEquals("UP", info.getInstanceStatus());
        assertEquals("HEALTHY", info.getHealthyStatus());
    }

    @Test
    public void testMetadata() {
        NodeInfo info = new NodeInfo();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("zone", "zone1");
        metadata.put("rack", "rack1");
        
        info.setMetadata(metadata);
        assertEquals(metadata, info.getMetadata());
        assertEquals("zone1", info.getMetadata().get("zone"));
    }
}

