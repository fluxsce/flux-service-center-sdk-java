package com.flux.servicecenter.client;

import com.flux.servicecenter.listener.ConfigChangeListener;
import com.flux.servicecenter.model.*;

import java.util.List;

/**
 * 配置中心接口
 * 
 * <p>提供类似 Nacos Config 的配置管理功能，包括：</p>
 * <ul>
 *   <li><b>配置管理</b> - 增删改查配置、配置发布</li>
 *   <li><b>配置监听</b> - 实时监听配置变更</li>
 *   <li><b>配置历史</b> - 查询配置的历史版本</li>
 *   <li><b>配置回滚</b> - 回滚到历史版本</li>
 * </ul>
 * 
 * <p><b>核心概念：</b></p>
 * <ul>
 *   <li><b>命名空间（Namespace）</b>：用于环境隔离（如：dev、test、prod）</li>
 *   <li><b>分组（Group）</b>：用于配置分类（如：DEFAULT_GROUP、DB_GROUP）</li>
 *   <li><b>配置ID（ConfigDataId）</b>：配置的唯一标识（如：application.yaml）</li>
 *   <li><b>内容类型（ContentType）</b>：配置格式（如：yaml、json、properties、xml）</li>
 * </ul>
 * 
 * <p><b>配置发布示例：</b></p>
 * <pre>{@code
 * ConfigInfo config = new ConfigInfo()
 *     .setNamespaceId("production")
 *     .setGroupName("DB_GROUP")
 *     .setConfigDataId("mysql-config.yaml")
 *     .setContentType("yaml")
 *     .setConfigContent("host: 192.168.1.100\nport: 3306\nusername: root")
 *     .setConfigDesc("MySQL 生产环境配置");
 * 
 * SaveConfigResult result = client.saveConfig(config);
 * if (result.isSuccess()) {
 *     System.out.println("配置发布成功");
 * }
 * }</pre>
 * 
 * <p><b>配置获取示例：</b></p>
 * <pre>{@code
 * GetConfigResult result = client.getConfig("production", "DB_GROUP", "mysql-config.yaml");
 * if (result.isSuccess()) {
 *     String content = result.getConfig().getConfigContent();
 *     // 解析配置内容
 *     Properties props = parseYaml(content);
 * }
 * }</pre>
 * 
 * <p><b>配置监听示例：</b></p>
 * <pre>{@code
 * String watchId = client.watchConfig(
 *     "production",
 *     "DB_GROUP",
 *     "mysql-config.yaml",
 *     new ConfigChangeListener() {
 *         @Override
 *         public void onConfigChange(ConfigChangeEvent event) {
 *             System.out.println("配置发生变更: " + event.getEventType());
 *             String newContent = event.getConfig().getConfigContent();
 *             // 重新加载配置
 *             reloadConfig(newContent);
 *         }
 *     }
 * );
 * 
 * // 取消监听
 * client.unwatch(watchId);
 * }</pre>
 * 
 * <p><b>配置回滚示例：</b></p>
 * <pre>{@code
 * // 1. 查询配置历史
 * GetConfigHistoryResult historyResult = client.getConfigHistory(
 *     "production", "DB_GROUP", "mysql-config.yaml", 1, 10);
 * List<ConfigHistoryInfo> histories = historyResult.getHistories();
 * 
 * // 2. 选择要回滚的版本
 * ConfigHistoryInfo targetVersion = histories.get(1); // 上一个版本
 * 
 * // 3. 执行回滚
 * RollbackConfigResult rollbackResult = client.rollbackConfig(
 *     "production", "DB_GROUP", "mysql-config.yaml", targetVersion.getHistoryId());
 * }</pre>
 * 
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>配置变更会实时推送给所有监听器（< 1秒延迟）</li>
 *   <li>配置历史默认保留最近100个版本</li>
 *   <li>支持断线重连，自动恢复配置监听</li>
 *   <li>所有方法都是线程安全的</li>
 * </ul>
 * 
 * @author shangjian
 * @version 1.0.0
 * @see ConfigInfo
 * @see SaveConfigResult
 * @see GetConfigResult
 * @see ConfigChangeListener
 */
public interface IConfigService {
    
    // ========================================
    // 配置管理
    // ========================================
    
