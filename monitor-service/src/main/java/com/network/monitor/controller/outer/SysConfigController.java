package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.SysConfigEntity;
import com.network.monitor.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/config")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    @GetMapping("/list")
    public ApiResponse<List<SysConfigEntity>> getAllConfigs() {
        try {
            List<SysConfigEntity> list = sysConfigService.getAllConfigs();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("获取所有配置失败", e);
            return ApiResponse.error("获取配置列表失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<SysConfigEntity> getConfigById(@PathVariable Long id) {
        try {
            SysConfigEntity config = sysConfigService.getConfigById(id);
            if (config == null) {
                return ApiResponse.notFound("配置不存在");
            }
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("根据ID获取配置失败：id={}", id, e);
            return ApiResponse.error("获取配置失败");
        }
    }

    @GetMapping("/key/{configKey}")
    public ApiResponse<SysConfigEntity> getConfigByKey(@PathVariable String configKey) {
        try {
            SysConfigEntity config = sysConfigService.getConfigByKey(configKey);
            if (config == null) {
                return ApiResponse.notFound("配置不存在");
            }
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("根据键获取配置失败：configKey={}", configKey, e);
            return ApiResponse.error("获取配置失败");
        }
    }

    @GetMapping("/value/{configKey}")
    public ApiResponse<String> getConfigValue(@PathVariable String configKey) {
        try {
            String value = sysConfigService.getConfigValue(configKey);
            if (value == null) {
                return ApiResponse.notFound("配置不存在");
            }
            return ApiResponse.success(value);
        } catch (Exception e) {
            log.error("根据键获取配置值失败：configKey={}", configKey, e);
            return ApiResponse.error("获取配置值失败");
        }
    }

    @PostMapping("/update")
    public ApiResponse<Void> updateConfig(@RequestBody SysConfigEntity config) {
        try {
            if (config.getId() == null) {
                return ApiResponse.error("配置ID不能为空");
            }
            sysConfigService.updateConfig(config);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("更新配置失败：configKey={}", config.getConfigKey(), e);
            return ApiResponse.error("更新配置失败：" + e.getMessage());
        }
    }

    @PostMapping("/update-value")
    public ApiResponse<Void> updateConfigValue(@RequestParam String configKey, @RequestParam String configValue) {
        try {
            sysConfigService.updateConfigValue(configKey, configValue);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("更新配置值失败：configKey={}", configKey, e);
            return ApiResponse.error("更新配置值失败：" + e.getMessage());
        }
    }

    @PostMapping("/add")
    public ApiResponse<Void> addConfig(@RequestBody SysConfigEntity config) {
        try {
            if (config.getConfigKey() == null || config.getConfigKey().isEmpty()) {
                return ApiResponse.error("配置键不能为空");
            }
            if (config.getConfigValue() == null || config.getConfigValue().isEmpty()) {
                return ApiResponse.error("配置值不能为空");
            }
            sysConfigService.addConfig(config);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("添加配置失败：configKey={}", config.getConfigKey(), e);
            return ApiResponse.error("添加配置失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        try {
            sysConfigService.deleteConfig(id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除配置失败：id={}", id, e);
            return ApiResponse.error("删除配置失败：" + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<Void> refreshConfig() {
        try {
            sysConfigService.refreshCache();
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("刷新配置缓存失败：", e);
            return ApiResponse.error("刷新配置失败");
        }
    }
}