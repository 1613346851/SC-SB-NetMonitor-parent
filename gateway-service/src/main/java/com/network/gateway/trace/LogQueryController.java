package com.network.gateway.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inner/logs")
@RequiredArgsConstructor
public class LogQueryController {

    private final LogQueryService logQueryService;

    @GetMapping("/trace/{traceId}")
    public ResponseEntity<Map<String, Object>> queryByTraceId(
            @PathVariable String traceId,
            @RequestParam(defaultValue = "100") int limit) {
        
        LogQueryService.LogQueryResult result = logQueryService.queryByTraceId(traceId, limit);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("traceId", traceId);
        response.put("totalCount", result.getTotalCount());
        response.put("entries", result.getEntries());
        
        if (!result.isSuccess()) {
            response.put("message", result.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ip/{ip}")
    public ResponseEntity<Map<String, Object>> queryByIp(
            @PathVariable String ip,
            @RequestParam(defaultValue = "100") int limit) {
        
        LogQueryService.LogQueryResult result = logQueryService.queryByIp(ip, limit);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("ip", ip);
        response.put("totalCount", result.getTotalCount());
        response.put("entries", result.getEntries());
        
        if (!result.isSuccess()) {
            response.put("message", result.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/timerange")
    public ResponseEntity<Map<String, Object>> queryByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "100") int limit) {
        
        LogQueryService.LogQueryResult result = logQueryService.queryByTimeRange(startTime, endTime, limit);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("startTime", startTime);
        response.put("endTime", endTime);
        response.put("totalCount", result.getTotalCount());
        response.put("entries", result.getEntries());
        
        if (!result.isSuccess()) {
            response.put("message", result.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/level/{level}")
    public ResponseEntity<Map<String, Object>> queryByLevel(
            @PathVariable String level,
            @RequestParam(defaultValue = "100") int limit) {
        
        LogQueryService.LogQueryResult result = logQueryService.queryByLevel(level, limit);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("level", level.toUpperCase());
        response.put("totalCount", result.getTotalCount());
        response.put("entries", result.getEntries());
        
        if (!result.isSuccess()) {
            response.put("message", result.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> queryByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "100") int limit) {
        
        LogQueryService.LogQueryResult result = logQueryService.queryByKeyword(keyword, limit);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("keyword", keyword);
        response.put("totalCount", result.getTotalCount());
        response.put("entries", result.getEntries());
        
        if (!result.isSuccess()) {
            response.put("message", result.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getLogStats() {
        LogQueryService.LogStats stats = logQueryService.getLogStats();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("stats", stats);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getLogHealth() {
        LogQueryService.LogStats stats = logQueryService.getLogStats();
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", stats.getErrorCount() > 100 ? "WARNING" : "UP");
        health.put("logFileSizeBytes", stats.getFileSizeBytes());
        health.put("totalLines", stats.getTotalLines());
        health.put("errorCount", stats.getErrorCount());
        health.put("warnCount", stats.getWarnCount());
        health.put("infoCount", stats.getInfoCount());
        health.put("debugCount", stats.getDebugCount());
        
        return ResponseEntity.ok(health);
    }
}
