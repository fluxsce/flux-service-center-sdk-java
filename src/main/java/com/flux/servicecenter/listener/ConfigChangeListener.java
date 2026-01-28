package com.flux.servicecenter.listener;

import com.flux.servicecenter.model.ConfigChangeEvent;
import com.flux.servicecenter.model.ConfigInfo;

/**
 * 配置变更监听器
 * 
 * <p>用于接收配置变更事件。使用领域对象而非 Proto 对象，实现业务层与 Proto 层的解耦。</p>
 * 
 * <p>提供默认实现，用户只需覆盖感兴趣的方法即可。</p>
 *
 * @author shangjian
 */
public interface ConfigChangeListener {
    
    /**
     * 处理配置变更事件
     * 
     * @param event 配置变更事件领域对象
     */
    void onConfigChange(ConfigChangeEvent event);
    
    /**
     * 配置更新事件
     * 
     * @param config 配置数据领域对象
     * @param contentMd5 新的 MD5 值
     */
    default void onConfigUpdated(ConfigInfo config, String contentMd5) {
        // 默认实现，子类可以覆盖
    }
    
    /**
     * 配置删除事件
     * 
     * @param namespaceId 命名空间ID
     * @param groupName 分组名
     * @param configDataId 配置标识
     */
    default void onConfigDeleted(String namespaceId, String groupName, String configDataId) {
        // 默认实现，子类可以覆盖
    }
    
    /**
     * 监听连接断开
     * 
     * @param cause 断开原因
     */
    default void onDisconnected(Throwable cause) {
        // 默认实现，子类可以覆盖
    }
    
    /**
     * 监听连接恢复
     */
    default void onReconnected() {
        // 默认实现，子类可以覆盖
    }
}

