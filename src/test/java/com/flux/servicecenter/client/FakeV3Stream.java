package com.flux.servicecenter.client;

import com.flux.servicecenter.registry.RegistryProto;
import com.flux.servicecenter.stream.ServiceCenterStreamGrpc;
import com.flux.servicecenter.stream.StreamProto;
import com.flux.servicecenter.stream.StreamProto.ClientMessage;
import com.flux.servicecenter.stream.StreamProto.ServerMessage;
import com.flux.servicecenter.stream.StreamProto.ServerMessageType;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内 v3 流服务：握手、注册、订阅、断连剔除临时节点并通知其它连接。
 */
final class FakeV3Stream extends ServiceCenterStreamGrpc.ServiceCenterStreamImplBase {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, RegistryProto.Node> nodes = new ConcurrentHashMap<>();
    private final Map<String, String> nodeOwner = new ConcurrentHashMap<>();
    private final AtomicInteger handshakes = new AtomicInteger();

    int handshakeCount() {
        return handshakes.get();
    }

    void dropProviders() {
        for (Session session : sessions.values()) {
            if (!session.nodeIds.isEmpty()) {
                session.drop();
            }
        }
    }

    void dropAll() {
        for (Session session : sessions.values()) {
            session.drop();
        }
    }

    @Override
    public StreamObserver<ClientMessage> connect(StreamObserver<ServerMessage> response) {
        Session session = new Session(response);
        return new StreamObserver<>() {
            @Override
            public void onNext(ClientMessage msg) {
                session.onMessage(msg);
            }

            @Override
            public void onError(Throwable t) {
                session.close(false);
            }

            @Override
            public void onCompleted() {
                session.close(false);
            }
        };
    }

    private final class Session {
        private final StreamObserver<ServerMessage> out;
        private final String connectionId = UUID.randomUUID().toString();
        private final CopyOnWriteArrayList<Sub> subs = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> nodeIds = new CopyOnWriteArrayList<>();
        private volatile boolean ready;
        private volatile boolean closed;

        Session(StreamObserver<ServerMessage> out) {
            this.out = out;
        }

        void onMessage(ClientMessage msg) {
            if (closed) {
                return;
            }
            try {
                switch (msg.getMessageType()) {
                    case CLIENT_HANDSHAKE -> handshake(msg);
                    case CLIENT_PING -> pong(msg);
                    case CLIENT_REGISTER_SERVICE -> registerService(msg);
                    case CLIENT_REGISTER_NODE -> registerNode(msg, msg.getRegisterNode());
                    case CLIENT_UNREGISTER_NODE -> unregister(msg, msg.getUnregisterNode().getNodeId(),
                            RegistryProto.NamingEventType.NAMING_EVENT_NODE_DEREGISTERED);
                    case CLIENT_DISCOVER_NODES -> discover(msg);
                    case CLIENT_GET_SERVICE -> getService(msg);
                    case CLIENT_SUBSCRIBE_SERVICES -> subscribe(msg);
                    case CLIENT_UNSUBSCRIBE -> {
                        subs.clear();
                        ack(msg);
                    }
                    case CLIENT_HEARTBEAT -> reply(msg, ServerMessageType.SERVER_HEARTBEAT,
                            ServerMessage.newBuilder()
                                    .setRequestId(msg.getRequestId())
                                    .setMessageType(ServerMessageType.SERVER_HEARTBEAT)
                                    .setHeartbeat(RegistryProto.RegistryResponse.newBuilder().setSuccess(true))
                                    .build());
                    case CLIENT_WATCH_CONFIG, CLIENT_UNWATCH_CONFIG -> ack(msg);
                    case CLIENT_GET_CONFIG, CLIENT_GET_DRAFT -> reply(msg, ServerMessageType.SERVER_ERROR,
                            error(msg, "CONFIG_NOT_FOUND", "not found"));
                    default -> reply(msg, ServerMessageType.SERVER_ERROR, error(msg, "UNSUPPORTED", "unsupported"));
                }
            } catch (RuntimeException e) {
                reply(msg, ServerMessageType.SERVER_ERROR, error(msg, "INTERNAL", e.getMessage()));
            }
        }

        private void handshake(ClientMessage msg) {
            ready = true;
            sessions.put(connectionId, this);
            handshakes.incrementAndGet();
            reply(msg, ServerMessageType.SERVER_HANDSHAKE, ServerMessage.newBuilder()
                    .setRequestId(msg.getRequestId())
                    .setMessageType(ServerMessageType.SERVER_HANDSHAKE)
                    .setHandshake(StreamProto.ServerHandshake.newBuilder()
                            .setSuccess(true)
                            .setConnectionId(connectionId)
                            .setServerTime(System.currentTimeMillis())
                            .setProtocolVersion("v3")
                            .setTenantId("t1")
                            .build())
                    .build());
        }

