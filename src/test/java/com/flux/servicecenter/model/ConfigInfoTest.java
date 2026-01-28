package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigInfo 测试类
 * 
 * @author shangjian
 */
public class ConfigInfoTest {

    @Test
    public void testDefaultConstructor() {
        ConfigInfo info = new ConfigInfo();
        assertNull(info.getNamespaceId());
        assertNull(info.getGroupName());
        assertNull(info.getConfigDataId());
    }

    @Test
    public void testConstructorWithParams() {
        ConfigInfo info = new ConfigInfo("namespace1", "group1", "config1");
        assertEquals("namespace1", info.getNamespaceId());
        assertEquals("group1", info.getGroupName());
        assertEquals("config1", info.getConfigDataId());
    }

    @Test
    public void testGettersAndSetters() {
        ConfigInfo info = new ConfigInfo();
        
        info.setNamespaceId("ns1");
        info.setGroupName("g1");
        info.setConfigDataId("config1");
        info.setContentType("JSON");
        info.setConfigContent("{\"key\":\"value\"}");
        info.setContentMd5("abc123");
        info.setConfigDesc("Test config");
        info.setConfigVersion(1L);
        info.setChangeType("ADD");
        info.setChangeReason("Initial");
        info.setChangedBy("admin");
        
        assertEquals("ns1", info.getNamespaceId());
        assertEquals("g1", info.getGroupName());
        assertEquals("config1", info.getConfigDataId());
        assertEquals("JSON", info.getContentType());
        assertEquals("{\"key\":\"value\"}", info.getConfigContent());
        assertEquals("abc123", info.getContentMd5());
        assertEquals("Test config", info.getConfigDesc());
        assertEquals(1L, info.getConfigVersion());
        assertEquals("ADD", info.getChangeType());
        assertEquals("Initial", info.getChangeReason());
        assertEquals("admin", info.getChangedBy());
    }

    @Test
    public void testEqualsAndHashCode() {
        ConfigInfo info1 = new ConfigInfo("ns1", "g1", "config1");
        ConfigInfo info2 = new ConfigInfo("ns1", "g1", "config1");
        ConfigInfo info3 = new ConfigInfo("ns2", "g1", "config1");
        
        assertEquals(info1, info2);
        assertNotEquals(info1, info3);
        assertEquals(info1.hashCode(), info2.hashCode());
    }
}

