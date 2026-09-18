# Flux Service Center SDK for Java

[![CI](https://github.com/fluxsce/flux-service-center-sdk-java/actions/workflows/ci.yml/badge.svg)](https://github.com/fluxsce/flux-service-center-sdk-java/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![gRPC](https://img.shields.io/badge/gRPC-1.83.1-green.svg)](https://grpc.io/)

> 企业级服务注册中心与配置中心 Java SDK

## ✨ 核心特性

- 🚀 **服务注册发现** - 服务注册、节点管理、服务发现、实时变更推送
- ⚙️ **配置中心** - 配置管理、实时监听、版本回滚、多格式支持
- 🔌 **高级特性** - 双向流通信、集群支持、自动重连、TLS 加密、认证支持

## 📦 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>io.github.fluxsce</groupId>
    <artifactId>flux-service-center-sdk</artifactId>
    <version>3.0.0</version>
</dependency>
```

### 基本使用

```java
import com.flux.servicecenter.client.IServiceCenterClient;
import com.flux.servicecenter.client.ServiceCenterClients;
import com.flux.servicecenter.config.ServiceCenterConfig;
import com.flux.servicecenter.model.*;

// 1. 创建 v3 客户端（ServiceCenterClients.create）
ServiceCenterConfig config = new ServiceCenterConfig()
    .setServerHost("localhost")
    .setServerPort(12004)
    .setNamespaceId("my-namespace")
    .setGroupName("my-group");

IServiceCenterClient client = ServiceCenterClients.create(config);
client.connect();

// 2. 服务注册
ServiceInfo service = new ServiceInfo();
service.setServiceName("user-service");
service.setServiceType("INTERNAL");

NodeInfo node = new NodeInfo();
node.setIpAddress("192.168.1.100");
node.setPortNumber(8080);

RegisterServiceResult result = client.registerService(service, node);

// 3. 服务发现
GetServiceResult discovered = client.getService("my-namespace", "my-group", "user-service");
List<NodeInfo> nodes = discovered.getHealthyNodes();

// 4. 配置管理（saveConfig = 先 saveDraft 再 publishConfig）
ConfigInfo configInfo = new ConfigInfo();
configInfo.setConfigDataId("app.config");
configInfo.setConfigContent("key=value");
configInfo.setContentType("properties");

client.saveConfig(configInfo);

// 5. 关闭客户端
client.close();
```

## 🏗️ 高级配置

### 集群模式（生产环境推荐）

```java
ServiceCenterConfig config = new ServiceCenterConfig()
    // 集群地址（逗号分隔，支持故障切换和负载均衡）
    .setServerAddress("node1:12004,node2:12004,node3:12004")
    .setNamespaceId("production")
    .setHeartbeatInterval(5000)
    .setReconnectInterval(3000)
    .setKeepAliveTime(30000)
    .setKeepAliveTimeout(10000);
```

### TLS 加密

```java
ServiceCenterConfig config = new ServiceCenterConfig()
    .setServerAddress("secure-server:12004")
    .setEnableTls(true)
    .setTlsCaPath("/etc/certs/ca.crt")
    .setTlsCertPath("/etc/certs/client.crt")  // 双向 TLS
    .setTlsKeyPath("/etc/certs/client.key")
    .setNamespaceId("production");
```

### 认证配置

```java
// 用户ID/密码认证（与 authToken 二选一）
ServiceCenterConfig config = new ServiceCenterConfig()
    .setServerAddress("auth-server:12004")
    .setUserId("user-001")
    .setPassword("secure-password")
    .setNamespaceId("production");

// 或使用 API Token
config.setAuthToken("your-api-token");
```

## 📚 主要功能

### 服务注册发现

```java
// 注册服务
client.registerService(serviceInfo, nodeInfo);

// 发现服务
GetServiceResult discovered = client.getService(namespace, group, serviceName);
List<NodeInfo> nodes = discovered.getHealthyNodes();

// 订阅服务变更
String subscriptionId = client.subscribeService(namespace, group, serviceName, 
    event -> {
        System.out.println("服务变更: " + event.getEventType());
    });

// 取消订阅
client.unsubscribe(subscriptionId);
```

### 配置管理

```java
// 保存并发布配置（内部先 saveDraft 再 publishConfig）
client.saveConfig(configInfo);

// 只写草稿 / 单独发布
client.saveDraft(configInfo);
client.publishConfig(namespace, group, configId, "release");

// 获取配置
GetConfigResult result = client.getConfig(namespace, group, configId);

// 监听配置变更
String watchId = client.watchConfig(namespace, group, configId,
    event -> {
        ConfigInfo changed = event.getConfig();
        if (changed != null) {
            System.out.println("配置变更: " + changed.getConfigContent());
        }
    });

// 配置历史与回滚（historyId 即 configVersion）
List<ConfigHistory> history = client.getConfigHistory(namespace, group, configId, 1, 10);
client.rollbackConfig(namespace, group, configId, history.get(0).getHistoryId());
```

## 🔧 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `serverHost` | String | localhost | 服务器主机 |
| `serverPort` | int | 12004 | 服务器端口 |
| `serverAddress` | String | - | 完整地址，优先于 host/port；逗号分隔多个地址做故障切换 |
| `enableTls` | boolean | false | 是否启用 TLS |
| `tlsCaPath` / `tlsCertPath` / `tlsKeyPath` | String | - | CA / 客户端证书 / 私钥路径 |
| `userId` / `password` | String | - | 用户认证（与 authToken 二选一） |
| `authToken` | String | - | API Token 认证 |
| `namespaceId` | String | 空 | 命名空间，需由调用方按环境设置 |
| `groupName` | String | DEFAULT_GROUP | 分组名称 |
| `heartbeatInterval` | long | 5000 | 心跳间隔（毫秒） |
| `reconnectInterval` | long | 3000 | 重连间隔（毫秒） |
| `maxReconnectAttempts` | int | 10 | 最大重连次数，`-1` 表示无限重连 |
| `requestTimeout` | long | 30000 | 请求超时（毫秒） |
| `keepAliveTime` | long | 30000 | Keep-Alive 间隔（毫秒） |
| `keepAliveTimeout` | long | 10000 | Keep-Alive 超时（毫秒） |
| `maxInboundMessageSize` | int | 16MB | 最大消息大小 |

## 🏃 最佳实践

### 使用 Try-With-Resources

```java
try (IServiceCenterClient client = ServiceCenterClients.create(config)) {
    client.connect();
    // 业务逻辑
} // 自动关闭
```

### 生产环境配置

```java
ServiceCenterConfig config = new ServiceCenterConfig()
    .setServerAddress("sc1:12004,sc2:12004,sc3:12004")  // 集群
    .setEnableTls(true)                                  // TLS 加密
    .setTlsCaPath("/etc/certs/ca.crt")
    .setUserId("prod-user")                              // 认证
    .setPassword("secure-password")
    .setNamespaceId("production")
    .setHeartbeatInterval(5000)
    .setReconnectInterval(3000);
```

### 优雅关闭

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    client.close();
}));
```

## 📊 性能指标

- 连接建立：< 100ms
- 服务注册：< 50ms
- 服务发现：< 30ms
- 配置查询：< 20ms
- 事件推送延迟：< 10ms
- 并发支持：10,000+ QPS

## 🔍 常见问题

### 连接失败

```bash
# 检查网络
telnet <server-host> <server-port>

# 检查服务
ps aux | grep service-center
```

### TLS 握手失败

```bash
# 验证证书
openssl x509 -in /path/to/ca.crt -text -noout

# 测试连接
openssl s_client -connect server:12004 -CAfile /path/to/ca.crt
```

## 📝 更新日志

详见 [CHANGELOG.md](CHANGELOG.md)

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

```
Copyright 2024 Flux Service Center SDK Contributors

Licensed under the Apache License, Version 2.0
```

## 📞 联系方式

- **项目主页**: [GitHub Repository](https://github.com/fluxsce/flux-service-center-sdk-java)
- **问题反馈**: [Issue Tracker](https://github.com/fluxsce/flux-service-center-sdk-java/issues)

---

**如果这个项目对你有帮助，请给个 ⭐ Star 支持一下！**
