package com.flux.servicecenter.client.internal;

import com.flux.servicecenter.config.ConfigProto;
import com.flux.servicecenter.model.ConfigChangeEvent;
import com.flux.servicecenter.model.NodeInfo;
import com.flux.servicecenter.model.ServiceChangeEvent;
import com.flux.servicecenter.registry.RegistryProto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConvertersTest {

    @Test
    public void mapsNodeEventsToPublicTypes() {
        assertEquals(ServiceChangeEvent.EventType.NODE_ADDED,
                Converters.mapNaming(RegistryProto.NamingEventType.NAMING_EVENT_NODE_REGISTERED));
        assertEquals(ServiceChangeEvent.EventType.NODE_UPDATED,
                Converters.mapNaming(RegistryProto.NamingEventType.NAMING_EVENT_NODE_UPDATED));
        assertEquals(ServiceChangeEvent.EventType.NODE_REMOVED,
                Converters.mapNaming(RegistryProto.NamingEventType.NAMING_EVENT_NODE_DEREGISTERED));
        assertEquals(ServiceChangeEvent.EventType.NODE_REMOVED,
                Converters.mapNaming(RegistryProto.NamingEventType.NAMING_EVENT_NODE_EVICTED));
        assertEquals(ServiceChangeEvent.EventType.SERVICE_ADDED,
                Converters.mapNaming(RegistryProto.NamingEventType.NAMING_EVENT_SERVICE_ADDED));
    }

    @Test
    public void mapsPublishedConfigToUpdated() {
        assertEquals(ConfigChangeEvent.EventType.CONFIG_UPDATED,
                Converters.mapConfig(ConfigProto.ConfigEventType.CONFIG_EVENT_PUBLISHED));
        assertEquals(ConfigChangeEvent.EventType.CONFIG_UPDATED,
                Converters.mapConfig(ConfigProto.ConfigEventType.CONFIG_EVENT_ROLLED_BACK));
        assertEquals(ConfigChangeEvent.EventType.CONFIG_DELETED,
                Converters.mapConfig(ConfigProto.ConfigEventType.CONFIG_EVENT_DELETED));
    }

    @Test
    public void serviceChangeKeepsNodesAlias() {
        RegistryProto.ServiceChangeEvent proto = RegistryProto.ServiceChangeEvent.newBuilder()
                .setEventType(RegistryProto.NamingEventType.NAMING_EVENT_NODE_REGISTERED)
                .setTimestamp(1_700_000_000_000L)
                .setNamespaceId("ns")
                .setGroupName("g")
                .setServiceName("svc")
                .addNodes(RegistryProto.Node.newBuilder()
                        .setIpAddress("10.0.0.1")
                        .setPortNumber(80)
                        .setEphemeral(true)
                        .build())
                .build();
        ServiceChangeEvent event = Converters.toModel(proto);
        assertEquals(ServiceChangeEvent.EventType.NODE_ADDED, event.getEventType());
        assertEquals("1700000000000", event.getTimestamp());
        assertNotNull(event.getNodes());
        assertEquals(1, event.getNodes().size());
        assertEquals(event.getAllNodes(), event.getNodes());
        assertEquals("Y", event.getNodes().get(0).getEphemeral());
    }

    @Test
    public void nodeEphemeralDefaultsToTemporary() {
        assertTrue(Converters.isEphemeral(null));
        assertTrue(Converters.isEphemeral(""));
        assertTrue(Converters.isEphemeral("Y"));
        assertFalse(Converters.isEphemeral("N"));
        NodeInfo info = new NodeInfo("10.0.0.1", 80);
        info.setEphemeral("N");
        RegistryProto.Node proto = Converters.toProto(info);
        assertTrue(proto.hasEphemeral());
        assertFalse(proto.getEphemeral());
    }

    @Test
    public void configHistoryIdIsVersion() {
        ConfigProto.ConfigHistory proto = ConfigProto.ConfigHistory.newBuilder()
                .setConfigHistoryId(9)
                .setConfigVersion(3)
                .setChangeTime(1_700_000_000_000L)
                .build();
        assertEquals("3", Converters.toHistory(proto).getHistoryId());
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L).toString(), Converters.toHistory(proto).getChangeTime());
    }
}