        private void pong(ClientMessage msg) {
            requireReady();
            reply(msg, ServerMessageType.SERVER_PONG, ServerMessage.newBuilder()
                    .setRequestId(msg.getRequestId())
                    .setMessageType(ServerMessageType.SERVER_PONG)
                    .setPong(StreamProto.ServerPong.newBuilder()
                            .setTimestamp(System.currentTimeMillis())
                            .setClientTimestamp(msg.getPing().getTimestamp())
                            .build())
                    .build());
        }

        private void registerService(ClientMessage msg) {
            requireReady();
            RegistryProto.Service in = msg.getRegisterService();
            String nodeId = "";
            if (in.hasNode()) {
                nodeId = registerNode(msg, in.getNode().toBuilder()
                        .setNamespaceId(blankTo(in.getNode().getNamespaceId(), in.getNamespaceId()))
                        .setGroupName(blankTo(in.getNode().getGroupName(), in.getGroupName()))
                        .setServiceName(blankTo(in.getNode().getServiceName(), in.getServiceName()))
                        .build());
            }
            reply(msg, ServerMessageType.SERVER_REGISTER_SERVICE, ServerMessage.newBuilder()
                    .setRequestId(msg.getRequestId())
                    .setMessageType(ServerMessageType.SERVER_REGISTER_SERVICE)
                    .setRegisterService(RegistryProto.RegisterServiceResponse.newBuilder()
                            .setSuccess(true)
                            .setNodeId(nodeId))
                    .build());
        }

        private String registerNode(ClientMessage msg, RegistryProto.Node in) {
            requireReady();
            String nodeId = in.getNodeId().isBlank() ? UUID.randomUUID().toString() : in.getNodeId();
            RegistryProto.Node stored = in.toBuilder().setNodeId(nodeId).build();
            nodes.put(nodeId, stored);
            nodeOwner.put(nodeId, connectionId);
            nodeIds.add(nodeId);
            if (msg.getMessageType() == StreamProto.ClientMessageType.CLIENT_REGISTER_NODE) {
                reply(msg, ServerMessageType.SERVER_REGISTER_NODE, ServerMessage.newBuilder()
                        .setRequestId(msg.getRequestId())
                        .setMessageType(ServerMessageType.SERVER_REGISTER_NODE)
                        .setRegisterNode(RegistryProto.RegisterNodeResponse.newBuilder()
                                .setSuccess(true)
                                .setNodeId(nodeId))
                        .build());
            }
            push(stored, RegistryProto.NamingEventType.NAMING_EVENT_NODE_REGISTERED);
            return nodeId;
        }

        private void unregister(ClientMessage msg, String nodeId, RegistryProto.NamingEventType type) {
            requireReady();
            RegistryProto.Node node = nodes.remove(nodeId);
            nodeOwner.remove(nodeId);
            nodeIds.remove(nodeId);
            if (node != null) {
                push(node, type);
            }
            reply(msg, ServerMessageType.SERVER_UNREGISTER_NODE, ServerMessage.newBuilder()
                    .setRequestId(msg.getRequestId())
                    .setMessageType(ServerMessageType.SERVER_UNREGISTER_NODE)
                    .setUnregisterNode(RegistryProto.RegistryResponse.newBuilder().setSuccess(true))
                    .build());
        }

        private void discover(ClientMessage msg) {
            requireReady();
            RegistryProto.DiscoverNodesRequest in = msg.getDiscoverNodes();
            java.util.ArrayList<RegistryProto.Node> list = new java.util.ArrayList<>();
            for (RegistryProto.Node node : nodes.values()) {
                if (in.getNamespaceId().isEmpty() || in.getNamespaceId().equals(node.getNamespaceId())) {
                    if (in.getGroupName().isEmpty() || in.getGroupName().equals(node.getGroupName())) {
                        if (in.getServiceName().isEmpty() || in.getServiceName().equals(node.getServiceName())) {
                            list.add(node);
                        }
                    }
                }
            }
            reply(msg, ServerMessageType.SERVER_DISCOVER_NODES, ServerMessage.newBuilder()
                    .setRequestId(msg.getRequestId())
                    .setMessageType(ServerMessageType.SERVER_DISCOVER_NODES)
                    .setDiscoverNodes(RegistryProto.DiscoverNodesResponse.newBuilder()
                            .setSuccess(true)
                            .addAllNodes(list))
                    .build());
        }

