package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RegisterServiceResult 测试类
 * 
 * @author shangjian
 */
public class RegisterServiceResultTest {

    @Test
    public void testDefaultConstructor() {
        RegisterServiceResult result = new RegisterServiceResult();
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
        assertNull(result.getNodeId());
    }

    @Test
    public void testGettersAndSetters() {
        RegisterServiceResult result = new RegisterServiceResult();
        result.setSuccess(true);
        result.setMessage("Service registered");
        result.setNodeId("node123");
        
        assertTrue(result.isSuccess());
        assertEquals("Service registered", result.getMessage());
        assertEquals("node123", result.getNodeId());
    }
}

