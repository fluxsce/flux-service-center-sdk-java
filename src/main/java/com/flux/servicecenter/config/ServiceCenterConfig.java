package com.flux.servicecenter.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务中心客户端配置类
 * 
 * <p>用于配置 Flux Service Center 客户端的行为，包括服务器地址、连接参数、超时设置等。
 * 所有配置项都提供了默认值，可以根据实际需求进行修改。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * ServiceCenterConfig config = new ServiceCenterConfig()
 *     .setServerHost("localhost")
 *     .setServerPort(50051)
 *     .setEnableTls(false)
 *     .setHeartbeatInterval(5000)
 *     .setRequestTimeout(30000);
 * }</pre>
 * 
 * @author shangjian
 * @version 1.0.0
 */
public class ServiceCenterConfig {
    
    /** 服务器地址，默认 localhost */
    private String serverHost = "localhost";
    
    /** 服务器端口，默认 12004 */
    private int serverPort = 12004;
    
    /** 
     * 服务器地址（完整地址），支持单个或多个地址（逗号分隔）
     * 例如：单个地址 "localhost:12004" 或集群地址 "localhost:12004,192.168.1.1:12004,192.168.1.2:12004"
     * 如果设置了此字段，将优先使用此字段；否则使用 serverHost:serverPort
     */
    private String serverAddress;
    
    /** 是否启用 TLS 加密，默认 false */
    private boolean enableTls = false;
    
    /** TLS CA 证书路径（用于验证服务端证书），可选，不设置则使用系统默认信任证书 */
    private String tlsCaPath;
    
    /** TLS 客户端证书路径（用于双向 TLS 认证），可选 */
    private String tlsCertPath;
    
    /** TLS 客户端私钥路径（用于双向 TLS 认证），可选 */
    private String tlsKeyPath;
    
    /** 认证令牌，用于身份验证（可选，与用户ID密码二选一） */
    private String authToken;
    
    /** 用户ID，用于用户ID密码认证（可选，与 authToken 二选一）
     * 注意：这里应该填写用户ID（userId），而不是用户名（userName）
     * userId 是数据库中的唯一标识 */
    private String userId;
    
    /** 密码，用于用户ID密码认证（可选，与 authToken 二选一） */
    private String password;
    
    /** 命名空间ID，默认 ns_F41J68C80A50C28G68A06I53A49J4 */
    private String namespaceId = "ns_F41J68C80A50C28G68A06I53A49J4";
    
    /** 分组名，默认 DEFAULT_GROUP */
    private String groupName = "DEFAULT_GROUP";
    
    /** 心跳间隔（毫秒），默认 5000 毫秒（5秒） */
    private long heartbeatInterval = 5000;
    
    /** 重连间隔（毫秒），默认 3000 毫秒（3秒） */
    private long reconnectInterval = 3000;
    
    /** 最大重连次数，默认 10 次，-1 表示无限重连 */
    private int maxReconnectAttempts = 10;
    
    /** 请求超时时间（毫秒），默认 30000 毫秒（30秒） */
    private long requestTimeout = 30000;
    
    /** gRPC Keep-Alive 时间间隔（毫秒），默认 30000 毫秒（30秒） */
    private long keepAliveTime = 30000;
    
    /** gRPC Keep-Alive 超时时间（毫秒），默认 10000 毫秒（10秒） */
    private long keepAliveTimeout = 10000;
    
    /** gRPC Keep-Alive 是否在没有调用时也发送心跳，默认 true */
    private boolean keepAliveWithoutCalls = true;
    
    /** gRPC 最大入站消息大小（字节），默认 16MB */
    private int maxInboundMessageSize = 16 * 1024 * 1024;
    
    /** 客户端元数据，用于存储额外的客户端信息（可选） */
    private Map<String, String> metadata = new HashMap<>();

    /**
     * 获取服务器地址
     * 
     * @return 服务器地址，默认 "localhost"
     */
    public String getServerHost() {
        return serverHost;
    }

