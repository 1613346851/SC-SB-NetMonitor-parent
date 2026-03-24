package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.GatewayConfigDTO;
import com.network.monitor.service.AuthService;
import com.network.monitor.service.GatewayConfigService;
import com.network.monitor.service.OperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/gateway")
public class GatewayConfigManageController {

    @Autowired
    private GatewayConfigService gatewayConfigService;

    @Autowired
    private AuthService authService;

    @Autowired
    private OperLogService operLogService;

    @GetMapping("/config/health")
    public ApiResponse<Map<String, Object>> checkGatewayHealth(HttpServletRequest request) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("configCount", gatewayConfigService.getGatewayConfigCount());
            result.put("timestamp", System.currentTimeMillis());
            
            operLogService.logOperation(authService.getCurrentUsername(), "QUERY", "网关配置", 
                "检查网关健康状态", "health", "/api/gateway/config/health", getClientIp(request), 0);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("检查网关健康状态失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ApiResponse.success(result);
        }
    }

    @PostMapping("/config/push")
    public ApiResponse<Void> pushConfig(@RequestBody GatewayConfigDTO dto, HttpServletRequest request) {
        try {
            if (dto.getConfigKey() == null || dto.getConfigKey().isEmpty()) {
                return ApiResponse.badRequest("配置键不能为空");
            }
            if (dto.getConfigValue() == null) {
                return ApiResponse.badRequest("配置值不能为空");
            }

            log.info("推送配置到网关：configKey={}", dto.getConfigKey());
            boolean success = gatewayConfigService.pushConfigToGateway(dto.getConfigKey(), dto.getConfigValue());
            
            if (success) {
                operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "网关配置", 
                    "推送配置到网关：" + dto.getConfigKey(), "push", "/api/gateway/config/push", getClientIp(request), 0);
                return ApiResponse.success();
            } else {
                return ApiResponse.error("推送配置失败");
            }
        } catch (Exception e) {
            log.error("推送配置到网关失败", e);
            return ApiResponse.error("推送配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/config/sync")
    public ApiResponse<Void> syncConfigs(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> configs = (Map<String, String>) body.get("configs");
            
            if (configs == null || configs.isEmpty()) {
                return ApiResponse.badRequest("配置数据不能为空");
            }

            log.info("批量同步配置到网关，共{}项", configs.size());
            
            for (Map.Entry<String, String> entry : configs.entrySet()) {
                gatewayConfigService.pushConfigToGateway(entry.getKey(), entry.getValue());
            }
            
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "网关配置", 
                "批量同步配置到网关，共" + configs.size() + "项", "sync", "/api/gateway/config/sync", getClientIp(request), 0);
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("批量同步配置失败", e);
            return ApiResponse.error("同步配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/config/refresh")
    public ApiResponse<Void> refreshConfigs(HttpServletRequest request) {
        try {
            log.info("刷新并推送所有网关配置");
            gatewayConfigService.refreshGatewayConfigCache();
            
            operLogService.logOperation(authService.getCurrentUsername(), "UPDATE", "网关配置", 
                "刷新并推送所有网关配置", "refresh", "/api/gateway/config/refresh", getClientIp(request), 0);
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("刷新网关配置失败", e);
            return ApiResponse.error("刷新配置失败: " + e.getMessage());
        }
    }

    @GetMapping("/config/list")
    public ApiResponse<List<Map<String, Object>>> getGatewayConfigList(HttpServletRequest request) {
        try {
            List<Map<String, Object>> configList = gatewayConfigService.getGatewayConfigList();
            
            operLogService.logOperation(authService.getCurrentUsername(), "QUERY", "网关配置", 
                "查询网关配置列表", "list", "/api/gateway/config/list", getClientIp(request), 0);
            
            return ApiResponse.success(configList);
        } catch (Exception e) {
            log.error("获取网关配置列表失败", e);
            return ApiResponse.error("获取配置列表失败: " + e.getMessage());
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
