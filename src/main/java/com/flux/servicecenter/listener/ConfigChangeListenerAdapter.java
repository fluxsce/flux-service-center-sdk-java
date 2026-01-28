package com.flux.servicecenter.listener;

import com.flux.servicecenter.model.ConfigChangeEvent;
import com.flux.servicecenter.model.ConfigInfo;

/**
 * 配置变更监听器适配器
 * <p>
 * 提供了 {@link ConfigChangeListener} 接口的默认空实现。
 * 用户可以继承此适配器，并只覆盖感兴趣的方法，从而简化监听器的实现。
 * </p>
 *
 * @author shangjian
 */
public abstract class ConfigChangeListenerAdapter implements ConfigChangeListener {

    @Override
    public void onConfigChange(ConfigChangeEvent event) {
        // 根据事件类型调用对应的默认方法
        switch (event.getEventType()) {
            case CONFIG_UPDATED:
                onConfigUpdated(event.getConfig(), event.getContentMd5());
                break;
            case CONFIG_DELETED:
                onConfigDeleted(event.getNamespaceId(), event.getGroupName(), event.getConfigDataId());
                break;
        }
    }

    @Override
    public void onConfigUpdated(ConfigInfo config, String contentMd5) {
        // 默认空实现
    }

    @Override
    public void onConfigDeleted(String namespaceId, String groupName, String configDataId) {
        // 默认空实现
    }

    @Override
    public void onDisconnected(Throwable cause) {
        // 默认空实现
    }

    @Override
    public void onReconnected() {
        // 默认空实现
    }
}

