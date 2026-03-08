package com.network.monitor.controller.inner;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.DefenseLogDTO;
import com.network.monitor.service.DefenseLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 防御日志接收控制器（对内跨服务接口）
 */
@Slf4j
@RestController
@RequestMapping("/api/inner/defense")
public class DefenseLogReceiveController {

    @Value("${cross-service.auth.verify-token:MonitorSecureToken456}")
    private String expectedToken;

    @Autowired
    private DefenseLogService defenseLogService;

    /**
     * 接收网关同步的防御日志
     * 添加跨服务鉴权验证，确保数据来源合法
     */
    @PostMapping("/log/sync")
    public ApiResponse<Void> syncDefenseLog(
            @RequestBody DefenseLogDTO logDTO,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken) {
        try {
            // 验证跨服务鉴权 Token
            if (!validateCrossServiceToken(authToken)) {
                log.warn("接收到未授权的防御日志推送请求：token={}", authToken);
                return ApiResponse.error("未授权访问");
            }

            log.info("接收到防御日志：attackId={}, defenseType={}, executeStatus={}", 
                logDTO.getAttackId(), logDTO.getDefenseType(), logDTO.getExecuteStatus());
            
            defenseLogService.receiveDefenseLog(logDTO);
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("处理防御日志失败：", e);
            return ApiResponse.error("处理防御日志失败");
        }
    }

    /**
     * 验证跨服务鉴权 Token
     */
    private boolean validateCrossServiceToken(String authToken) {
        return expectedToken != null && expectedToken.equals(authToken);
    }
}
