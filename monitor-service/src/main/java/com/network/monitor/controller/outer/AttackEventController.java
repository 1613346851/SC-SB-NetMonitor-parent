package com.network.monitor.controller.outer;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.entity.AttackEventEntity;
import com.network.monitor.service.AttackEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/event")
public class AttackEventController {

    @Autowired
    private AttackEventService attackEventService;

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getEventList(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        try {
            int offset = (pageNum - 1) * pageSize;
            
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);
            String orderBy = buildOrderBy(sortField, sortOrder);
            
            List<AttackEventEntity> list = attackEventService.getEventsByCondition(
                eventId, sourceIp, attackType, riskLevel, status, 
                startDateTime, endDateTime, offset, pageSize, orderBy
            );
            
            long total = attackEventService.countEventsByCondition(
                eventId, sourceIp, attackType, riskLevel, status, 
                startDateTime, endDateTime
            );

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询攻击事件失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<AttackEventEntity> getEventById(@PathVariable Long id) {
        try {
            AttackEventEntity event = attackEventService.getEventById(id);
            if (event == null) {
                return ApiResponse.error("事件不存在");
            }
            return ApiResponse.success(event);
        } catch (Exception e) {
            log.error("查询攻击事件失败：id={}", id, e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/eventId/{eventId}")
    public ApiResponse<AttackEventEntity> getEventByEventId(@PathVariable String eventId) {
        try {
            AttackEventEntity event = attackEventService.getEventByEventId(eventId);
            if (event == null) {
                return ApiResponse.error("事件不存在");
            }
            return ApiResponse.success(event);
        } catch (Exception e) {
            log.error("查询攻击事件失败：eventId={}", eventId, e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/ip/{sourceIp}")
    public ApiResponse<List<AttackEventEntity>> getEventsByIp(@PathVariable String sourceIp) {
        try {
            List<AttackEventEntity> events = attackEventService.getEventsByIp(sourceIp);
            return ApiResponse.success(events);
        } catch (Exception e) {
            log.error("查询IP攻击事件失败：sourceIp={}", sourceIp, e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/recent")
    public ApiResponse<List<AttackEventEntity>> getRecentEvents(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<AttackEventEntity> events = attackEventService.getRecentEvents(limit);
            return ApiResponse.success(events);
        } catch (Exception e) {
            log.error("查询最近攻击事件失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/ongoing/count")
    public ApiResponse<Integer> getOngoingEventCount() {
        try {
            int count = attackEventService.getOngoingEventCount();
            return ApiResponse.success(count);
        } catch (Exception e) {
            log.error("查询进行中事件数量失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getEventStatistics() {
        try {
            Map<String, Object> stats = attackEventService.getEventStatistics();
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("查询事件统计失败：", e);
            return ApiResponse.error("查询失败");
        }
    }

    @PutMapping("/{id}/end")
    public ApiResponse<Void> markEventAsEnded(@PathVariable Long id) {
        try {
            attackEventService.markEventAsEnded(id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("标记事件结束失败：id={}", id, e);
            return ApiResponse.error("操作失败");
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    private String buildOrderBy(String sortField, String sortOrder) {
        String field = mapSortField(sortField);
        String order = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        return field + " " + order;
    }

    private String mapSortField(String sortField) {
        return switch (sortField) {
            case "id" -> "id";
            case "startTime" -> "start_time";
            case "endTime" -> "end_time";
            case "sourceIp" -> "source_ip";
            case "attackType" -> "attack_type";
            case "riskLevel" -> "risk_level";
            case "totalRequests" -> "total_requests";
            case "peakRps" -> "peak_rps";
            default -> "id";
        };
    }
}
