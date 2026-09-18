package com.flux.servicecenter.client;

import com.flux.servicecenter.client.internal.Converters;
import com.flux.servicecenter.client.internal.ServiceCenterException;
import com.flux.servicecenter.client.internal.StreamSession;
import com.flux.servicecenter.config.ConfigProto;
import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.listener.ServiceChangeListener;
import com.flux.servicecenter.model.ConfigChangeEvent;
import com.flux.servicecenter.model.ConfigHistory;
import com.flux.servicecenter.model.ConfigInfo;
import com.flux.servicecenter.model.GetConfigResult;
import com.flux.servicecenter.model.GetServiceResult;
import com.flux.servicecenter.model.NodeInfo;
import com.flux.servicecenter.model.OperationResult;
import com.flux.servicecenter.model.RegisterNodeResult;
import com.flux.servicecenter.model.RegisterServiceResult;
import com.flux.servicecenter.model.RollbackConfigResult;
import com.flux.servicecenter.model.SaveConfigResult;
import com.flux.servicecenter.model.ServiceChangeEvent;
import com.flux.servicecenter.model.ServiceInfo;
import com.flux.servicecenter.registry.RegistryProto;
import com.flux.servicecenter.stream.StreamProto;
import com.flux.servicecenter.stream.StreamProto.ClientMessage;
import com.flux.servicecenter.stream.StreamProto.ClientMessageType;
import com.flux.servicecenter.stream.StreamProto.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service Center 3.0 client. All RPCs go through {@code ServiceCenterStream.Connect}.
 *
 * <p>Public methods stay compatible with 2.x so callers can upgrade the dependency only.</p>
 *
 * <p><b>Cluster / HA:</b> set {@code serverAddress} to {@code host1:port,host2:port,host3:port}.
 * The client holds one stream at a time, fails over to the next address after a failed
 * handshake or a dropped stream, then restores registrations, subscriptions and watches.
 * This is client-side failover, not a replicated data-plane write to every peer.</p>
 *
 * <p><b>Events:</b> after subscribe and after reconnect the client rediscovers current nodes
 * so a missed push cannot leave a stale local view. Keep {@code onServiceChange} non-blocking.</p>
 */
