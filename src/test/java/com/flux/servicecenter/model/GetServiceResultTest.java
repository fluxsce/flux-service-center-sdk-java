package com.flux.servicecenter.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GetServiceResultTest {

    @Test
    public void testDefaultConstructor() {
        GetServiceResult result = new GetServiceResult();
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
        assertNull(result.getService());
        assertNull(result.getNodes());
        assertTrue(result.getHealthyNodes().isEmpty());
    }

    @Test
    public void testGettersAndSetters() {
        GetServiceResult result = new GetServiceResult();
        ServiceInfo service = new ServiceInfo("ns1", "g1", "service1");
        List<NodeInfo> nodes = new ArrayList<>();
        NodeInfo healthy = new NodeInfo("192.168.1.1", 8080);
        healthy.setHealthyStatus("HEALTHY");
        healthy.setInstanceStatus("UP");
        NodeInfo down = new NodeInfo("192.168.1.2", 8081);
        down.setHealthyStatus("UNHEALTHY");
        down.setInstanceStatus("DOWN");
        nodes.add(healthy);
        nodes.add(down);

        result.setSuccess(true);
        result.setMessage("Service found");
        result.setService(service);
        result.setNodes(nodes);

        assertTrue(result.isSuccess());
        assertEquals("Service found", result.getMessage());
        assertEquals(service, result.getService());
        assertEquals(2, result.getNodes().size());
        assertEquals(1, result.getHealthyNodes().size());
        assertEquals("192.168.1.1", result.getHealthyNodes().get(0).getIpAddress());
    }
}
