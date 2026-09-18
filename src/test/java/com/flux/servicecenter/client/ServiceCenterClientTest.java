package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.model.ConfigInfo;
import com.flux.servicecenter.model.NodeInfo;
import com.flux.servicecenter.model.ServiceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.0 客户端本地行为：构造校验、默认值填充、工厂入口。
 */
public class ServiceCenterClientTest {

    private ServiceCenterClient client;

    @AfterEach
    public void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void constructor_validConfig() {
        client = new ServiceCenterClient(validConfig());
        assertNotNull(client);
        assertFalse(client.isConnected());
        assertNull(client.getLastError());
    }

    @Test
    public void constructor_nullConfig() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceCenterClient(null));
    }

    @Test
    public void close_isIdempotent() {
        client = new ServiceCenterClient(validConfig());
        client.close();
        client.close();
        assertFalse(client.isConnected());
    }

    @Test
    public void factory_create_returnsStreamClient() {
        IServiceCenterClient created = ServiceCenterClients.create(validConfig());
        try {
            assertTrue(created instanceof StreamBasedServiceCenterClient);
            assertTrue(created instanceof ServiceCenterClient);
            assertFalse(created.isConnected());
        } finally {
            created.close();
        }
    }

    @Test
    public void registerService_fillsDefaultsBeforeConnectCheck() {
        client = new ServiceCenterClient(validConfig());
        ServiceInfo service = new ServiceInfo();
        service.setServiceName("test-service");
        NodeInfo node = new NodeInfo("127.0.0.1", 8080);
        assertThrows(IllegalStateException.class, () -> client.registerService(service, node));
        assertEquals("ns_test", service.getNamespaceId());
        assertEquals("DEFAULT_GROUP", service.getGroupName());
        assertEquals("ns_test", node.getNamespaceId());
        assertEquals("DEFAULT_GROUP", node.getGroupName());
        assertEquals("test-service", node.getServiceName());
    }

    @Test
    public void registerService_keepsCustomNamespace() {
        client = new ServiceCenterClient(validConfig());
        ServiceInfo service = new ServiceInfo("ns_custom", "custom-group", "test-service");
        NodeInfo node = new NodeInfo("127.0.0.1", 8080);
        node.setNamespaceId("ns_custom");
        node.setGroupName("custom-group");
        assertThrows(IllegalStateException.class, () -> client.registerService(service, node));
        assertEquals("ns_custom", service.getNamespaceId());
        assertEquals("custom-group", service.getGroupName());
    }

    @Test
    public void saveConfig_fillsDefaults() {
        client = new ServiceCenterClient(validConfig());
        ConfigInfo info = new ConfigInfo();
        info.setConfigDataId("config1");
        info.setConfigContent("x");
        assertThrows(IllegalStateException.class, () -> client.saveConfig(info));
        assertEquals("ns_test", info.getNamespaceId());
        assertEquals("DEFAULT_GROUP", info.getGroupName());
    }

    @Test
    public void streamSubclass_constructs() {
        StreamBasedServiceCenterClient stream = new StreamBasedServiceCenterClient(validConfig());
        try {
            assertFalse(stream.isConnected());
        } finally {
            stream.close();
        }
    }

    private static ServiceCenterConfig validConfig() {
        return new ServiceCenterConfig()
                .setServerHost("localhost")
                .setServerPort(12004)
                .setNamespaceId("ns_test")
                .setGroupName("DEFAULT_GROUP");
    }
}