    /**
     * 设置服务器地址
     * 
     * @param serverHost 服务器地址，不能为 null
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setServerHost(String serverHost) {
        if (serverHost == null || serverHost.trim().isEmpty()) {
            throw new IllegalArgumentException("服务器地址不能为空");
        }
        this.serverHost = serverHost;
        return this;
    }

    /**
     * 获取服务器端口
     * 
     * @return 服务器端口，默认 12004
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * 设置服务器端口
     * 
     * @param serverPort 服务器端口，范围 1-65535
     * @return 当前配置对象，支持链式调用
     * @throws IllegalArgumentException 如果端口号不在有效范围内
     */
    public ServiceCenterConfig setServerPort(int serverPort) {
        if (serverPort < 1 || serverPort > 65535) {
            throw new IllegalArgumentException("端口号必须在 1-65535 范围内");
        }
        this.serverPort = serverPort;
        return this;
    }

    /**
     * 是否启用 TLS 加密
     * 
     * @return true 如果启用 TLS，false 否则，默认 false
     */
    public boolean isEnableTls() {
        return enableTls;
    }

    /**
     * 设置是否启用 TLS 加密
     * 
     * <p>启用 TLS 后，客户端与服务端的通信将使用加密传输，提高安全性。
     * 注意：启用 TLS 需要服务端也配置相应的证书。</p>
     * 
     * @param enableTls true 启用 TLS，false 禁用 TLS
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setEnableTls(boolean enableTls) {
        this.enableTls = enableTls;
        return this;
    }

    /**
     * 获取 TLS CA 证书路径
     * 
     * @return TLS CA 证书路径，用于验证服务端证书（可选）
     */
    public String getTlsCaPath() {
        return tlsCaPath;
    }

    /**
     * 设置 TLS CA 证书路径
     * 
     * <p>用于验证服务端证书。如果不设置，则使用系统默认的信任证书。
     * 适用于服务端使用自签名证书的情况。</p>
     * 
     * @param tlsCaPath TLS CA 证书文件路径（PEM 格式）
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setTlsCaPath(String tlsCaPath) {
        this.tlsCaPath = tlsCaPath;
        return this;
    }

    /**
     * 获取 TLS 客户端证书路径
     * 
     * @return TLS 客户端证书路径（可选）
     */
    public String getTlsCertPath() {
        return tlsCertPath;
    }

    /**
     * 设置 TLS 客户端证书路径
     * 
     * <p>用于双向 TLS 认证（mTLS）。如果服务端要求客户端提供证书，则需要设置此项。
     * 必须与 tlsKeyPath 一起使用。</p>
     * 
     * @param tlsCertPath TLS 客户端证书文件路径（PEM 格式）
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setTlsCertPath(String tlsCertPath) {
        this.tlsCertPath = tlsCertPath;
        return this;
    }

    /**
     * 获取 TLS 客户端私钥路径
     * 
     * @return TLS 客户端私钥路径（可选）
     */
    public String getTlsKeyPath() {
        return tlsKeyPath;
    }

    /**
     * 设置 TLS 客户端私钥路径
     * 
     * <p>用于双向 TLS 认证（mTLS）。如果服务端要求客户端提供证书，则需要设置此项。
     * 必须与 tlsCertPath 一起使用。</p>
     * 
     * @param tlsKeyPath TLS 客户端私钥文件路径（PEM 格式）
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setTlsKeyPath(String tlsKeyPath) {
        this.tlsKeyPath = tlsKeyPath;
        return this;
    }

    /**
     * 获取认证令牌
     * 
     * @return 认证令牌，如果未设置则返回 null
     */
    public String getAuthToken() {
        return authToken;
    }

    /**
     * 设置认证令牌
     * 
     * <p>用于身份验证，如果服务端启用了认证功能，需要提供有效的令牌。
     * 令牌会通过 gRPC metadata 的 authorization 头传递给服务端。</p>
     * 
     * @param authToken 认证令牌，可以为 null（表示不使用认证）
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setAuthToken(String authToken) {
        this.authToken = authToken;
        return this;
    }

    /**
     * 获取心跳间隔
     * 
     * @return 心跳间隔（毫秒），默认 5000 毫秒（5秒）
     */
    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * 设置心跳间隔
     * 
     * <p>心跳用于保持节点在线状态。注册节点后，客户端会按照此间隔自动发送心跳。
     * 如果节点在指定时间内未收到心跳，服务端可能会将其标记为不健康或删除。</p>
     * 
     * <p>建议值：5000-10000 毫秒（5-10秒）。过短会增加网络负担，过长可能导致节点状态更新不及时。</p>
     * 
     * @param heartbeatInterval 心跳间隔（毫秒），必须大于 0
     * @return 当前配置对象，支持链式调用
     * @throws IllegalArgumentException 如果间隔小于等于 0
     */
    public ServiceCenterConfig setHeartbeatInterval(long heartbeatInterval) {
        if (heartbeatInterval <= 0) {
            throw new IllegalArgumentException("心跳间隔必须大于 0");
        }
        this.heartbeatInterval = heartbeatInterval;
        return this;
    }