    /**
     * 保存或更新配置
     * 
     * <p>发布配置到服务中心，如果配置已存在则更新，否则创建新配置。
     * 配置保存成功后会自动推送给所有监听器。</p>
     * 
     * <p><b>必填字段：</b></p>
     * <ul>
     *   <li>namespaceId - 命名空间ID</li>
     *   <li>groupName - 分组名</li>
     *   <li>configDataId - 配置ID</li>
     *   <li>configContent - 配置内容</li>
     *   <li>contentType - 内容类型（text/json/xml/yaml/properties）</li>
     * </ul>
     * 
     * <p><b>可选字段：</b></p>
     * <ul>
     *   <li>configDesc - 配置描述</li>
     *   <li>configTag - 配置标签（用于分类和搜索）</li>
     * </ul>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * ConfigInfo config = new ConfigInfo()
     *     .setNamespaceId("production")
     *     .setGroupName("APP_GROUP")
     *     .setConfigDataId("application.yaml")
     *     .setContentType("yaml")
     *     .setConfigContent("server:\n  port: 8080")
     *     .setConfigDesc("应用主配置")
     *     .setConfigTag("app,common");
     * 
     * SaveConfigResult result = client.saveConfig(config);
     * }</pre>
     * 
     * @param config 配置信息，不能为 null
     * @return 保存结果，包含是否成功、消息、是否为新建
     * @throws IllegalArgumentException 如果必填字段为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果保存失败
     */
    SaveConfigResult saveConfig(ConfigInfo config);
    
    /**
     * 获取配置内容
     * 
     * <p>查询指定配置的最新内容和元数据。
     * 如果配置不存在，返回的结果中 success 为 false。</p>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * GetConfigResult result = client.getConfig("production", "APP_GROUP", "application.yaml");
     * 
     * if (result.isSuccess()) {
     *     ConfigInfo config = result.getConfig();
     *     String content = config.getConfigContent();
     *     String contentType = config.getContentType();
     *     
     *     // 根据内容类型解析配置
     *     Object parsedConfig = parseConfigContent(content, contentType);
     * } else {
     *     System.out.println("配置不存在: " + result.getMessage());
     * }
     * }</pre>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置ID，不能为空
     * @return 获取配置结果，包含配置详细信息
     * @throws IllegalArgumentException 如果必填参数为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果查询失败
     */
    GetConfigResult getConfig(String namespaceId, String groupName, String configDataId);
    
    /**
     * 删除配置
     * 
     * <p>从服务中心删除指定配置。
     * 删除后会通知所有监听器配置已被删除。</p>
     * 
     * <p><b>注意：</b>删除配置不会删除配置历史记录，仍可通过历史记录恢复。</p>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置ID，不能为空
     * @return 操作结果
     * @throws IllegalArgumentException 如果必填参数为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果删除失败
     */
    OperationResult deleteConfig(String namespaceId, String groupName, String configDataId);
    
    /**
     * 查询配置列表
     * 
     * <p>查询指定命名空间和分组下的所有配置。
     * 支持分页查询和模糊搜索。</p>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * // 查询所有配置
     * ListConfigsResult result = client.listConfigs("production", "APP_GROUP", null, 1, 50);
     * 
     * // 搜索包含 "database" 的配置
     * ListConfigsResult searchResult = client.listConfigs("production", "APP_GROUP", "database", 1, 50);
     * 
     * List<ConfigInfo> configs = result.getConfigs();
     * int total = result.getTotal();
     * }</pre>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则查询所有分组
     * @param searchKey 搜索关键字（匹配 configDataId 或 configDesc），可以为 null
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @return 配置列表结果，包含总数和当前页数据
     * @throws IllegalArgumentException 如果参数无效
     * @throws IllegalStateException 如果客户端未连接
     */
    List<ConfigInfo> listConfigs(String namespaceId, String groupName, 
                                 String searchKey, int pageNum, int pageSize);
    
    // ========================================
    // 配置监听
    // ========================================
    
    /**
     * 监听配置变更
     * 
     * <p>实时监听指定配置的变更事件，包括：</p>
     * <ul>
     *   <li>配置内容更新</li>
     *   <li>配置被删除</li>
     *   <li>配置回滚</li>
     * </ul>
     * 
     * <p><b>使用场景：</b></p>
     * <ul>
     *   <li>动态配置：应用运行时自动更新配置</li>
     *   <li>热加载：无需重启即可生效新配置</li>
     *   <li>配置同步：多个应用实例同步配置</li>
     * </ul>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * // 监听数据库配置变更
     * String watchId = client.watchConfig(
     *     "production",
     *     "DB_GROUP",
     *     "mysql-config.yaml",
     *     new ConfigChangeListener() {
     *         @Override
     *         public void onConfigChange(ConfigChangeEvent event) {
     *             if (event.getEventType() == ConfigChangeEventType.CONFIG_UPDATED) {
     *                 String newContent = event.getConfig().getConfigContent();
     *                 // 重新初始化数据源
     *                 reinitializeDataSource(newContent);
     *                 System.out.println("数据库配置已更新并生效");
     *             } else if (event.getEventType() == ConfigChangeEventType.CONFIG_DELETED) {
     *                 // 使用默认配置
     *                 useDefaultConfig();
     *             }
     *         }
     *     }
     * );
     * 
     * // 应用关闭时取消监听
     * Runtime.getRuntime().addShutdownHook(new Thread(() -> {
     *     client.unwatch(watchId);
     * }));
     * }</pre>
     * 
     * <p><b>注意事项：</b></p>
     * <ul>
     *   <li>监听器在独立的线程中执行，不会阻塞主流程</li>
     *   <li>支持断线重连，自动恢复监听状态</li>
     *   <li>配置变更推送延迟通常 < 1秒</li>
     *   <li>客户端关闭时会自动取消所有监听</li>
     * </ul>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置ID，不能为空
     * @param listener 变更事件监听器，不能为 null
     * @return 监听ID，用于后续取消监听
     * @throws IllegalArgumentException 如果必填参数为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果监听失败
     */
    String watchConfig(String namespaceId, String groupName, 
                      String configDataId, ConfigChangeListener listener);
    
