package com.flux.servicecenter.client;

import com.flux.servicecenter.config.ServiceCenterConfig;

/**
 * 兼容入口：3.0 实现就是双向流客户端。
 *
 * <p>新代码请用 {@link ServiceCenterClients#create(ServiceCenterConfig)}。
 * 直接 {@code new StreamBasedServiceCenterClient(config)} 仍可升级。</p>
 */
public class StreamBasedServiceCenterClient extends ServiceCenterClient {

    public StreamBasedServiceCenterClient(ServiceCenterConfig config) {
        super(config);
    }
}
