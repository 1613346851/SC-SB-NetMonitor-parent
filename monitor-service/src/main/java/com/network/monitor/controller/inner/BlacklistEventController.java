package com.network.monitor.controller.inner;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.BlacklistEventDTO;
import com.network.monitor.service.BlacklistManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@RestController
@RequestMapping("/api/inner/blacklist")
public class BlacklistEventController {

    @Autowired
    private BlacklistManageService blacklistManageService;

    @PostMapping("/event")
    public ApiResponse<Void> receiveBlacklistEvent(@RequestBody BlacklistEventDTO event) {
        try {
            log.info("接收到黑名单事件: ip={}, banType={}, reason={}, duration={}, eventId={}", 
                event.getIp(), event.getBanType(), event.getBanReason(), event.getDuration(), event.getEventId());
            
            validateEvent(event);
            
            LocalDateTime expireTime = calculateExpireTime(event);
            
            String operator = event.getOperator() != null ? event.getOperator() : "SYSTEM";
            
            blacklistManageService.addToBlacklist(
                event.getIp(),
                event.getBanReason(),
                expireTime,
                operator,
                event.getEventId()
            );
            
            log.info("黑名单事件处理成功: ip={}, expireTime={}", event.getIp(), expireTime);
            
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            log.warn("黑名单事件参数无效: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("处理黑名单事件失败: ip={}", event.getIp(), e);
            return ApiResponse.error("处理黑名单事件失败: " + e.getMessage());
        }
    }

    @PostMapping("/event/batch")
    public ApiResponse<Integer> receiveBlacklistEvents(@RequestBody java.util.List<BlacklistEventDTO> events) {
        try {
            log.info("接收到批量黑名单事件: count={}", events.size());
            
            int successCount = 0;
            for (BlacklistEventDTO event : events) {
                try {
                    LocalDateTime expireTime = calculateExpireTime(event);
                    String operator = event.getOperator() != null ? event.getOperator() : "SYSTEM";
                    
                    blacklistManageService.addToBlacklist(
                        event.getIp(),
                        event.getBanReason(),
                        expireTime,
                        operator,
                        event.getEventId()
                    );
                    successCount++;
                } catch (Exception e) {
                    log.warn("处理单个黑名单事件失败: ip={}, error={}", event.getIp(), e.getMessage());
                }
            }
            
            log.info("批量黑名单事件处理完成: total={}, success={}", events.size(), successCount);
            
            return ApiResponse.success(successCount);
        } catch (Exception e) {
            log.error("批量处理黑名单事件失败", e);
            return ApiResponse.error("批量处理黑名单事件失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/event/{ip}")
    public ApiResponse<Void> removeBlacklistEvent(@PathVariable String ip) {
        try {
            log.info("接收到移除黑名单请求: ip={}", ip);
            
            blacklistManageService.removeFromBlacklist(ip);
            
            log.info("移除黑名单成功: ip={}", ip);
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("移除黑名单失败: ip={}", ip, e);
            return ApiResponse.error("移除黑名单失败: " + e.getMessage());
        }
    }

    @GetMapping("/event/check/{ip}")
    public ApiResponse<Boolean> checkBlacklist(@PathVariable String ip) {
        try {
            boolean inBlacklist = blacklistManageService.isInBlacklist(ip);
            return ApiResponse.success(inBlacklist);
        } catch (Exception e) {
            log.error("检查黑名单失败: ip={}", ip, e);
            return ApiResponse.error("检查黑名单失败: " + e.getMessage());
        }
    }

    private void validateEvent(BlacklistEventDTO event) {
        if (event == null) {
            throw new IllegalArgumentException("黑名单事件不能为空");
        }
        if (event.getIp() == null || event.getIp().isEmpty()) {
            throw new IllegalArgumentException("IP地址不能为空");
        }
        if (event.getBanReason() == null || event.getBanReason().isEmpty()) {
            throw new IllegalArgumentException("封禁原因不能为空");
        }
    }

    private LocalDateTime calculateExpireTime(BlacklistEventDTO event) {
        if (event.isPermanent()) {
            return null;
        }
        
        long durationSeconds = event.getDuration();
        if (event.getTimestamp() != null) {
            return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(event.getTimestamp()),
                ZoneId.systemDefault()
            ).plusSeconds(durationSeconds);
        }
        
        return LocalDateTime.now().plusSeconds(durationSeconds);
    }
}