    /**
     * 取消配置监听
     * 
     * <p>停止监听指定配置的变更事件，关闭对应的监听流。</p>
     * 
     * @param watchId 监听ID（由 watchConfig 返回）
     * @return 操作结果
     * @throws IllegalArgumentException 如果 watchId 为空
     * @throws IllegalStateException 如果客户端未连接
     */
    OperationResult unwatch(String watchId);
    
    // ========================================
    // 配置历史与回滚
    // ========================================
    
    /**
     * 获取配置历史记录
     * 
     * <p>查询指定配置的所有历史版本，按时间倒序排列（最新的在前）。
     * 每次保存配置都会生成一条历史记录。</p>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * List<ConfigHistory> histories = client.getConfigHistory(
     *     "production", "APP_GROUP", "application.yaml", 1, 10);
     * 
     * // 显示历史版本列表
     * for (ConfigHistory history : histories) {
     *     System.out.printf("版本: %s, 时间: %s, 操作人: %s\n",
     *         history.getHistoryId(),
     *         history.getCreateTime(),
     *         history.getCreatedBy());
     * }
     * }</pre>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置ID，不能为空
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @return 配置历史列表（只读）
     * @throws IllegalArgumentException 如果参数无效
     * @throws IllegalStateException 如果客户端未连接
     */
    List<ConfigHistory> getConfigHistory(String namespaceId, String groupName, 
                                        String configDataId, int pageNum, int pageSize);
    
    /**
     * 回滚配置到指定历史版本
     * 
     * <p>将配置恢复到某个历史版本。
     * 回滚成功后会通知所有监听器配置已更新。</p>
     * 
     * <p><b>回滚流程：</b></p>
     * <ol>
     *   <li>查询配置历史记录</li>
     *   <li>选择目标版本的 historyId</li>
     *   <li>调用 rollbackConfig 执行回滚</li>
     *   <li>回滚后会生成新的历史记录</li>
     * </ol>
     * 
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * // 1. 查询历史版本
     * GetConfigHistoryResult historyResult = client.getConfigHistory(
     *     "production", "APP_GROUP", "application.yaml", 1, 10);
     * 
     * // 2. 选择要回滚的版本（例如：上一个版本）
     * ConfigHistoryInfo previousVersion = historyResult.getHistories().get(1);
     * 
     * // 3. 执行回滚
     * RollbackConfigResult rollbackResult = client.rollbackConfig(
     *     "production",
     *     "APP_GROUP",
     *     "application.yaml",
     *     previousVersion.getHistoryId()
     * );
     * 
     * if (rollbackResult.isSuccess()) {
     *     System.out.println("配置已回滚到版本: " + previousVersion.getHistoryId());
     * }
     * }</pre>
     * 
     * <p><b>注意：</b>回滚操作本身也会生成一条历史记录，因此可以回滚回滚操作。</p>
     * 
     * @param namespaceId 命名空间ID，不能为空
     * @param groupName 分组名，如果为 null 则使用 "DEFAULT_GROUP"
     * @param configDataId 配置ID，不能为空
     * @param historyId 历史记录ID（由 getConfigHistory 获取）
     * @return 回滚结果
     * @throws IllegalArgumentException 如果必填参数为空
     * @throws IllegalStateException 如果客户端未连接
     * @throws RuntimeException 如果回滚失败
     */
    RollbackConfigResult rollbackConfig(String namespaceId, String groupName, 
                                       String configDataId, String historyId);
    
    // ========================================
    // 工具方法
    // ========================================
    
    /**
     * 获取所有活跃的配置监听ID列表
     * 
     * <p>返回当前客户端的所有活跃配置监听，可用于监控和管理。</p>
     * 
     * @return 监听ID列表（只读）
     */
    List<String> getActiveWatches();
}