    /**
     * 获取重连间隔
     * 
     * @return 重连间隔（毫秒），默认 3000 毫秒（3秒）
     */
    public long getReconnectInterval() {
        return reconnectInterval;
    }

    /**
     * 设置重连间隔
     * 
     * <p>当订阅连接断开时，客户端会按照此间隔尝试重新连接。
     * 重连会持续进行，直到达到最大重连次数或连接成功。</p>
     * 
     * <p>建议值：3000-10000 毫秒（3-10秒）。过短可能会在服务端未恢复时频繁重连，过长会导致恢复延迟。</p>
     * 
     * @param reconnectInterval 重连间隔（毫秒），必须大于 0
     * @return 当前配置对象，支持链式调用
     * @throws IllegalArgumentException 如果间隔小于等于 0
     */
    public ServiceCenterConfig setReconnectInterval(long reconnectInterval) {
        if (reconnectInterval <= 0) {
            throw new IllegalArgumentException("重连间隔必须大于 0");
        }
        this.reconnectInterval = reconnectInterval;
        return this;
    }

    /**
     * 获取最大重连次数
     * 
     * @return 最大重连次数，默认 10 次，-1 表示无限重连
     */
    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    /**
     * 设置最大重连次数
     * 
     * <p>当订阅连接断开时，客户端会尝试重连，直到达到最大重连次数。
     * 如果设置为 -1，则表示无限重连，直到连接成功或客户端关闭。</p>
     * 
     * <p>建议值：
     * <ul>
     *   <li>生产环境：10-20 次，避免无限重连消耗资源</li>
     *   <li>开发/测试环境：-1（无限重连），方便调试</li>
     * </ul></p>
     * 
     * @param maxReconnectAttempts 最大重连次数，-1 表示无限重连
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
        return this;
    }

    /**
     * 获取请求超时时间
     * 
     * @return 请求超时时间（毫秒），默认 30000 毫秒（30秒）
     */
    public long getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * 设置请求超时时间
     * 
     * <p>所有 gRPC 请求的超时时间。如果请求在指定时间内未完成，会抛出超时异常。</p>
     * 
     * <p>建议值：
     * <ul>
     *   <li>快速操作（如心跳、查询）：5000-10000 毫秒（5-10秒）</li>
     *   <li>一般操作（如注册、发现）：30000 毫秒（30秒）</li>
     *   <li>复杂操作（如批量操作）：60000 毫秒（60秒）</li>
     * </ul></p>
     * 
     * @param requestTimeout 请求超时时间（毫秒），必须大于 0
     * @return 当前配置对象，支持链式调用
     * @throws IllegalArgumentException 如果超时时间小于等于 0
     */
    public ServiceCenterConfig setRequestTimeout(long requestTimeout) {
        if (requestTimeout <= 0) {
            throw new IllegalArgumentException("请求超时时间必须大于 0");
        }
        this.requestTimeout = requestTimeout;
        return this;
    }

    /**
     * 获取客户端元数据
     * 
     * <p>元数据是键值对集合，可以用于存储额外的客户端信息，如环境标识、版本号等。
     * 这些信息可以在服务注册时传递给服务端。</p>
     * 
     * @return 客户端元数据映射，如果未设置则返回空映射（不会返回 null）
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 设置客户端元数据
     * 
     * <p>如果传入 null，则清空现有元数据。</p>
     * 
     * @param metadata 客户端元数据映射，可以为 null
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
        return this;
    }

    /**
     * 获取用户ID
     * 
     * @return 用户ID，如果未设置则返回 null
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 设置用户ID
     * 
     * <p>用于用户ID密码认证。如果同时设置了 userId 和 password，将优先使用用户ID密码认证。
     * 如果只设置了 authToken，则使用令牌认证。</p>
     * 
     * <p><strong>注意</strong>：这里应该填写用户ID（userId），而不是用户名（userName）。
     * userId 是数据库中 HUB_USER 表的唯一主键标识。</p>
     * 
     * @param userId 用户ID，可以为 null（表示不使用用户ID密码认证）
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    /**
     * 获取密码
     * 
     * @return 密码，如果未设置则返回 null
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码
     * 
     * <p>用于用户ID密码认证。如果同时设置了 userId 和 password，将优先使用用户ID密码认证。
     * 如果只设置了 authToken，则使用令牌认证。</p>
     * 
     * @param password 密码，可以为 null（表示不使用用户ID密码认证）
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setPassword(String password) {
        this.password = password;
        return this;
    }

    /**
     * 获取命名空间ID
     * 
     * @return 命名空间ID，默认 "ns_F41J68C80A50C28G68A06I53A49J4"
     */
    public String getNamespaceId() {
        return namespaceId;
    }

