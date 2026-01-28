package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * GetServiceResult 测试类
 * 
 * @author shangjian
 */
public class GetServiceResultTest {

    @Test
    public void testDefaultConstructor() {
        GetServiceResult result = new GetServiceResult();
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
        assertNull(result.getService());
        assertNull(result.getNodes());
    }

    @Test
    public void testGettersAndSetters() {
        GetServiceResult result = new GetServiceResult();
        ServiceInfo service = new ServiceInfo("ns1", "g1", "service1");
        List<NodeInfo> nodes = new ArrayList<>();
        nodes.add(new NodeInfo("192.168.1.1", 8080));
        
        result.setSuccess(true);
        result.setMessage("Service found");
        result.setService(service);
        result.setNodes(nodes);
        
        assertTrue(result.isSuccess());
        assertEquals("Service found", result.getMessage());
        assertEquals(service, result.getService());
        assertEquals(nodes, result.getNodes());
        assertEquals(1, result.getNodes().size());
    }
}