        private void getService(ClientMessage msg) {
            requireReady();
            RegistryProto.ServiceKey in = msg.getGetService();
            java.util.ArrayList<RegistryProto.Node> list = new java.util.ArrayList<>();
            for (RegistryProto.Node node : nodes.values()) {
                if (in.getNamespaceId().equals(node.getNamespaceId())
                        && in.getGroupName().equals(node.getGroupName())
                        && in.getServiceName().equals(node.getServiceName())) {
                    list.add(node);
                }
            }
            reply(msg, ServerMessageType.SERVER_GET_SERVICE, ServerMessage.newBuilder()
                    .setRequestId(msg.getRequestId())
                    .setMessageType(ServerMessageType.SERVER_GET_SERVICE)
                    .setGetService(RegistryProto.GetServiceResponse.newBuilder()
                            .setSuccess(true)
                            .setService(RegistryProto.Service.newBuilder()
                                    .setNamespaceId(in.getNamespaceId())
                                    .setGroupName(in.getGroupName())
                                    .setServiceName(in.getServiceName()))
                            .addAllNodes(list))
                    .build());
        }

        private void subscribe(ClientMessage msg) {
            requireReady();
            RegistryProto.SubscribeServicesRequest in = msg.getSubscribeServices();
            subs.add(new Sub(in.getNamespaceId(), in.getGroupName(), in.getServiceNamesList()));
            ack(msg);
        }

        private void requireReady() {
            if (!ready) {
                throw new IllegalStateException("handshake required");
            }
        }

        private void ack(ClientMessage msg) {
            reply(msg, ServerMessageType.SERVER_ACK, ServerMessage.newBuilder()
                    .setRequestId(msg.getRequestId())
                    .setMessageType(ServerMessageType.SERVER_ACK)
                    .setAck(StreamProto.Ack.newBuilder().setSuccess(true).setCode("OK"))
                    .build());
        }

        private void drop() {
            try {
                out.onError(Status.UNAVAILABLE.withDescription("dropped").asRuntimeException());
            } catch (RuntimeException ignored) {
            }
            close(true);
        }

        private void close(boolean dropped) {
            if (closed) {
                return;
            }
            closed = true;
            sessions.remove(connectionId, this);
            for (String nodeId : nodeIds) {
                RegistryProto.Node node = nodes.remove(nodeId);
                nodeOwner.remove(nodeId);
                if (node != null) {
                    push(node, RegistryProto.NamingEventType.NAMING_EVENT_NODE_EVICTED);
                }
            }
            nodeIds.clear();
            if (!dropped) {
                try {
                    out.onCompleted();
                } catch (RuntimeException ignored) {
                }
            }
        }

        private synchronized void reply(ClientMessage req, ServerMessageType type, ServerMessage msg) {
            if (closed) {
                return;
            }
            out.onNext(msg);
        }

        private void push(RegistryProto.Node node, RegistryProto.NamingEventType type) {
            ServerMessage event = ServerMessage.newBuilder()
                    .setMessageType(ServerMessageType.SERVER_SERVICE_CHANGE)
                    .setServiceChange(RegistryProto.ServiceChangeEvent.newBuilder()
                            .setEventType(type)
                            .setTimestamp(System.currentTimeMillis())
                            .setNamespaceId(node.getNamespaceId())
                            .setGroupName(node.getGroupName())
                            .setServiceName(node.getServiceName())
                            .setChangedNode(node)
                            .addNodes(node)
                            .build())
                    .build();
            for (Session session : sessions.values()) {
                if (session.closed) {
                    continue;
                }
                for (Sub sub : session.subs) {
                    if (sub.matches(node)) {
                        session.emit(event);
                        break;
                    }
                }
            }
        }

        private synchronized void emit(ServerMessage msg) {
            if (closed) {
                return;
            }
            try {
                out.onNext(msg);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static ServerMessage error(ClientMessage msg, String code, String message) {
        return ServerMessage.newBuilder()
                .setRequestId(msg.getRequestId())
                .setMessageType(ServerMessageType.SERVER_ERROR)
                .setError(StreamProto.ErrorResponse.newBuilder().setCode(code).setMessage(message == null ? "" : message))
                .build();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Sub(String namespaceId, String groupName, java.util.List<String> serviceNames) {
        boolean matches(RegistryProto.Node node) {
            if (!namespaceId.isEmpty() && !namespaceId.equals(node.getNamespaceId())) {
                return false;
            }
            if (!groupName.isEmpty() && !groupName.equals(node.getGroupName())) {
                return false;
            }
            return serviceNames == null || serviceNames.isEmpty() || serviceNames.contains(node.getServiceName());
        }
    }
}