    /**
     * 设置命名空间ID
     * 
     * <p>命名空间用于服务隔离。不同的命名空间下的服务和配置相互独立。</p>
     * 
     * @param namespaceId 命名空间ID，不能为 null 或空
     * @return 当前配置对象，支持链式调用
     * @throws IllegalArgumentException 如果命名空间ID为空
     */
    public ServiceCenterConfig setNamespaceId(String namespaceId) {
        if (namespaceId == null || namespaceId.trim().isEmpty()) {
            throw new IllegalArgumentException("命名空间ID不能为空");
        }
        this.namespaceId = namespaceId;
        return this;
    }

    /**
     * 获取分组名
     * 
     * @return 分组名，默认 "DEFAULT_GROUP"
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * 设置分组名
     * 
     * <p>分组用于服务分组管理。同一命名空间下的不同分组可以包含相同的服务名。</p>
     * 
     * @param groupName 分组名，如果为 null 或空则使用 "DEFAULT_GROUP"
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setGroupName(String groupName) {
        this.groupName = (groupName == null || groupName.trim().isEmpty()) ? "DEFAULT_GROUP" : groupName;
        return this;
    }

    /**
     * 获取服务器完整地址
     * 
     * <p>如果设置了 serverAddress，则返回 serverAddress；否则返回 serverHost:serverPort</p>
     * 
     * <p>格式：单个地址 "host:port" 或集群地址 "host1:port1,host2:port2,host3:port3"</p>
     * 
     * @return 服务器完整地址
     */
    public String getServerAddress() {
        if (serverAddress != null && !serverAddress.trim().isEmpty()) {
            return serverAddress;
        }
        return serverHost + ":" + serverPort;
    }

    /**
     * 设置服务器地址（完整地址）
     * 
     * <p>支持单个地址或集群地址（多个地址用逗号分隔）。</p>
     * 
     * <p>示例：</p>
     * <ul>
     *   <li>单个地址：<code>"localhost:12004"</code></li>
     *   <li>集群地址：<code>"localhost:12004,192.168.1.1:12004,192.168.1.2:12004"</code></li>
     * </ul>
     * 
     * <p>如果设置了此字段，将优先使用此字段；否则使用 serverHost:serverPort 组合。</p>
     * 
     * @param serverAddress 服务器地址，格式：单个地址 "host:port" 或集群地址 "host1:port1,host2:port2,..."
     * @return 当前配置对象，支持链式调用
     * @throws IllegalArgumentException 如果地址格式不正确
     */
    public ServiceCenterConfig setServerAddress(String serverAddress) {
        if (serverAddress != null && !serverAddress.trim().isEmpty()) {
            // 验证地址格式
            String[] addresses = serverAddress.split(",");
            for (String addr : addresses) {
                String trimmed = addr.trim();
                if (trimmed.isEmpty()) {
                    throw new IllegalArgumentException("服务器地址不能包含空的地址项");
                }
                // 验证格式：host:port
                if (!trimmed.matches("^[^:]+:\\d+$")) {
                    throw new IllegalArgumentException("服务器地址格式不正确，应为 'host:port' 或 'host1:port1,host2:port2,...'，当前值: " + trimmed);
                }
            }
            this.serverAddress = serverAddress;
        } else {
            this.serverAddress = null;
        }
        return this;
    }

