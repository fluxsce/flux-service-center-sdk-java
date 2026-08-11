package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;

/**
 * 服务中心客户端工厂。
 *
 * <p>对外统一通过本类创建 {@link IServiceCenterClient}；
 * <b>默认实现为双向流客户端</b>（{@link StreamBasedServiceCenterClient}）。</p>
 *
 * <p><b>推荐流程：</b></p>
 * <ol>
 *   <li>构建 {@link ServiceCenterConfig}</li>
 *   <li>{@link #create(ServiceCenterConfig)} 获得客户端</li>
 *   <li>{@link IServiceCenterClient#connect()} 建连并完成握手</li>
 *   <li>注册 / 发现 / 订阅 / 配置操作</li>
 *   <li>{@link IServiceCenterClient#close()} 优雅注销节点并释放资源</li>
 * </ol>
 *
 * <pre>{@code
 * ServiceCenterConfig config = new ServiceCenterConfig()
 *     .setServerHost("localhost")
 *     .setServerPort(50051)
 *     .setNamespaceId("my-namespace")
 *     .setGroupName("my-group");
 *
 * try (IServiceCenterClient client = ServiceCenterClients.create(config)) {
 *     client.connect();
 *     // 注册、发现、配置...
 * }
 * }</pre>
 *
 * @author shangjian
 * @see IServiceCenterClient
 * @see StreamBasedServiceCenterClient
 * @see ServiceCenterClient
 */
public final class ServiceCenterClients {

    private ServiceCenterClients() {
    }

    /**
     * 创建默认客户端（Stream 双向流）。
     *
     * <p>等价于 {@link #createStream(ServiceCenterConfig)}。新业务应使用本方法。</p>
     *
     * @param config 客户端配置，不能为 null
     * @return Stream 实现的 {@link IServiceCenterClient}
     */
    public static IServiceCenterClient create(ServiceCenterConfig config) {
        return createStream(config);
    }

    /**
     * 显式创建 Stream 双向流客户端。
     *
     * <p>单连接多路复用：注册发现、配置、推送、心跳均走统一双向流；
     * 断连后会按本地缓存恢复注册与订阅。</p>
     *
     * @param config 客户端配置，不能为 null
     * @return Stream 客户端
     */
    public static IServiceCenterClient createStream(ServiceCenterConfig config) {
        return new StreamBasedServiceCenterClient(config);
    }

    /**
     * 创建 Classic（多 unary / server-stream）客户端。
     *
     * <p>保留原 Classic 连接与业务管理器拆分流程，仅用于兼容旧集成；
     * 新代码请改用 {@link #create(ServiceCenterConfig)}。</p>
     *
     * @param config 客户端配置，不能为 null
     * @return Classic 客户端
     * @deprecated 请使用 {@link #create(ServiceCenterConfig)}；Classic 仅作兼容保留，后续版本可能移除
     */
    @Deprecated
    public static IServiceCenterClient createClassic(ServiceCenterConfig config) {
        return new ServiceCenterClient(config);
    }
}
