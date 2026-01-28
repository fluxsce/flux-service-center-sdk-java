package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * GetConfigResult 测试类
 * 
 * @author shangjian
 */
public class GetConfigResultTest {

    @Test
    public void testDefaultConstructor() {
        GetConfigResult result = new GetConfigResult();
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
        assertNull(result.getConfig());
    }

    @Test
    public void testConstructorWithParams() {
        ConfigInfo config = new ConfigInfo("ns1", "g1", "config1");
        GetConfigResult result = new GetConfigResult(true, "Success", config);
        
        assertTrue(result.isSuccess());
        assertEquals("Success", result.getMessage());
        assertEquals(config, result.getConfig());
    }

    @Test
    public void testGettersAndSetters() {
        GetConfigResult result = new GetConfigResult();
        ConfigInfo config = new ConfigInfo("ns1", "g1", "config1");
        
        result.setSuccess(true);
        result.setMessage("Config found");
        result.setConfig(config);
        
        assertTrue(result.isSuccess());
        assertEquals("Config found", result.getMessage());
        assertEquals(config, result.getConfig());
    }

    @Test
    public void testEquals() {
        ConfigInfo config1 = new ConfigInfo("ns1", "g1", "config1");
        ConfigInfo config2 = new ConfigInfo("ns1", "g1", "config1");
        GetConfigResult result1 = new GetConfigResult(true, "Success", config1);
        GetConfigResult result2 = new GetConfigResult(true, "Success", config2);
        
        assertEquals(result1, result2);
    }

    @Test
    public void testHashCode() {
        ConfigInfo config1 = new ConfigInfo("ns1", "g1", "config1");
        ConfigInfo config2 = new ConfigInfo("ns1", "g1", "config1");
        GetConfigResult result1 = new GetConfigResult(true, "Success", config1);
        GetConfigResult result2 = new GetConfigResult(true, "Success", config2);
        
        assertEquals(result1.hashCode(), result2.hashCode());
    }
}

