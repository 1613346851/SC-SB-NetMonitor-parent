package com.network.monitor.controller.inner;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.GatewayConfigDTO;
import com.network.monitor.service.GatewayConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 网关配置同步控制器（对内跨服务接口）
 * 提供网关配置的拉取、推送和刷新功能
 * 安全验证由 CrossServiceSecurityInterceptor 统一处理
 */
@Slf4j
@RestController
@RequestMapping("/api/inner/gateway/config")
public class GatewayConfigController {

    @Autowired
    private GatewayConfigService gatewayConfigService;

    /**
     * 网关拉取全部配置
     * 网关启动时调用此接口获取所有配置
     * 
     * @return 所有网关配置
     */
    @GetMapping("/sync")
    public ApiResponse<Map<String, Object>> syncConfigs() {
        try {
            Map<String, Object> configs = gatewayConfigService.getAllGatewayConfigs();
            log.info("网关拉取配置成功，共{}项", configs.size());
            return ApiResponse.success(configs);
        } catch (Exception e) {
            log.error("网关拉取配置失败", e);
            return ApiResponse.error("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 推送配置到网关
     * 支持单个配置推送和批量配置推送
     * 
     * @param dto 配置数据传输对象
     * @return 操作结果
     */
    @PostMapping("/push")
    public ApiResponse<Void> pushConfigToGateway(@RequestBody GatewayConfigDTO dto) {
        try {
            if (!dto.isValid()) {
                return ApiResponse.badRequest("无效的配置数据");
            }

            boolean success;
            if (dto.isBatchConfig()) {
                log.info("批量推送配置到网关，共{}项", dto.getConfigs().size());
                success = gatewayConfigService.pushAllConfigsToGateway();
            } else {
                log.info("推送单个配置到网关：configKey={}", dto.getConfigKey());
                success = gatewayConfigService.pushConfigToGateway(dto.getConfigKey(), dto.getConfigValue());
            }

            if (success) {
                return ApiResponse.success();
            } else {
                return ApiResponse.error("推送配置失败");
            }
        } catch (Exception e) {
            log.error("推送配置到网关失败", e);
            return ApiResponse.error("推送配置失败: " + e.getMessage());
        }
    }

    /**
     * 刷新网关配置缓存
     * 重新从数据库加载配置并推送到网关
     * 
     * @return 操作结果
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refreshConfig() {
        try {
            log.info("刷新网关配置缓存");
            gatewayConfigService.refreshGatewayConfigCache();
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("刷新网关配置缓存失败", e);
            return ApiResponse.error("刷新配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取网关配置项列表（带描述）
     * 用于前端配置管理页面展示
     * 
     * @return 配置项列表
     */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> getGatewayConfigList() {
        try {
            List<Map<String, Object>> configList = gatewayConfigService.getGatewayConfigList();
            return ApiResponse.success(configList);
        } catch (Exception e) {
            log.error("获取网关配置列表失败", e);
            return ApiResponse.error("获取配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 更新单个网关配置
     * 更新数据库配置并推送到网关
     * 
     * @param dto 配置数据传输对象
     * @return 操作结果
     */
    @PutMapping("/update")
    public ApiResponse<Void> updateConfig(@RequestBody GatewayConfigDTO dto) {
        try {
            if (dto.getConfigKey() == null || dto.getConfigKey().isEmpty()) {
                return ApiResponse.badRequest("配置键不能为空");
            }
            if (dto.getConfigValue() == null) {
                return ApiResponse.badRequest("配置值不能为空");
            }

            log.info("更新网关配置：configKey={}, configValue={}", dto.getConfigKey(), dto.getConfigValue());
            gatewayConfigService.updateConfig(dto.getConfigKey(), dto.getConfigValue());
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            log.warn("更新网关配置参数错误：{}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("更新网关配置失败", e);
            return ApiResponse.error("更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个配置值
     * 
     * @param configKey 配置键
     * @return 配置值
     */
    @GetMapping("/value/{configKey}")
    public ApiResponse<String> getConfigValue(@PathVariable String configKey) {
        try {
            String value = gatewayConfigService.getConfigValue(configKey, null);
            if (value == null) {
                return ApiResponse.notFound("配置项不存在: " + configKey);
            }
            return ApiResponse.success(value);
        } catch (Exception e) {
            log.error("获取配置值失败：configKey={}", configKey, e);
            return ApiResponse.error("获取配置值失败: " + e.getMessage());
        }
    }
}
