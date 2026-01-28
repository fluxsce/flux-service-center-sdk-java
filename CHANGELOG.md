# 更新日志

本文件记录了本项目的所有重要变更。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，并遵循 [语义化版本](https://semver.org/lang/zh-CN/) 规范。

## [Unreleased]（未发布）

## [2.0.0] - 2026-01-28

### 新增
- 🚀 **集群地址支持**
  - 支持配置多个服务器地址（逗号分隔），实现高可用集群连接
  - 自动故障切换，当某个节点故障时自动切换到其他可用节点
  - 轮询负载均衡策略（round_robin），请求均匀分配到所有节点
  - 支持 TLS 加密的集群模式，保障数据传输安全
  - 完全兼容原有单机配置方式，无需修改现有代码

- 🚀 **Keep-Alive 配置支持**
  - 新增 `keepAliveTime` 配置项，控制 Keep-Alive 心跳间隔（默认 30000 毫秒）
  - 新增 `keepAliveTimeout` 配置项，控制 Keep-Alive 超时时间（默认 10000 毫秒）
  - 新增 `keepAliveWithoutCalls` 配置项，控制是否在无活跃调用时发送心跳（默认 true）
  - 新增 `maxInboundMessageSize` 配置项，控制最大入站消息大小（默认 16MB）
  - 统一使用毫秒作为时间单位，与其他配置项保持一致

- 🚀 **StreamBasedServiceCenterClient 增强**
  - 基于 gRPC 双向流的高性能客户端实现
  - 支持实时事件推送（服务变更、配置变更）
  - 自动重连机制，支持指数退避策略（最多重试 5 次）
  - 重连后自动恢复状态（重新注册节点、恢复订阅、恢复配置监听）
  - 连接健康检查，及时发现并处理连接异常

### 变更
- ⚡ **配置管理优化**
  - `ServiceCenterConfig` 新增集群地址相关配置项
  - 配置项 `serverAddress` 现在支持单个地址或多个地址（逗号分隔）
  - 优先级：`serverAddress` > `serverHost` + `serverPort`
  - 所有时间相关配置统一使用毫秒作为单位

- ⚡ **连接管理优化**
  - `ConnectionManager` 和 `StreamConnectionManager` 统一使用配置类中的 Keep-Alive 参数
  - 优化连接建立逻辑，自动识别单机模式和集群模式
  - 改进日志输出，区分单机模式和集群模式的日志信息


## [1.0.0] - 2025-10-24

### 新增
- 🚀 **核心功能**
  - 服务注册与节点管理
  - 服务发现与健康检查
  - 服务变更实时订阅
  - 配置的增删改查
  - 配置变更实时监听
  - 配置历史与版本回滚
  - 支持多种配置格式（text、JSON、XML、YAML、Properties）

- 🚀 **客户端实现**
  - `ServiceCenterClient` - 基于独立 RPC 调用的传统客户端
  - `StreamBasedServiceCenterClient` - 基于双向流的高性能客户端
  - 完整的领域模型（ServiceInfo、NodeInfo、ConfigInfo 等）
  - 丰富的监听器接口（ServiceChangeListener、ConfigChangeListener）

- 🚀 **高级特性**
  - 自动心跳机制，保持节点在线状态
  - 自动重连机制，连接断开自动恢复
  - TLS/mTLS 加密通信支持
  - 用户ID/密码认证和 Token 认证
  - 元数据管理，灵活扩展节点和服务属性

- 📚 **文档与示例**
  - 完整的 JavaDoc 注释
  - 丰富的使用示例
  - 详细的配置说明

### 技术栈
- **开发语言**: Java 8+
- **通信协议**: gRPC
- **序列化**: Protocol Buffers
- **日志框架**: SLF4J
- **构建工具**: Maven

### 性能指标
- 连接建立：< 100ms
- 服务注册：< 50ms
- 服务发现：< 30ms
- 配置查询：< 20ms
- 事件推送延迟：< 10ms
- 并发支持：10,000+ QPS

## 版本对比

| 版本 | 发布时间 | 主要特性 |
|------|----------|----------|
| 2.0.0 | 2026-01-28 | 集群支持、Keep-Alive 配置、性能优化 |
| 1.0.0 | 2025-10-24 | 首次发布，核心功能完整实现 |

## 升级指南

### 从 1.0.0 升级到 2.0.0

- **完全兼容**：原有 API 无需修改
- **新增功能**：集群地址支持、Keep-Alive 配置
- **升级步骤**：更新依赖版本即可，新功能可选启用

## 贡献日志说明

贡献代码时请：

1. 将变更添加到 `[Unreleased]` 部分
2. 使用如下分类：
   - `新增`：新特性
   - `变更`：已有功能变更
   - `弃用`：即将移除的功能
   - `移除`：已移除的功能
   - `修复`：Bug 修复
   - `安全`：安全漏洞修复

3. 格式示例：
   ```markdown
   - **类别**：简要描述 [#PR编号](PR链接)
   ```

4. 链接相关的 Pull Request 和 Issue

## 支持与反馈

如需了解特定版本详情或升级支持：
- 查阅 [项目文档](README.md)
- 提交 [Issue](https://github.com/fluxsce/flux-service-center-sdk-java/issues)
- 参与 [社区讨论](https://github.com/fluxsce/flux-service-center-sdk-java/discussions)

---

**图例：**
- 🚀 新特性
- 🔧 改进
- 🐛 修复
- ⚡ 性能
- 🛡️ 安全
- 📚 文档

