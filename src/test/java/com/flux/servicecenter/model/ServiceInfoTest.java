package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ServiceInfo 测试类
 * 
 * @author shangjian
 */
public class ServiceInfoTest {

    @Test
    public void testDefaultConstructor() {
        ServiceInfo info = new ServiceInfo();
        assertNull(info.getNamespaceId());
        assertNull(info.getGroupName());
        assertNull(info.getServiceName());
    }

    @Test
    public void testConstructorWithParams() {
        ServiceInfo info = new ServiceInfo("namespace1", "group1", "service1");
        assertEquals("namespace1", info.getNamespaceId());
        assertEquals("group1", info.getGroupName());
        assertEquals("service1", info.getServiceName());
    }

    @Test
    public void testGettersAndSetters() {
        ServiceInfo info = new ServiceInfo();
        
        info.setNamespaceId("ns1");
        info.setGroupName("g1");
        info.setServiceName("s1");
        info.setServiceType("INTERNAL");
        info.setServiceVersion("1.0.0");
        info.setServiceDescription("Test service");
        info.setProtectThreshold(0.8);
        
        assertEquals("ns1", info.getNamespaceId());
        assertEquals("g1", info.getGroupName());
        assertEquals("s1", info.getServiceName());
        assertEquals("INTERNAL", info.getServiceType());
        assertEquals("1.0.0", info.getServiceVersion());
        assertEquals("Test service", info.getServiceDescription());
        assertEquals(0.8, info.getProtectThreshold(), 0.001);
    }

    @Test
    public void testMetadata() {
        ServiceInfo info = new ServiceInfo();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", "value2");
        
        info.setMetadata(metadata);
        assertEquals(metadata, info.getMetadata());
        assertEquals("value1", info.getMetadata().get("key1"));
    }

    @Test
    public void testTags() {
        ServiceInfo info = new ServiceInfo();
        Map<String, String> tags = new HashMap<>();
        tags.put("env", "prod");
        tags.put("region", "us-east");
        
        info.setTags(tags);
        assertEquals(tags, info.getTags());
        assertEquals("prod", info.getTags().get("env"));
    }
}