public class ServiceCenterClient implements IServiceCenterClient {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceCenterClient.class);

    private final ServiceCenterConfig config;
    private final StreamSession session;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService listenerPool;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private final Map<String, NodeInfo> registeredNodes = new ConcurrentHashMap<>();
    private final Map<String, ServiceInfo> nodeServices = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private final Map<String, ServiceSub> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, ConfigWatch> watches = new ConcurrentHashMap<>();

    private volatile Throwable lastError;
    private ScheduledFuture<?> pingTask;
    private ScheduledFuture<?> reconnectTask;

    public ServiceCenterClient(ServiceCenterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        if (config.getServerHost() == null || config.getServerHost().isBlank()) {
            throw new IllegalArgumentException("Server host must not be empty");
        }
        if (config.getServerPort() < 1 || config.getServerPort() > 65535) {
            throw new IllegalArgumentException("Server port must be between 1 and 65535");
        }
        if (config.getHeartbeatInterval() <= 0) {
            throw new IllegalArgumentException("heartbeatInterval must be greater than 0");
        }
        if (config.getReconnectInterval() <= 0) {
            throw new IllegalArgumentException("reconnectInterval must be greater than 0");
        }
        if (config.getRequestTimeout() <= 0) {
            throw new IllegalArgumentException("requestTimeout must be greater than 0");
        }
        this.config = config;
        this.session = new StreamSession(config, this::onServiceChange, this::onConfigChange, this::onStreamLost);
        this.scheduler = Executors.newScheduledThreadPool(2, r -> daemon("sc-v3-sched", r));
        this.listenerPool = Executors.newCachedThreadPool(r -> daemon("sc-v3-listener", r));
    }

    /**
     * Opens the stream. First call tries every configured address once and throws if all fail.
     * After a successful session drops, {@link #scheduleReconnect()} retries with failover.
     */
    @Override
    public synchronized void connect() {
        ensureOpen();
        boolean resume = lastError != null || reconnectAttempts.get() > 0;
        session.connect();
        reconnectAttempts.set(0);
        startPing();
        restoreState();
        if (resume) {
            notifyReconnected();
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cancelTask(reconnectTask);
        cancelTask(pingTask);
        heartbeats.values().forEach(ServiceCenterClient::cancelTask);
        heartbeats.clear();
        if (session.isOpen()) {
            try {
                for (String nodeId : new ArrayList<>(registeredNodes.keySet())) {
                    unregisterNodeQuiet(nodeId);
                }
                if (!subscriptions.isEmpty()) {
                    sendEmpty(ClientMessageType.CLIENT_UNSUBSCRIBE);
                }
                if (!watches.isEmpty()) {
                    sendEmpty(ClientMessageType.CLIENT_UNWATCH_CONFIG);
                }
            } catch (RuntimeException e) {
                LOG.debug("close cleanup: {}", e.getMessage());
            }
        }
        subscriptions.clear();
        watches.clear();
        registeredNodes.clear();
        nodeServices.clear();
        session.close();
        scheduler.shutdownNow();
        listenerPool.shutdownNow();
    }

    @Override
    public boolean isConnected() {
        return !closed.get() && session.isOpen();
    }

    @Override
    public boolean checkHealth() {
        if (!isConnected()) {
            return false;
        }
        try {
            session.sendPing();
            return true;
        } catch (RuntimeException e) {
            lastError = e;
            return false;
        }
    }

    /**
     * Last stream error, or {@code null} if none.
     */
    public Throwable getLastError() {
        return lastError;
    }

    @Override
    public RegisterServiceResult registerService(ServiceInfo serviceInfo, NodeInfo nodeInfo) {
        Objects.requireNonNull(serviceInfo, "serviceInfo");
        fillService(serviceInfo);
        if (nodeInfo != null) {
            fillNode(nodeInfo, serviceInfo);
        }
        requireConnected();
        if (isBlank(serviceInfo.getServiceName())) {
            throw new IllegalArgumentException("serviceName must not be empty");
        }
        RegistryProto.Node protoNode = nodeInfo == null ? null : Converters.toProto(nodeInfo);
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_REGISTER_SERVICE)
                .setRegisterService(Converters.toProto(serviceInfo, protoNode))
                .build();
        ServerMessage resp = session.request(req);
        RegistryProto.RegisterServiceResponse body = resp.getRegisterService();
        RegisterServiceResult out = new RegisterServiceResult(body.getSuccess(), body.getMessage(), body.getNodeId());
        out.setCode(body.getCode());
        if (out.isSuccess() && nodeInfo != null && notBlank(out.getNodeId())) {
            nodeInfo.setNodeId(out.getNodeId());
            trackNode(out.getNodeId(), nodeInfo, serviceInfo);
        }
        return out;
    }

    @Override
    public OperationResult unregisterService(String namespaceId, String groupName, String serviceName, String nodeId) {
        if (notBlank(nodeId)) {
            return unregisterNode(nodeId);
        }
        requireConnected();
        if (isBlank(serviceName)) {
            throw new IllegalArgumentException("serviceName must not be empty");
        }
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_UNREGISTER_SERVICE)
                .setUnregisterService(RegistryProto.ServiceKey.newBuilder()
                        .setNamespaceId(ns(namespaceId))
                        .setGroupName(group(groupName))
                        .setServiceName(serviceName)
                        .build())
                .build();
        ServerMessage resp = session.request(req);
        return toOp(resp.getUnregisterService());
    }

    @Override
    public RegisterNodeResult registerNode(NodeInfo nodeInfo) {
        Objects.requireNonNull(nodeInfo, "nodeInfo");
        fillNode(nodeInfo, null);
        requireConnected();
        if (isBlank(nodeInfo.getServiceName()) || isBlank(nodeInfo.getIpAddress()) || nodeInfo.getPortNumber() <= 0) {
            throw new IllegalArgumentException("serviceName, ipAddress and portNumber must not be empty");
        }
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_REGISTER_NODE)
                .setRegisterNode(Converters.toProto(nodeInfo))
                .build();
        ServerMessage resp = session.request(req);
        RegistryProto.RegisterNodeResponse body = resp.getRegisterNode();
        RegisterNodeResult out = new RegisterNodeResult(body.getSuccess(), body.getMessage(), body.getNodeId());
        if (out.isSuccess() && notBlank(out.getNodeId())) {
            nodeInfo.setNodeId(out.getNodeId());
            trackNode(out.getNodeId(), nodeInfo, null);
        }
        return out;
    }

    @Override
    public OperationResult unregisterNode(String nodeId) {
        if (isBlank(nodeId)) {
            throw new IllegalArgumentException("nodeId must not be empty");
        }
        requireConnected();
        stopHeartbeat(nodeId);
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_UNREGISTER_NODE)
                .setUnregisterNode(RegistryProto.NodeKey.newBuilder().setNodeId(nodeId).build())
                .build();
        ServerMessage resp = session.request(req);
        registeredNodes.remove(nodeId);
        nodeServices.remove(nodeId);
        return toOp(resp.getUnregisterNode());
    }

    @Override
    public GetServiceResult getService(String namespaceId, String groupName, String serviceName) {
        requireConnected();
        if (isBlank(serviceName)) {
            throw new IllegalArgumentException("serviceName must not be empty");
        }
        try {
            ClientMessage req = session.newRequest(ClientMessageType.CLIENT_GET_SERVICE)
                    .setGetService(RegistryProto.ServiceKey.newBuilder()
                            .setNamespaceId(ns(namespaceId))
                            .setGroupName(group(groupName))
                            .setServiceName(serviceName)
                            .build())
                    .build();
            ServerMessage resp = session.request(req);
            RegistryProto.GetServiceResponse body = resp.getGetService();
            return new GetServiceResult(body.getSuccess(), body.getMessage(),
                    Converters.toModel(body.getService()), Converters.toNodes(body.getNodesList()));
        } catch (ServiceCenterException e) {
            if ("SERVICE_NOT_FOUND".equals(e.getCode())) {
                return new GetServiceResult(false, e.getMessage(), null, List.of());
            }
            throw e;
        }
    }

    @Override
    public OperationResult sendHeartbeat(String nodeId) {
        if (isBlank(nodeId)) {
            throw new IllegalArgumentException("nodeId must not be empty");
        }
        requireConnected();
        return heartbeatOnce(nodeId);
    }

    @Override
    public String subscribeService(String namespaceId, String groupName, String serviceName, ServiceChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        requireConnected();
        if (isBlank(serviceName)) {
            throw new IllegalArgumentException("serviceName must not be empty");
        }
        String ns = ns(namespaceId);
        String grp = group(groupName);
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_SUBSCRIBE_SERVICES)
                .setSubscribeServices(RegistryProto.SubscribeServicesRequest.newBuilder()
                        .setNamespaceId(ns)
                        .setGroupName(grp)
                        .addServiceNames(serviceName)
                        .build())
                .build();
        session.request(req);
        String id = UUID.randomUUID().toString();
        ServiceSub sub = new ServiceSub(id, ns, grp, serviceName, listener);
        subscriptions.put(id, sub);
        replaySubscription(sub);
        return id;
    }

    @Override
    public OperationResult unsubscribe(String subscriptionId) {
        if (isBlank(subscriptionId)) {
            throw new IllegalArgumentException("subscriptionId must not be empty");
        }
        requireConnected();
        subscriptions.remove(subscriptionId);
        refreshSubscriptions();
        return new OperationResult(true, "ok", "OK");
    }

    @Override
    public List<String> getRegisteredNodeIds() {
        return List.copyOf(registeredNodes.keySet());
    }

    @Override
    public List<String> getActiveSubscriptions() {
        return List.copyOf(subscriptions.keySet());
    }

    @Override
    public SaveConfigResult saveConfig(ConfigInfo configInfo) {
        SaveConfigResult draft = saveDraft(configInfo);
        if (!draft.isSuccess()) {
            return draft;
        }
        return publishConfig(configInfo.getNamespaceId(), configInfo.getGroupName(),
                configInfo.getConfigDataId(), configInfo.getChangeReason());
    }

    @Override
    public SaveConfigResult saveDraft(ConfigInfo configInfo) {
        Objects.requireNonNull(configInfo, "config");
        fillConfig(configInfo);
        requireConnected();
        if (isBlank(configInfo.getConfigDataId()) || configInfo.getConfigContent() == null) {
            throw new IllegalArgumentException("configDataId and configContent must not be empty");
        }
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_SAVE_CONFIG)
                .setSaveConfig(Converters.toProto(configInfo))
                .build();
        ServerMessage resp = session.request(req);
        return toSave(resp.getSaveConfig());
    }

    @Override
    public SaveConfigResult publishConfig(String namespaceId, String groupName, String configDataId, String changeReason) {
        requireConnected();
        if (isBlank(configDataId)) {
            throw new IllegalArgumentException("configDataId must not be empty");
        }
        ConfigProto.PublishConfigRequest.Builder b = ConfigProto.PublishConfigRequest.newBuilder()
                .setNamespaceId(ns(namespaceId))
                .setGroupName(group(groupName))
                .setConfigDataId(configDataId);
        if (changeReason != null) {
            b.setChangeReason(changeReason);
        }
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_PUBLISH_CONFIG)
                .setPublishConfig(b.build())
                .build();
        ServerMessage resp = session.request(req);
        return toSave(resp.getPublishConfig());
    }

    @Override
    public GetConfigResult getDraft(String namespaceId, String groupName, String configDataId) {
        requireConnected();
        if (isBlank(configDataId)) {
            throw new IllegalArgumentException("configDataId must not be empty");
        }
        try {
            ClientMessage req = session.newRequest(ClientMessageType.CLIENT_GET_DRAFT)
                    .setGetDraft(configKey(namespaceId, groupName, configDataId))
                    .build();
            ServerMessage resp = session.request(req);
            ConfigProto.GetConfigResponse body = resp.getGetDraft();
            return new GetConfigResult(body.getSuccess(), body.getMessage(), Converters.toModel(body.getConfig()));
        } catch (ServiceCenterException e) {
            if ("CONFIG_NOT_FOUND".equals(e.getCode())) {
                return new GetConfigResult(false, e.getMessage(), null);
            }
            throw e;
        }
    }

    @Override
    public GetConfigResult getConfig(String namespaceId, String groupName, String configDataId) {
        requireConnected();
        if (isBlank(configDataId)) {
            throw new IllegalArgumentException("configDataId must not be empty");
        }
        try {
            ClientMessage req = session.newRequest(ClientMessageType.CLIENT_GET_CONFIG)
                    .setGetConfig(configKey(namespaceId, groupName, configDataId))
                    .build();
            ServerMessage resp = session.request(req);
            ConfigProto.GetConfigResponse body = resp.getGetConfig();
            return new GetConfigResult(body.getSuccess(), body.getMessage(), Converters.toModel(body.getConfig()));
        } catch (ServiceCenterException e) {
            if ("CONFIG_NOT_FOUND".equals(e.getCode())) {
                return new GetConfigResult(false, e.getMessage(), null);
            }
            throw e;
        }
    }

    @Override
    public OperationResult deleteConfig(String namespaceId, String groupName, String configDataId) {
        requireConnected();
        if (isBlank(configDataId)) {
            throw new IllegalArgumentException("configDataId must not be empty");
        }
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_DELETE_CONFIG)
                .setDeleteConfig(configKey(namespaceId, groupName, configDataId))
                .build();
        ServerMessage resp = session.request(req);
        ConfigProto.ConfigResponse body = resp.getDeleteConfig();
        return new OperationResult(body.getSuccess(), body.getMessage(), body.getCode());
    }

    @Override
    public List<ConfigInfo> listConfigs(String namespaceId, String groupName, String searchKey, int pageNum, int pageSize) {
        requireConnected();
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_LIST_CONFIGS)
                .setListConfigs(ConfigProto.ListConfigsRequest.newBuilder()
                        .setNamespaceId(ns(namespaceId))
                        .setGroupName(groupName == null ? "" : groupName)
                        .build())
                .build();
        ServerMessage resp = session.request(req);
        List<ConfigInfo> all = new ArrayList<>();
        for (ConfigProto.ConfigData item : resp.getListConfigs().getConfigsList()) {
            ConfigInfo info = Converters.toModel(item);
            if (matchesSearch(info, searchKey)) {
                all.add(info);
            }
        }
        return page(all, pageNum, pageSize);
    }

    @Override
    public String watchConfig(String namespaceId, String groupName, String configDataId, ConfigChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        requireConnected();
        if (isBlank(configDataId)) {
            throw new IllegalArgumentException("configDataId must not be empty");
        }
        String ns = ns(namespaceId);
        String grp = group(groupName);
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_WATCH_CONFIG)
                .setWatchConfig(ConfigProto.WatchConfigRequest.newBuilder()
                        .setNamespaceId(ns)
                        .setGroupName(grp)
                        .addConfigDataIds(configDataId)
                        .build())
                .build();
        session.request(req);
        String id = UUID.randomUUID().toString();
        watches.put(id, new ConfigWatch(id, ns, grp, configDataId, listener));
        return id;
    }

    @Override
    public OperationResult unwatch(String watchId) {
        if (isBlank(watchId)) {
            throw new IllegalArgumentException("watchId must not be empty");
        }
        requireConnected();
        watches.remove(watchId);
        refreshWatches();
        return new OperationResult(true, "ok", "OK");
    }

    @Override
    public List<ConfigHistory> getConfigHistory(String namespaceId, String groupName, String configDataId, int pageNum, int pageSize) {
        requireConnected();
        if (isBlank(configDataId)) {
            throw new IllegalArgumentException("configDataId must not be empty");
        }
        int limit = pageNum < 1 || pageSize < 1 ? 100 : pageNum * pageSize;
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_GET_CONFIG_HISTORY)
                .setGetConfigHistory(ConfigProto.GetConfigHistoryRequest.newBuilder()
                        .setNamespaceId(ns(namespaceId))
                        .setGroupName(group(groupName))
                        .setConfigDataId(configDataId)
                        .setLimit(limit)
                        .setPageSize(pageSize > 0 ? pageSize : 0)
                        .build())
                .build();
        ServerMessage resp = session.request(req);
        List<ConfigHistory> all = new ArrayList<>();
        for (ConfigProto.ConfigHistory item : resp.getGetConfigHistory().getHistoryList()) {
            all.add(Converters.toHistory(item));
        }
        return page(all, pageNum, pageSize);
    }

    @Override
    public RollbackConfigResult rollbackConfig(String namespaceId, String groupName, String configDataId, String historyId) {
        requireConnected();
        if (isBlank(configDataId) || isBlank(historyId)) {
            throw new IllegalArgumentException("configDataId and historyId must not be empty");
        }
        long version;
        try {
            version = Long.parseLong(historyId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("historyId must be a version number", e);
        }
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_ROLLBACK_CONFIG)
                .setRollbackConfig(ConfigProto.RollbackConfigRequest.newBuilder()
                        .setNamespaceId(ns(namespaceId))
                        .setGroupName(group(groupName))
                        .setConfigDataId(configDataId)
                        .setTargetVersion(version)
                        .setChangeReason("rollback")
                        .build())
                .build();
        ServerMessage resp = session.request(req);
        ConfigProto.RollbackConfigResponse body = resp.getRollbackConfig();
        RollbackConfigResult out = new RollbackConfigResult(body.getSuccess(), body.getMessage(),
                body.getNewVersion(), body.getContentMd5());
        out.setCode(body.getCode());
        return out;
    }

    @Override
    public List<String> getActiveWatches() {
        return List.copyOf(watches.keySet());
    }

    private void trackNode(String nodeId, NodeInfo node, ServiceInfo service) {
        registeredNodes.put(nodeId, copyNode(node));
        if (service != null) {
            nodeServices.put(nodeId, copyService(service));
        }
        startHeartbeat(nodeId);
    }

    private void startHeartbeat(String nodeId) {
        stopHeartbeat(nodeId);
        heartbeats.put(nodeId, scheduler.scheduleAtFixedRate(() -> {
            try {
                heartbeatOnce(nodeId);
            } catch (RuntimeException e) {
                LOG.debug("heartbeat {} failed: {}", nodeId, e.getMessage());
            }
        }, config.getHeartbeatInterval(), config.getHeartbeatInterval(), TimeUnit.MILLISECONDS));
    }

    private void stopHeartbeat(String nodeId) {
        ScheduledFuture<?> task = heartbeats.remove(nodeId);
        cancelTask(task);
    }

    private OperationResult heartbeatOnce(String nodeId) {
        RegistryProto.HeartbeatRequest.Builder hb = RegistryProto.HeartbeatRequest.newBuilder().setNodeId(nodeId);
        NodeInfo node = registeredNodes.get(nodeId);
        ServiceInfo service = nodeServices.get(nodeId);
        if (node != null) {
            hb.setService(Converters.toProto(service, Converters.toProto(node)));
        }
        ClientMessage req = session.newRequest(ClientMessageType.CLIENT_HEARTBEAT)
                .setHeartbeat(hb.build())
                .build();
        ServerMessage resp = session.request(req);
        return toOp(resp.getHeartbeat());
    }

    private void startPing() {
        cancelTask(pingTask);
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                session.sendPing();
            } catch (RuntimeException e) {
                LOG.debug("ping failed: {}", e.getMessage());
            }
        }, config.getHeartbeatInterval(), config.getHeartbeatInterval(), TimeUnit.MILLISECONDS);
    }

    private void onServiceChange(RegistryProto.ServiceChangeEvent proto) {
        ServiceChangeEvent event = Converters.toModel(proto);
        for (ServiceSub sub : subscriptions.values()) {
            if (sub.matches(event)) {
                listenerPool.execute(() -> {
                    try {
                        sub.listener.onServiceChange(event);
                    } catch (RuntimeException e) {
                        LOG.warn("service listener error", e);
                    }
                });
            }
        }
    }

    private void onConfigChange(ConfigProto.ConfigChangeEvent proto) {
        ConfigChangeEvent event = Converters.toModel(proto);
        for (ConfigWatch watch : watches.values()) {
            if (watch.matches(event)) {
                listenerPool.execute(() -> {
                    try {
                        watch.listener.onConfigChange(event);
                    } catch (RuntimeException e) {
                        LOG.warn("config listener error", e);
                    }
                });
            }
        }
    }

    private void onStreamLost(Throwable t) {
        lastError = t;
        if (closed.get()) {
            return;
        }
        notifyDisconnected(t);
        scheduleReconnect();
    }

    private void notifyDisconnected(Throwable t) {
        for (ServiceSub sub : subscriptions.values()) {
            listenerPool.execute(() -> {
                try {
                    sub.listener.onDisconnected(t);
                } catch (RuntimeException e) {
                    LOG.debug("onDisconnected: {}", e.getMessage());
                }
            });
        }
        for (ConfigWatch watch : watches.values()) {
            listenerPool.execute(() -> {
                try {
                    watch.listener.onDisconnected(t);
                } catch (RuntimeException e) {
                    LOG.debug("onDisconnected: {}", e.getMessage());
                }
            });
        }
    }

    private void notifyReconnected() {
        for (ServiceSub sub : subscriptions.values()) {
            listenerPool.execute(() -> {
                try {
                    sub.listener.onReconnected();
                } catch (RuntimeException e) {
                    LOG.debug("onReconnected: {}", e.getMessage());
                }
            });
        }
        for (ConfigWatch watch : watches.values()) {
            listenerPool.execute(() -> {
                try {
                    watch.listener.onReconnected();
                } catch (RuntimeException e) {
                    LOG.debug("onReconnected: {}", e.getMessage());
                }
            });
        }
    }

    private synchronized void scheduleReconnect() {
        if (closed.get() || reconnectTask != null && !reconnectTask.isDone()) {
            return;
        }
        int max = config.getMaxReconnectAttempts();
        int attempt = reconnectAttempts.incrementAndGet();
        if (max >= 0 && attempt > max) {
            LOG.warn("v3 reconnect exhausted after {} attempts", attempt);
            return;
        }
        reconnectTask = scheduler.schedule(() -> {
            try {
                connect();
            } catch (RuntimeException e) {
                lastError = e;
                LOG.warn("v3 reconnect failed: {}", e.getMessage());
                scheduleReconnect();
            }
        }, config.getReconnectInterval(), TimeUnit.MILLISECONDS);
    }

    /** Re-register nodes, restore subscriptions/watches, then rediscover so missed pushes are repaired. */
    private void restoreState() {
        for (Map.Entry<String, NodeInfo> e : registeredNodes.entrySet()) {
            try {
                NodeInfo node = e.getValue();
                session.request(session.newRequest(ClientMessageType.CLIENT_REGISTER_NODE)
                        .setRegisterNode(Converters.toProto(node))
                        .build());
            } catch (RuntimeException ex) {
                LOG.warn("restore node {} failed: {}", e.getKey(), ex.getMessage());
            }
        }
        if (!subscriptions.isEmpty()) {
            refreshSubscriptions();
            for (ServiceSub sub : subscriptions.values()) {
                replaySubscription(sub);
            }
        }
        if (!watches.isEmpty()) {
            refreshWatches();
        }
    }

    private void replaySubscription(ServiceSub sub) {
        try {
            ServerMessage resp = session.request(session.newRequest(ClientMessageType.CLIENT_DISCOVER_NODES)
                    .setDiscoverNodes(RegistryProto.DiscoverNodesRequest.newBuilder()
                            .setNamespaceId(sub.namespaceId)
                            .setGroupName(sub.groupName)
                            .setServiceName(sub.serviceName)
                            .build())
                    .build());
            List<RegistryProto.Node> nodes = resp.getDiscoverNodes().getNodesList();
            if (nodes.isEmpty()) {
                return;
            }
            RegistryProto.ServiceChangeEvent.Builder ev = RegistryProto.ServiceChangeEvent.newBuilder()
                    .setEventType(RegistryProto.NamingEventType.NAMING_EVENT_NODE_REGISTERED)
                    .setTimestamp(System.currentTimeMillis())
                    .setNamespaceId(sub.namespaceId)
                    .setGroupName(sub.groupName)
                    .setServiceName(sub.serviceName)
                    .addAllNodes(nodes)
                    .setChangedNode(nodes.get(0));
            onServiceChange(ev.build());
        } catch (RuntimeException e) {
            LOG.debug("replay {}: {}", sub.serviceName, e.getMessage());
        }
    }

    private void refreshSubscriptions() {
        sendEmpty(ClientMessageType.CLIENT_UNSUBSCRIBE);
        for (ServiceSub sub : subscriptions.values()) {
            session.request(session.newRequest(ClientMessageType.CLIENT_SUBSCRIBE_SERVICES)
                    .setSubscribeServices(RegistryProto.SubscribeServicesRequest.newBuilder()
                            .setNamespaceId(sub.namespaceId)
                            .setGroupName(sub.groupName)
                            .addServiceNames(sub.serviceName)
                            .build())
                    .build());
        }
    }

    private void refreshWatches() {
        sendEmpty(ClientMessageType.CLIENT_UNWATCH_CONFIG);
        for (ConfigWatch watch : watches.values()) {
            session.request(session.newRequest(ClientMessageType.CLIENT_WATCH_CONFIG)
                    .setWatchConfig(ConfigProto.WatchConfigRequest.newBuilder()
                            .setNamespaceId(watch.namespaceId)
                            .setGroupName(watch.groupName)
                            .addConfigDataIds(watch.configDataId)
                            .build())
                    .build());
        }
    }

    private void sendEmpty(ClientMessageType type) {
        ClientMessage.Builder b = session.newRequest(type);
        if (type == ClientMessageType.CLIENT_UNSUBSCRIBE) {
            b.setUnsubscribe(StreamProto.Empty.getDefaultInstance());
        } else if (type == ClientMessageType.CLIENT_UNWATCH_CONFIG) {
            b.setUnwatchConfig(StreamProto.Empty.getDefaultInstance());
        }
        session.request(b.build());
    }

    private void unregisterNodeQuiet(String nodeId) {
        try {
            session.request(session.newRequest(ClientMessageType.CLIENT_UNREGISTER_NODE)
                    .setUnregisterNode(RegistryProto.NodeKey.newBuilder().setNodeId(nodeId).build())
                    .build());
        } catch (RuntimeException e) {
            LOG.debug("unregister {} on close: {}", nodeId, e.getMessage());
        }
        registeredNodes.remove(nodeId);
        nodeServices.remove(nodeId);
    }

    private void fillService(ServiceInfo service) {
        if (isBlank(service.getNamespaceId())) {
            service.setNamespaceId(config.getNamespaceId());
        }
        if (isBlank(service.getGroupName())) {
            service.setGroupName(group(config.getGroupName()));
        }
    }

    private void fillNode(NodeInfo node, ServiceInfo service) {
        if (isBlank(node.getNamespaceId())) {
            node.setNamespaceId(service != null && notBlank(service.getNamespaceId())
                    ? service.getNamespaceId() : config.getNamespaceId());
        }
        if (isBlank(node.getGroupName())) {
            node.setGroupName(service != null && notBlank(service.getGroupName())
                    ? service.getGroupName() : group(config.getGroupName()));
        }
        if (isBlank(node.getServiceName()) && service != null) {
            node.setServiceName(service.getServiceName());
        }
        if (node.getWeight() <= 0) {
            node.setWeight(100);
        }
        if (isBlank(node.getEphemeral())) {
            node.setEphemeral("Y");
        }
        if (isBlank(node.getInstanceStatus())) {
            node.setInstanceStatus("UP");
        }
        if (isBlank(node.getHealthyStatus())) {
            node.setHealthyStatus("HEALTHY");
        }
    }

    private void fillConfig(ConfigInfo info) {
        if (isBlank(info.getNamespaceId())) {
            info.setNamespaceId(config.getNamespaceId());
        }
        if (isBlank(info.getGroupName())) {
            info.setGroupName(group(config.getGroupName()));
        }
    }

    private void requireConnected() {
        ensureOpen();
        if (!session.isOpen()) {
            throw new IllegalStateException("client is not connected");
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("client is closed");
        }
    }

    private String ns(String value) {
        return Converters.ns(value, config.getNamespaceId());
    }

    private String group(String value) {
        return Converters.group(value, config.getGroupName());
    }

    private ConfigProto.ConfigKey configKey(String namespaceId, String groupName, String configDataId) {
        return ConfigProto.ConfigKey.newBuilder()
                .setNamespaceId(ns(namespaceId))
                .setGroupName(group(groupName))
                .setConfigDataId(configDataId)
                .build();
    }

    private static OperationResult toOp(RegistryProto.RegistryResponse body) {
        return new OperationResult(body.getSuccess(), body.getMessage(), body.getCode());
    }

    private static SaveConfigResult toSave(ConfigProto.SaveConfigResponse body) {
        SaveConfigResult out = new SaveConfigResult(body.getSuccess(), body.getMessage(),
                body.getVersion(), body.getContentMd5());
        out.setCode(body.getCode());
        return out;
    }

    private static boolean matchesSearch(ConfigInfo info, String searchKey) {
        if (isBlank(searchKey)) {
            return true;
        }
        String key = searchKey.toLowerCase();
        return contains(info.getConfigDataId(), key) || contains(info.getConfigDesc(), key);
    }

    private static boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }

    private static <T> List<T> page(List<T> all, int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1) {
            return Collections.unmodifiableList(all);
        }
        int from = (pageNum - 1) * pageSize;
        if (from >= all.size()) {
            return List.of();
        }
        return List.copyOf(all.subList(from, Math.min(all.size(), from + pageSize)));
    }

    private static NodeInfo copyNode(NodeInfo in) {
        NodeInfo out = new NodeInfo();
        out.setNodeId(in.getNodeId());
        out.setNamespaceId(in.getNamespaceId());
        out.setGroupName(in.getGroupName());
        out.setServiceName(in.getServiceName());
        out.setIpAddress(in.getIpAddress());
        out.setPortNumber(in.getPortNumber());
        out.setWeight(in.getWeight());
        out.setEphemeral(in.getEphemeral());
        out.setInstanceStatus(in.getInstanceStatus());
        out.setHealthyStatus(in.getHealthyStatus());
        out.setMetadata(in.getMetadata());
        return out;
    }

    private static ServiceInfo copyService(ServiceInfo in) {
        ServiceInfo out = new ServiceInfo();
        out.setNamespaceId(in.getNamespaceId());
        out.setGroupName(in.getGroupName());
        out.setServiceName(in.getServiceName());
        out.setServiceType(in.getServiceType());
        out.setServiceVersion(in.getServiceVersion());
        out.setServiceDescription(in.getServiceDescription());
        out.setProtectThreshold(in.getProtectThreshold());
        out.setMetadata(in.getMetadata());
        out.setTags(in.getTags());
        return out;
    }

    private static Thread daemon(String prefix, Runnable r) {
        Thread t = new Thread(r, prefix + "-" + System.identityHashCode(r));
        t.setDaemon(true);
        return t;
    }

    private static void cancelTask(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean notBlank(String s) {
        return !isBlank(s);
    }

    private static final class ServiceSub {
        final String id;
        final String namespaceId;
        final String groupName;
        final String serviceName;
        final ServiceChangeListener listener;

        ServiceSub(String id, String namespaceId, String groupName, String serviceName, ServiceChangeListener listener) {
            this.id = id;
            this.namespaceId = namespaceId;
            this.groupName = groupName;
            this.serviceName = serviceName;
            this.listener = listener;
        }

        boolean matches(ServiceChangeEvent event) {
            return Objects.equals(namespaceId, event.getNamespaceId())
                    && Objects.equals(groupName, event.getGroupName())
                    && Objects.equals(serviceName, event.getServiceName());
        }
    }

    private static final class ConfigWatch {
        final String id;
        final String namespaceId;
        final String groupName;
        final String configDataId;
        final ConfigChangeListener listener;

        ConfigWatch(String id, String namespaceId, String groupName, String configDataId, ConfigChangeListener listener) {
            this.id = id;
            this.namespaceId = namespaceId;
            this.groupName = groupName;
            this.configDataId = configDataId;
            this.listener = listener;
        }

        boolean matches(ConfigChangeEvent event) {
            return Objects.equals(namespaceId, event.getNamespaceId())
                    && Objects.equals(groupName, event.getGroupName())
                    && Objects.equals(configDataId, event.getConfigDataId());
        }
    }
}
