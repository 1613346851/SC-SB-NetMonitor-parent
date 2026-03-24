package com.network.monitor.service;

import java.util.List;
import java.util.Map;

/**
 * 网关配置管理服务
 * 负责管理网关服务的所有配置项，支持配置的读取、更新和推送
 */
public interface GatewayConfigService {

    /**
     * 获取所有网关配置
     * @return 配置Map，key为配置键，value为配置值
     */
    Map<String, Object> getAllGatewayConfigs();

    /**
     * 获取字符串配置值
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    String getConfigValue(String configKey, String defaultValue);

    /**
     * 获取整数配置值
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    int getIntConfigValue(String configKey, int defaultValue);

    /**
     * 获取长整数配置值
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    long getLongConfigValue(String configKey, long defaultValue);

    /**
     * 获取布尔配置值
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    boolean getBooleanConfigValue(String configKey, boolean defaultValue);

    /**
     * 获取列表配置值
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值列表
     */
    List<String> getListConfigValue(String configKey, String defaultValue);

    /**
     * 更新配置并推送到网关
     * @param configKey 配置键
     * @param configValue 配置值
     */
    void updateConfig(String configKey, String configValue);

    /**
     * 推送单个配置到网关
     * @param configKey 配置键
     * @param configValue 配置值
     * @return 是否成功
     */
    boolean pushConfigToGateway(String configKey, String configValue);

    /**
     * 推送所有配置到网关
     * @return 是否成功
     */
    boolean pushAllConfigsToGateway();

    /**
     * 刷新网关配置缓存
     */
    void refreshGatewayConfigCache();

    /**
     * 获取网关配置项列表（带描述）
     * @return 配置项详情列表
     */
    List<Map<String, Object>> getGatewayConfigList();

    /**
     * 获取网关配置数量
     * @return 配置数量
     */
    int getGatewayConfigCount();
}
