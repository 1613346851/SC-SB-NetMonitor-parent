package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.SysConfigEntity;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.OperLogService;
import com.network.monitor.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private AuthService authService;

    @Autowired
    private OperLogService operLogService;

    @GetMapping("/alert.sound")
    public ApiResponse<Map<String, String>> getAlertSoundConfig() {
        try {
            Map<String, String> config = new HashMap<>();
            config.put("alert.sound.enabled", sysConfigService.getConfigValue("alert.sound.enabled"));
            config.put("alert.sound.level-threshold", sysConfigService.getConfigValue("alert.sound.level-threshold"));
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("获取告警声音配置失败", e);
            return ApiResponse.error("获取配置失败");
        }
    }

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
    public ApiResponse<Void> updateConfig(@RequestBody SysConfigEntity config, HttpServletRequest request) {
        try {
            if (config.getId() == null) {
                return ApiResponse.error("配置ID不能为空");
            }
            
            SysConfigEntity oldConfig = sysConfigService.getConfigById(config.getId());
            if (oldConfig == null) {
                return ApiResponse.error("配置不存在");
            }
            
            String oldValue = oldConfig.getConfigValue();
            String newValue = config.getConfigValue();
            
            sysConfigService.updateConfig(config);
            
            String logContent;
            if (oldValue != null && oldValue.equals(newValue)) {
                logContent = "更新配置：" + config.getConfigKey() + "（值未变更）";
            } else {
                logContent = "更新配置：" + config.getConfigKey() + "，值从【" + (oldValue != null ? oldValue : "空") + "】变更为【" + (newValue != null ? newValue : "空") + "】";
            }
            
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "系统配置", 
                logContent, "update", "/api/config/update", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("更新配置失败：configKey={}", config.getConfigKey(), e);
            return ApiResponse.error("更新配置失败：" + e.getMessage());
        }
    }

    @PostMapping("/update-value")
    public ApiResponse<Void> updateConfigValue(@RequestParam String configKey, @RequestParam String configValue, HttpServletRequest request) {
        try {
            String oldValue = sysConfigService.getConfigValue(configKey);
            
            sysConfigService.updateConfigValue(configKey, configValue);
            
            String logContent;
            if (oldValue != null && oldValue.equals(configValue)) {
                logContent = "更新配置值：" + configKey + "（值未变更）";
            } else {
                logContent = "更新配置值：" + configKey + "，值从【" + (oldValue != null ? oldValue : "空") + "】变更为【" + configValue + "】";
            }
            
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "系统配置", 
                logContent, "update-value", "/api/config/update-value", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("更新配置值失败：configKey={}", configKey, e);
            return ApiResponse.error("更新配置值失败：" + e.getMessage());
        }
    }

    @PostMapping("/add")
    public ApiResponse<Void> addConfig(@RequestBody SysConfigEntity config, HttpServletRequest request) {
        try {
            if (config.getConfigKey() == null || config.getConfigKey().isEmpty()) {
                return ApiResponse.error("配置键不能为空");
            }
            if (config.getConfigValue() == null || config.getConfigValue().isEmpty()) {
                return ApiResponse.error("配置值不能为空");
            }
            sysConfigService.addConfig(config);
            operLogService.logOperation(authService.getCurrentUsername(), "INSERT", "系统配置", 
                "新增配置：" + config.getConfigKey() + "，值为【" + config.getConfigValue() + "】", "add", "/api/config/add", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("添加配置失败：configKey={}", config.getConfigKey(), e);
            return ApiResponse.error("添加配置失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id, HttpServletRequest request) {
        try {
            SysConfigEntity config = sysConfigService.getConfigById(id);
            if (config == null) {
                return ApiResponse.error("配置不存在");
            }
            
            sysConfigService.deleteConfig(id);
            operLogService.logOperation(authService.getCurrentUsername(), "DELETE", "系统配置", 
                "删除配置：" + config.getConfigKey() + "，值为【" + (config.getConfigValue() != null ? config.getConfigValue() : "空") + "】", "delete", "/api/config/" + id, getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除配置失败：id={}", id, e);
            return ApiResponse.error("删除配置失败：" + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<Void> refreshConfig(HttpServletRequest request) {
        try {
            sysConfigService.refreshCache();
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "系统配置", 
                "刷新配置缓存", "refresh", "/api/config/refresh", getClientIp(request), 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("刷新配置缓存失败：", e);
            return ApiResponse.error("刷新配置失败");
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}