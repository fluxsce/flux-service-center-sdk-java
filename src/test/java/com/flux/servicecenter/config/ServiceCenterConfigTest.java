package com.flux.servicecenter.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ServiceCenterConfig 测试类
 * 
 * @author shangjian
 */
public class ServiceCenterConfigTest {

    @Test
    public void testDefaultValues() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        
        assertEquals("localhost", config.getServerHost());
        assertEquals(12004, config.getServerPort());
        assertFalse(config.isEnableTls());
        assertNull(config.getAuthToken());
        assertNull(config.getUserId());
        assertNull(config.getPassword());
        assertEquals("ns_F41J68C80A50C28G68A06I53A49J4", config.getNamespaceId());
        assertEquals("DEFAULT_GROUP", config.getGroupName());
        assertEquals(5000L, config.getHeartbeatInterval());
        assertEquals(3000L, config.getReconnectInterval());
        assertEquals(10, config.getMaxReconnectAttempts());
        assertEquals(30000L, config.getRequestTimeout());
        assertNotNull(config.getMetadata());
        assertEquals("localhost:12004", config.getServerAddress());
    }

    @Test
    public void testSetServerHost() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setServerHost("example.com");
        assertEquals("example.com", config.getServerHost());
        assertEquals("example.com:50051", config.getServerAddress());
    }

    @Test
    public void testSetServerHostNull() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setServerHost(null)
        );
    }

    @Test
    public void testSetServerHostEmpty() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setServerHost("")
        );
    }

    @Test
    public void testSetServerHostWhitespace() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setServerHost("   ")
        );
    }

    @Test
    public void testSetServerPort() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setServerPort(8080);
        assertEquals(8080, config.getServerPort());
        assertEquals("localhost:8080", config.getServerAddress());
    }
    
    @Test
    public void testSetUserId() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertNull(config.getUserId());
        
        config.setUserId("admin");
        assertEquals("admin", config.getUserId());
        
        config.setUserId(null);
        assertNull(config.getUserId());
    }
    
    @Test
    public void testSetPassword() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertNull(config.getPassword());
        
        config.setPassword("testpass");
        assertEquals("testpass", config.getPassword());
        
        config.setPassword(null);
        assertNull(config.getPassword());
    }
    
    @Test
    public void testSetNamespaceId() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertEquals("ns_F41J68C80A50C28G68A06I53A49J4", config.getNamespaceId());
        
        config.setNamespaceId("ns_test");
        assertEquals("ns_test", config.getNamespaceId());
    }
    
    @Test
    public void testSetNamespaceIdNull() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setNamespaceId(null)
        );
    }
    
    @Test
    public void testSetNamespaceIdEmpty() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setNamespaceId("")
        );
    }
    
    @Test
    public void testSetNamespaceIdWhitespace() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setNamespaceId("   ")
        );
    }
    
    @Test
    public void testSetGroupName() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertEquals("DEFAULT_GROUP", config.getGroupName());
        
        config.setGroupName("test-group");
        assertEquals("test-group", config.getGroupName());
    }
    
    @Test
    public void testSetGroupNameNull() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setGroupName(null);
        assertEquals("DEFAULT_GROUP", config.getGroupName());
    }
    
    @Test
    public void testSetGroupNameEmpty() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setGroupName("");
        assertEquals("DEFAULT_GROUP", config.getGroupName());
    }
    
    @Test
    public void testSetGroupNameWhitespace() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setGroupName("   ");
        assertEquals("DEFAULT_GROUP", config.getGroupName());
    }

    @Test
    public void testSetServerPortTooLow() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setServerPort(0)
        );
    }

    @Test
    public void testSetServerPortTooHigh() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setServerPort(65536)
        );
    }

    @Test
    public void testSetEnableTls() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setEnableTls(true);
        assertTrue(config.isEnableTls());
        
        config.setEnableTls(false);
        assertFalse(config.isEnableTls());
    }

    @Test
    public void testSetAuthToken() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertNull(config.getAuthToken());
        
        config.setAuthToken("token123");
        assertEquals("token123", config.getAuthToken());
        
        config.setAuthToken(null);
        assertNull(config.getAuthToken());
    }

    @Test
    public void testSetHeartbeatInterval() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setHeartbeatInterval(10000);
        assertEquals(10000L, config.getHeartbeatInterval());
    }

    @Test
    public void testSetHeartbeatIntervalZero() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setHeartbeatInterval(0)
        );
    }

    @Test
    public void testSetHeartbeatIntervalNegative() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setHeartbeatInterval(-1)
        );
    }

    @Test
    public void testSetReconnectInterval() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setReconnectInterval(5000);
        assertEquals(5000L, config.getReconnectInterval());
    }

    @Test
    public void testSetReconnectIntervalZero() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setReconnectInterval(0)
        );
    }

    @Test
    public void testSetMaxReconnectAttempts() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setMaxReconnectAttempts(20);
        assertEquals(20, config.getMaxReconnectAttempts());
        
        config.setMaxReconnectAttempts(-1);
        assertEquals(-1, config.getMaxReconnectAttempts());
    }

    @Test
    public void testSetRequestTimeout() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setRequestTimeout(60000);
        assertEquals(60000L, config.getRequestTimeout());
    }

    @Test
    public void testSetRequestTimeoutZero() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setRequestTimeout(0)
        );
    }

    @Test
    public void testSetMetadata() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", "value2");
        
        config.setMetadata(metadata);
        assertEquals(metadata, config.getMetadata());
        assertEquals("value1", config.getMetadata().get("key1"));
    }

    @Test
    public void testSetMetadataNull() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setMetadata(null);
        assertNotNull(config.getMetadata());
        assertTrue(config.getMetadata().isEmpty());
    }

    @Test
    public void testChainedCalls() {
        ServiceCenterConfig config = new ServiceCenterConfig()
                .setServerHost("test.com")
                .setServerPort(9090)
                .setEnableTls(true)
                .setAuthToken("token")
                .setUserId("admin")
                .setPassword("pass")
                .setNamespaceId("ns_test")
                .setGroupName("test-group")
                .setHeartbeatInterval(10000)
                .setReconnectInterval(5000)
                .setMaxReconnectAttempts(5)
                .setRequestTimeout(60000);
        
        assertEquals("test.com", config.getServerHost());
        assertEquals(9090, config.getServerPort());
        assertTrue(config.isEnableTls());
        assertEquals("token", config.getAuthToken());
        assertEquals("admin", config.getUserId());
        assertEquals("pass", config.getPassword());
        assertEquals("ns_test", config.getNamespaceId());
        assertEquals("test-group", config.getGroupName());
        assertEquals(10000L, config.getHeartbeatInterval());
        assertEquals(5000L, config.getReconnectInterval());
        assertEquals(5, config.getMaxReconnectAttempts());
        assertEquals(60000L, config.getRequestTimeout());
    }
    
    @Test
    public void testUserIdPasswordAuth() {
        ServiceCenterConfig config = new ServiceCenterConfig()
                .setUserId("admin")
                .setPassword("password123");
        
        assertEquals("admin", config.getUserId());
        assertEquals("password123", config.getPassword());
    }
    
    @Test
    public void testNamespaceIdAndGroupName() {
        ServiceCenterConfig config = new ServiceCenterConfig()
                .setNamespaceId("ns_custom")
                .setGroupName("custom-group");
        
        assertEquals("ns_custom", config.getNamespaceId());
        assertEquals("custom-group", config.getGroupName());
    }
    
    @Test
    public void testSetServerAddress_Single() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setServerAddress("localhost:12004");
        assertEquals("localhost:12004", config.getServerAddress());
        
        List<String> addresses = config.getServerAddresses();
        assertEquals(1, addresses.size());
        assertEquals("localhost:12004", addresses.get(0));
    }
    
    @Test
    public void testSetServerAddress_Cluster() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setServerAddress("localhost:12004,192.168.1.1:12004,192.168.1.2:12004");
        assertEquals("localhost:12004,192.168.1.1:12004,192.168.1.2:12004", config.getServerAddress());
        
        List<String> addresses = config.getServerAddresses();
        assertEquals(3, addresses.size());
        assertEquals("localhost:12004", addresses.get(0));
        assertEquals("192.168.1.1:12004", addresses.get(1));
        assertEquals("192.168.1.2:12004", addresses.get(2));
    }
    
    @Test
    public void testSetServerAddress_WithSpaces() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setServerAddress("localhost:12004, 192.168.1.1:12004 , 192.168.1.2:12004");
        
        List<String> addresses = config.getServerAddresses();
        assertEquals(3, addresses.size());
        assertEquals("localhost:12004", addresses.get(0));
        assertEquals("192.168.1.1:12004", addresses.get(1));
        assertEquals("192.168.1.2:12004", addresses.get(2));
    }
    
    @Test
    public void testSetServerAddress_Null() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setServerAddress("localhost:12004");
        assertEquals("localhost:12004", config.getServerAddress());
        
        config.setServerAddress(null);
        assertEquals("localhost:12004", config.getServerAddress()); // 回退到 serverHost:serverPort
    }
    
    @Test
    public void testSetServerAddress_Empty() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        config.setServerAddress("localhost:12004");
        assertEquals("localhost:12004", config.getServerAddress());
        
        config.setServerAddress("");
        assertEquals("localhost:12004", config.getServerAddress()); // 回退到 serverHost:serverPort
    }
    
    @Test
    public void testSetServerAddress_InvalidFormat_NoPort() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setServerAddress("localhost")
        );
    }
    
    @Test
    public void testSetServerAddress_InvalidFormat_NoHost() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setServerAddress(":12004")
        );
    }
    
    @Test
    public void testSetServerAddress_InvalidFormat_InvalidPort() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setServerAddress("localhost:abc")
        );
    }
    
    @Test
    public void testSetServerAddress_InvalidFormat_EmptyItem() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        assertThrows(IllegalArgumentException.class, () -> 
            config.setServerAddress("localhost:12004,,192.168.1.1:12004")
        );
    }
    
    @Test
    public void testGetServerAddress_Priority() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        // 默认使用 serverHost:serverPort
        assertEquals("localhost:12004", config.getServerAddress());
        
        // 设置 serverAddress 后，优先使用 serverAddress
        config.setServerAddress("custom-host:9090");
        assertEquals("custom-host:9090", config.getServerAddress());
        
        // 即使修改了 serverHost 和 serverPort，仍然使用 serverAddress
        config.setServerHost("other-host");
        config.setServerPort(8080);
        assertEquals("custom-host:9090", config.getServerAddress());
    }
    
    @Test
    public void testGetServerAddresses_Default() {
        ServiceCenterConfig config = new ServiceCenterConfig();
        List<String> addresses = config.getServerAddresses();
        assertEquals(1, addresses.size());
        assertEquals("localhost:12004", addresses.get(0));
    }
}

