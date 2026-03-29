package com.network.gateway.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inner/metrics")
@RequiredArgsConstructor
public class GatewayMetricsController {

    private final GatewayMetricsService metricsService;
    private final GatewayAlertService alertService;
    private final AlertRuleConfig alertRuleConfig;

    @GetMapping("/snapshot")
    public ResponseEntity<GatewayMetrics.MetricsSnapshot> getMetricsSnapshot() {
        return ResponseEntity.ok(metricsService.getMetricsSnapshot());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("summary", metricsService.getMetricsSummary());
        summary.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<GatewayAlertService.AlertEvent>> evaluateAlerts() {
        return ResponseEntity.ok(alertService.evaluateAlerts());
    }

    @GetMapping("/alerts/states")
    public ResponseEntity<List<GatewayAlertService.AlertState>> getAlertStates() {
        return ResponseEntity.ok(alertService.getAlertStates());
    }

    @GetMapping("/alerts/rules")
    public ResponseEntity<List<AlertRuleConfig.AlertRule>> getAlertRules() {
        return ResponseEntity.ok(alertRuleConfig.getRules());
    }

    @PostMapping("/alerts/{ruleName}/reset")
    public ResponseEntity<Map<String, Object>> resetAlertState(@PathVariable String ruleName) {
        alertService.resetAlertState(ruleName);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Alert state reset for rule: " + ruleName);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/alerts/reset-all")
    public ResponseEntity<Map<String, Object>> resetAllAlertStates() {
        alertService.resetAllAlertStates();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "All alert states reset");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health/detail")
    public ResponseEntity<Map<String, Object>> getDetailedHealth() {
        GatewayMetrics.MetricsSnapshot snapshot = metricsService.getMetricsSnapshot();
        List<GatewayAlertService.AlertEvent> alerts = alertService.evaluateAlerts();
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", alerts.isEmpty() ? "UP" : "WARNING");
        health.put("uptime", snapshot.getUptime());
        health.put("totalRequests", snapshot.getTotalRequests());
        health.put("blockRate", String.format("%.2f%%", snapshot.getBlockRate()));
        health.put("errorRate", String.format("%.2f%%", snapshot.getErrorRate()));
        health.put("stateDistribution", Map.of(
            "normal", snapshot.getNormalStateCount(),
            "suspicious", snapshot.getSuspiciousStateCount(),
            "attacking", snapshot.getAttackingStateCount(),
            "defended", snapshot.getDefendedStateCount(),
            "cooldown", snapshot.getCooldownStateCount()
        ));
        health.put("pushStats", Map.of(
            "trafficSuccess", snapshot.getTrafficPushSuccess(),
            "trafficFailure", snapshot.getTrafficPushFailure(),
            "defenseLogSuccess", snapshot.getDefenseLogPushSuccess(),
            "defenseLogFailure", snapshot.getDefenseLogPushFailure()
        ));
        health.put("activeAlerts", alerts.size());
        health.put("alerts", alerts);
        
        return ResponseEntity.ok(health);
    }
}
