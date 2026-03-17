package com.network.monitor.service.impl;

import com.network.monitor.entity.SysConfigEntity;
import com.network.monitor.event.ConfigRefreshEvent;
import com.network.monitor.event.ConfigUpdateEvent;
import com.network.monitor.loader.ConfigLoader;
import com.network.monitor.mapper.SysConfigMapper;
import com.network.monitor.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class SysConfigServiceImpl implements SysConfigService, ConfigLoader {

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public List<SysConfigEntity> loadAllConfigs() {
        return getAllConfigs();
    }

    @Override
    public List<SysConfigEntity> getAllConfigs() {
        try {
            return sysConfigMapper.selectAll();
        } catch (Exception e) {
            log.error("获取所有配置失败", e);
            throw new RuntimeException("获取配置失败：" + e.getMessage(), e);
        }
    }

    @Override
    public SysConfigEntity getConfigById(Long id) {
        try {
            return sysConfigMapper.selectById(id);
        } catch (Exception e) {
            log.error("根据ID获取配置失败：id={}", id, e);
            return null;
        }
    }

    @Override
    public SysConfigEntity getConfigByKey(String configKey) {
        try {
            return sysConfigMapper.selectByKey(configKey);
        } catch (Exception e) {
            log.error("根据键获取配置失败：configKey={}", configKey, e);
            return null;
        }
    }

    @Override
    public String getConfigValue(String configKey) {
        try {
            SysConfigEntity config = sysConfigMapper.selectByKey(configKey);
            return config != null ? config.getConfigValue() : null;
        } catch (Exception e) {
            log.error("根据键获取配置值失败：configKey={}", configKey, e);
            return null;
        }
    }

    @Override
    public void updateConfig(SysConfigEntity config) {
        try {
            config.setUpdateTime(LocalDateTime.now());
            int result = sysConfigMapper.updateById(config);
            if (result > 0) {
                eventPublisher.publishEvent(new ConfigUpdateEvent(this, config.getConfigKey(), config.getConfigValue()));
                log.info("更新配置成功：configKey={}", config.getConfigKey());
            } else {
                log.warn("更新配置失败，未找到记录：configKey={}", config.getConfigKey());
            }
        } catch (Exception e) {
            log.error("更新配置失败：configKey={}", config.getConfigKey(), e);
            throw new RuntimeException("更新配置失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void updateConfigValue(String configKey, String configValue) {
        try {
            int result = sysConfigMapper.updateByKey(configKey, configValue);
            if (result > 0) {
                eventPublisher.publishEvent(new ConfigUpdateEvent(this, configKey, configValue));
                log.info("更新配置值成功：configKey={}, configValue={}", configKey, configValue);
            } else {
                log.warn("更新配置值失败，未找到记录：configKey={}", configKey);
            }
        } catch (Exception e) {
            log.error("更新配置值失败：configKey={}", configKey, e);
            throw new RuntimeException("更新配置值失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void addConfig(SysConfigEntity config) {
        try {
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
            sysConfigMapper.insert(config);
            log.info("添加配置成功：configKey={}, configValue={}", config.getConfigKey(), config.getConfigValue());
        } catch (Exception e) {
            log.error("添加配置失败：configKey={}", config.getConfigKey(), e);
            throw new RuntimeException("添加配置失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void deleteConfig(Long id) {
        try {
            int result = sysConfigMapper.deleteById(id);
            if (result > 0) {
                log.info("删除配置成功：id={}", id);
            } else {
                log.warn("删除配置失败，未找到记录：id={}", id);
            }
        } catch (Exception e) {
            log.error("删除配置失败：id={}", id, e);
            throw new RuntimeException("删除配置失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void refreshCache() {
        eventPublisher.publishEvent(new ConfigRefreshEvent(this));
        log.info("发布配置刷新事件成功");
    }
}
