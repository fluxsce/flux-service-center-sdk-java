package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;

/**
 * 服务中心客户端工厂。
 *
 * <p>3.0 只提供 v3 双向流实现。调用方继续用 {@link #create(ServiceCenterConfig)} 即可升级。</p>
 */
public final class ServiceCenterClients {

    private ServiceCenterClients() {
    }

    /**
     * 创建 v3 客户端。
     *
     * @param config 客户端配置，不能为 null
     * @return 实现 {@link IServiceCenterClient} 的流客户端
     */
    public static IServiceCenterClient create(ServiceCenterConfig config) {
        return createStream(config);
    }

    /**
     * 显式创建流客户端，行为与 {@link #create(ServiceCenterConfig)} 相同。
     *
     * @param config 客户端配置，不能为 null
     * @return 流客户端
     */
    public static IServiceCenterClient createStream(ServiceCenterConfig config) {
        return new StreamBasedServiceCenterClient(config);
    }
}
