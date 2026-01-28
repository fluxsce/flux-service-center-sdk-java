package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RegisterNodeResult 测试类
 * 
 * @author shangjian
 */
public class RegisterNodeResultTest {

    @Test
    public void testDefaultConstructor() {
        RegisterNodeResult result = new RegisterNodeResult();
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
        assertNull(result.getNodeId());
    }

    @Test
    public void testGettersAndSetters() {
        RegisterNodeResult result = new RegisterNodeResult();
        result.setSuccess(true);
        result.setMessage("Node registered");
        result.setNodeId("node456");
        
        assertTrue(result.isSuccess());
        assertEquals("Node registered", result.getMessage());
        assertEquals("node456", result.getNodeId());
    }
}