    /**
     * 获取服务器地址列表
     * 
     * <p>将服务器地址（可能是单个或多个）解析为地址列表。</p>
     * 
     * @return 服务器地址列表，每个元素格式为 "host:port"
     */
    public List<String> getServerAddresses() {
        String address = getServerAddress();
        List<String> addresses = new ArrayList<>();
        if (address != null && !address.trim().isEmpty()) {
            String[] parts = address.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    addresses.add(trimmed);
                }
            }
        }
        return addresses;
    }

    /**
     * 获取 gRPC Keep-Alive 时间间隔
     * 
     * @return Keep-Alive 时间间隔（毫秒），默认 30000 毫秒（30秒）
     */
    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * 设置 gRPC Keep-Alive 时间间隔
     * 
     * <p>Keep-Alive 用于检测连接是否存活。客户端会按照此间隔发送心跳包。</p>
     * 
     * <p>建议值：30000-60000 毫秒（30-60秒）。过短会增加网络负担，过长可能无法及时检测连接故障。</p>
     * 
     * @param keepAliveTime Keep-Alive 时间间隔（毫秒），必须大于 0
     * @return 当前配置对象，支持链式调用
     * @throws IllegalArgumentException 如果间隔小于等于 0
     */
    public ServiceCenterConfig setKeepAliveTime(long keepAliveTime) {
        if (keepAliveTime <= 0) {
            throw new IllegalArgumentException("Keep-Alive 时间间隔必须大于 0");
        }
        this.keepAliveTime = keepAliveTime;
        return this;
    }

    /**
     * 获取 gRPC Keep-Alive 超时时间
     * 
     * @return Keep-Alive 超时时间（毫秒），默认 10000 毫秒（10秒）
     */
    public long getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    /**
     * 设置 gRPC Keep-Alive 超时时间
     * 
     * <p>如果在此时间内没有收到心跳响应，则认为连接已断开。</p>
     * 
     * <p>建议值：5000-15000 毫秒（5-15秒）。建议设置为 keepAliveTime 的 1/3 到 1/2。</p>
     * 
     * @param keepAliveTimeout Keep-Alive 超时时间（毫秒），必须大于 0
     * @return 当前配置对象，支持链式调用
     * @throws IllegalArgumentException 如果超时时间小于等于 0
     */
    public ServiceCenterConfig setKeepAliveTimeout(long keepAliveTimeout) {
        if (keepAliveTimeout <= 0) {
            throw new IllegalArgumentException("Keep-Alive 超时时间必须大于 0");
        }
        this.keepAliveTimeout = keepAliveTimeout;
        return this;
    }

    /**
     * 获取是否在没有调用时也发送 Keep-Alive
     * 
     * @return true 如果启用，false 否则，默认 true
     */
    public boolean isKeepAliveWithoutCalls() {
        return keepAliveWithoutCalls;
    }

    /**
     * 设置是否在没有调用时也发送 Keep-Alive
     * 
     * <p>如果设置为 true，即使没有活跃的 RPC 调用，客户端也会发送 Keep-Alive 心跳。
     * 这有助于保持长连接活跃，防止被防火墙或负载均衡器断开。</p>
     * 
     * <p>建议值：true（生产环境推荐）</p>
     * 
     * @param keepAliveWithoutCalls 是否启用
     * @return 当前配置对象，支持链式调用
     */
    public ServiceCenterConfig setKeepAliveWithoutCalls(boolean keepAliveWithoutCalls) {
        this.keepAliveWithoutCalls = keepAliveWithoutCalls;
        return this;
    }

    /**
     * 获取 gRPC 最大入站消息大小
     * 
     * @return 最大入站消息大小（字节），默认 16MB
     */
    public int getMaxInboundMessageSize() {
        return maxInboundMessageSize;
    }

    /**
     * 设置 gRPC 最大入站消息大小
     * 
     * <p>限制可以接收的最大消息大小。如果接收到更大的消息，会抛出异常。</p>
     * 
     * <p>建议值：
     * <ul>
     *   <li>一般场景：16MB（默认值）</li>
     *   <li>大文件传输：64MB 或更大</li>
     *   <li>内存受限环境：4MB 或 8MB</li>
     * </ul></p>
     * 
     * @param maxInboundMessageSize 最大入站消息大小（字节），必须大于 0
     * @return 当前配置对象，支持链式调用
     * @throws IllegalArgumentException 如果大小小于等于 0
     */
    public ServiceCenterConfig setMaxInboundMessageSize(int maxInboundMessageSize) {
        if (maxInboundMessageSize <= 0) {
            throw new IllegalArgumentException("最大入站消息大小必须大于 0");
        }
        this.maxInboundMessageSize = maxInboundMessageSize;
        return this;
    }
}

