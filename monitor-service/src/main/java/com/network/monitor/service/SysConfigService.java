package com.network.monitor.service;

import com.network.monitor.entity.SysConfigEntity;

import java.util.List;
import java.util.Map;

public interface SysConfigService {

    List<SysConfigEntity> getAllConfigs();

    SysConfigEntity getConfigById(Long id);

    SysConfigEntity getConfigByKey(String configKey);

    String getConfigValue(String configKey);

    void updateConfig(SysConfigEntity config);

    void updateConfigValue(String configKey, String configValue);

    void addConfig(SysConfigEntity config);

    void deleteConfig(Long id);

    void refreshCache();
}