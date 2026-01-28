package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * OperationResult 测试类
 * 
 * @author shangjian
 */
public class OperationResultTest {

    @Test
    public void testDefaultConstructor() {
        OperationResult result = new OperationResult();
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
        assertNull(result.getCode());
    }

    @Test
    public void testConstructorWithSuccessAndMessage() {
        OperationResult result = new OperationResult(true, "Success");
        assertTrue(result.isSuccess());
        assertEquals("Success", result.getMessage());
        assertNull(result.getCode());
    }

    @Test
    public void testConstructorWithAllParams() {
        OperationResult result = new OperationResult(true, "Success", "CODE001");
        assertTrue(result.isSuccess());
        assertEquals("Success", result.getMessage());
        assertEquals("CODE001", result.getCode());
    }

    @Test
    public void testGettersAndSetters() {
        OperationResult result = new OperationResult();
        result.setSuccess(true);
        result.setMessage("Test message");
        result.setCode("TEST_CODE");
        
        assertTrue(result.isSuccess());
        assertEquals("Test message", result.getMessage());
        assertEquals("TEST_CODE", result.getCode());
    }

    @Test
    public void testEquals() {
        OperationResult result1 = new OperationResult(true, "Success", "CODE001");
        OperationResult result2 = new OperationResult(true, "Success", "CODE001");
        OperationResult result3 = new OperationResult(false, "Failed", "CODE002");
        
        assertEquals(result1, result2);
        assertNotEquals(result1, result3);
    }

    @Test
    public void testHashCode() {
        OperationResult result1 = new OperationResult(true, "Success", "CODE001");
        OperationResult result2 = new OperationResult(true, "Success", "CODE001");
        
        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    public void testToString() {
        OperationResult result = new OperationResult(true, "Success", "CODE001");
        String str = result.toString();
        assertTrue(str.contains("success=true"));
        assertTrue(str.contains("message='Success'"));
        assertTrue(str.contains("code='CODE001'"));
    }
}

