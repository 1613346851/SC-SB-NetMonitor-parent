package com.network.gateway.metrics;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.alert")
public class AlertRuleConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = true;
    private List<AlertRule> rules = new ArrayList<>();

    @Data
    public static class AlertRule implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private String type;
        private String metric;
        private String operator;
        private double threshold;
        private int durationSeconds = 60;
        private String severity = "WARNING";
        private String message;
        private boolean enabled = true;
    }

    public AlertRuleConfig() {
        initDefaultRules();
    }

    private void initDefaultRules() {
        rules.add(createRule(
            "high_block_rate",
            "BLOCK_RATE",
            "gateway_blocked_requests_total",
            ">",
            100,
            60,
            "WARNING",
            "封禁请求数量过高，可能存在攻击行为"
        ));

        rules.add(createRule(
            "high_error_rate",
            "ERROR_RATE",
            "gateway_error_requests_total",
            ">",
            50,
            60,
            "WARNING",
            "错误请求率过高，请检查系统状态"
        ));

        rules.add(createRule(
            "traffic_push_failure",
            "PUSH_FAILURE",
            "gateway_traffic_push_failure_total",
            ">",
            10,
            300,
            "ERROR",
            "流量推送失败次数过多，请检查监测服务状态"
        ));

        rules.add(createRule(
            "defense_log_push_failure",
            "PUSH_FAILURE",
            "gateway_defense_log_push_failure_total",
            ">",
            10,
            300,
            "ERROR",
            "防御日志推送失败次数过多，请检查监测服务状态"
        ));

        rules.add(createRule(
            "high_attacking_count",
            "STATE_COUNT",
            "gateway_attacking_state_count",
            ">",
            10,
            60,
            "CRITICAL",
            "攻击中的IP数量过多，系统可能遭受大规模攻击"
        ));

        rules.add(createRule(
            "high_defended_count",
            "STATE_COUNT",
            "gateway_defended_state_count",
            ">",
            50,
            60,
            "WARNING",
            "已防御的IP数量过多，请关注系统负载"
        ));

        rules.add(createRule(
            "high_queue_memory",
            "MEMORY",
            "gateway_queue_memory_bytes",
            ">",
            100 * 1024 * 1024,
            60,
            "WARNING",
            "队列内存占用过高，可能需要扩容"
        ));

        rules.add(createRule(
            "slow_request_processing",
            "LATENCY",
            "gateway_request_processing_duration",
            ">",
            1000,
            60,
            "WARNING",
            "请求处理延迟过高，请检查系统性能"
        ));

        rules.add(createRule(
            "slow_push_latency",
            "LATENCY",
            "gateway_push_latency",
            ">",
            5000,
            60,
            "WARNING",
            "推送延迟过高，请检查网络或监测服务状态"
        ));

        rules.add(createRule(
            "state_transition_frequent",
            "FREQUENCY",
            "gateway_state_transitions_total",
            ">",
            1000,
            300,
            "WARNING",
            "状态转换过于频繁，可能存在异常行为"
        ));
    }

    private AlertRule createRule(String name, String type, String metric, String operator,
                                  double threshold, int durationSeconds, String severity, String message) {
        AlertRule rule = new AlertRule();
        rule.setName(name);
        rule.setType(type);
        rule.setMetric(metric);
        rule.setOperator(operator);
        rule.setThreshold(threshold);
        rule.setDurationSeconds(durationSeconds);
        rule.setSeverity(severity);
        rule.setMessage(message);
        rule.setEnabled(true);
        return rule;
    }

    public List<AlertRule> getEnabledRules() {
        return rules.stream()
            .filter(AlertRule::isEnabled)
            .toList();
    }

    public AlertRule getRuleByName(String name) {
        return rules.stream()
            .filter(r -> r.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
}
