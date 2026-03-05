package com.network.monitor.controller.inner;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.DefenseLogDTO;
import com.network.monitor.service.DefenseLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 防御日志接收控制器（对内跨服务接口）
 */
@Slf4j
@RestController
@RequestMapping("/api/inner/defense")
public class DefenseLogReceiveController {

    @Autowired
    private DefenseLogService defenseLogService;

    /**
     * 接收网关同步的防御日志
     */
    @PostMapping("/log/sync")
    public ApiResponse<Void> syncDefenseLog(@RequestBody DefenseLogDTO logDTO) {
        try {
            log.info("接收到防御日志：attackId={}, defenseType={}, executeStatus={}", 
                logDTO.getAttackId(), logDTO.getDefenseType(), logDTO.getExecuteStatus());
            
            defenseLogService.receiveDefenseLog(logDTO);
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("处理防御日志失败：", e);
            return ApiResponse.error("处理防御日志失败");
        }
    }
}
