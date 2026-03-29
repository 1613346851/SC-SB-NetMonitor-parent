package com.network.gateway.metrics;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayAlertService {

    private final AlertRuleConfig alertRuleConfig;
    private final GatewayMetricsService metricsService;
    
    private final Map<String, AlertState> alertStates = new ConcurrentHashMap<>();
    private final AtomicLong alertCounter = new AtomicLong(0);

    public List<AlertEvent> evaluateAlerts() {
        List<AlertEvent> alerts = new ArrayList<>();
        
        if (!alertRuleConfig.isEnabled()) {
            return alerts;
        }

        for (AlertRuleConfig.AlertRule rule : alertRuleConfig.getEnabledRules()) {
            try {
                AlertEvent alert = evaluateRule(rule);
                if (alert != null) {
                    alerts.add(alert);
                }
            } catch (Exception e) {
                log.error("Error evaluating alert rule: {}", rule.getName(), e);
            }
        }
        
        return alerts;
    }

    private AlertEvent evaluateRule(AlertRuleConfig.AlertRule rule) {
        double currentValue = getMetricValue(rule.getMetric());
        boolean conditionMet = evaluateCondition(currentValue, rule.getOperator(), rule.getThreshold());
        
        String ruleName = rule.getName();
        AlertState state = alertStates.computeIfAbsent(ruleName, k -> new AlertState());
        
        if (conditionMet) {
            state.incrementTriggerCount();
            state.setLastTriggerTime(Instant.now());
            
            long triggerDuration = state.getTriggerDurationSeconds();
            if (triggerDuration >= rule.getDurationSeconds()) {
                if (!state.isFiring()) {
                    state.setFiring(true);
                    state.setFiringStartTime(Instant.now());
                    
                    AlertEvent alert = createAlertEvent(rule, currentValue, state);
                    log.warn("Alert triggered: {} - {}", rule.getName(), rule.getMessage());
                    return alert;
                }
            }
        } else {
            if (state.isFiring()) {
                state.setFiring(false);
                state.setFiringStartTime(null);
                log.info("Alert resolved: {}", rule.getName());
            }
            state.resetTriggerCount();
        }
        
        return null;
    }

    private double getMetricValue(String metric) {
        GatewayMetrics.MetricsSnapshot snapshot = metricsService.getMetricsSnapshot();
        
        return switch (metric) {
            case "gateway_blocked_requests_total" -> snapshot.getTotalBlocked();
            case "gateway_error_requests_total" -> snapshot.getTotalErrors();
            case "gateway_traffic_push_failure_total" -> snapshot.getTrafficPushFailure();
            case "gateway_defense_log_push_failure_total" -> snapshot.getDefenseLogPushFailure();
            case "gateway_attacking_state_count" -> snapshot.getAttackingStateCount();
            case "gateway_defended_state_count" -> snapshot.getDefendedStateCount();
            case "gateway_state_transitions_total" -> snapshot.getStateTransitions();
            default -> 0;
        };
    }

    private boolean evaluateCondition(double value, String operator, double threshold) {
        return switch (operator) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "==" -> value == threshold;
            case "!=" -> value != threshold;
            default -> false;
        };
    }

    private AlertEvent createAlertEvent(AlertRuleConfig.AlertRule rule, double currentValue, AlertState state) {
        AlertEvent event = new AlertEvent();
        event.setId(alertCounter.incrementAndGet());
        event.setName(rule.getName());
        event.setType(rule.getType());
        event.setMetric(rule.getMetric());
        event.setThreshold(rule.getThreshold());
        event.setCurrentValue(currentValue);
        event.setSeverity(rule.getSeverity());
        event.setMessage(rule.getMessage());
        event.setFiringTime(Instant.now());
        event.setTriggerCount(state.getTriggerCount());
        return event;
    }

    public List<AlertState> getAlertStates() {
        return new ArrayList<>(alertStates.values());
    }

    public void resetAlertState(String ruleName) {
        alertStates.remove(ruleName);
        log.info("Alert state reset for rule: {}", ruleName);
    }

    public void resetAllAlertStates() {
        alertStates.clear();
        log.info("All alert states reset");
    }

    @Data
    public static class AlertState implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int triggerCount = 0;
        private Instant firstTriggerTime;
        private Instant lastTriggerTime;
        private boolean firing = false;
        private Instant firingStartTime;

        public void incrementTriggerCount() {
            if (triggerCount == 0) {
                firstTriggerTime = Instant.now();
            }
            triggerCount++;
        }

        public void resetTriggerCount() {
            triggerCount = 0;
            firstTriggerTime = null;
        }

        public long getTriggerDurationSeconds() {
            if (firstTriggerTime == null || lastTriggerTime == null) {
                return 0;
            }
            return java.time.Duration.between(firstTriggerTime, lastTriggerTime).getSeconds();
        }
    }

    @Data
    public static class AlertEvent implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private long id;
        private String name;
        private String type;
        private String metric;
        private double threshold;
        private double currentValue;
        private String severity;
        private String message;
        private Instant firingTime;
        private int triggerCount;
    }
}
