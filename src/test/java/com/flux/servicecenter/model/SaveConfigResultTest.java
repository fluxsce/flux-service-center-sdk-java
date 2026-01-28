package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SaveConfigResult 测试类
 * 
 * @author shangjian
 */
public class SaveConfigResultTest {

    @Test
    public void testDefaultConstructor() {
        SaveConfigResult result = new SaveConfigResult();
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
        assertEquals(0L, result.getVersion());
        assertNull(result.getContentMd5());
    }

    @Test
    public void testGettersAndSetters() {
        SaveConfigResult result = new SaveConfigResult();
        result.setSuccess(true);
        result.setMessage("Config saved");
        result.setVersion(2L);
        result.setContentMd5("abc123");
        
        assertTrue(result.isSuccess());
        assertEquals("Config saved", result.getMessage());
        assertEquals(2L, result.getVersion());
        assertEquals("abc123", result.getContentMd5());
    }
}

