package com.network.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class GatewayMetricsService {

    private final MeterRegistry meterRegistry;
    private final GatewayMetrics gatewayMetrics;
    
    private final ConcurrentMap<String, AtomicLong> ipQueueSizes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> ipStateGauges = new ConcurrentHashMap<>();
    
    private Counter totalRequestsCounter;
    private Counter blockedRequestsCounter;
    private Counter rateLimitedRequestsCounter;
    private Counter errorRequestsCounter;
    
    private Counter stateTransitionCounter;
    private Counter trafficPushSuccessCounter;
    private Counter trafficPushFailureCounter;
    private Counter defenseLogPushSuccessCounter;
    private Counter defenseLogPushFailureCounter;
    private Counter blacklistPushSuccessCounter;
    private Counter blacklistPushFailureCounter;
    
    private Counter normalStateCounter;
    private Counter suspiciousStateCounter;
    private Counter attackingStateCounter;
    private Counter defendedStateCounter;
    private Counter cooldownStateCounter;
    
    private Timer requestProcessingTimer;
    private Timer pushLatencyTimer;
    
    private AtomicLong totalIpCount = new AtomicLong(0);
    private AtomicLong activeIpCount = new AtomicLong(0);
    private AtomicLong queueMemoryBytes = new AtomicLong(0);

    public GatewayMetricsService(MeterRegistry meterRegistry, GatewayMetrics gatewayMetrics) {
        this.meterRegistry = meterRegistry;
        this.gatewayMetrics = gatewayMetrics;
        initMetrics();
    }

    private void initMetrics() {
        totalRequestsCounter = Counter.builder("gateway_requests_total")
            .description("Total number of requests processed")
            .register(meterRegistry);
        
        blockedRequestsCounter = Counter.builder("gateway_blocked_requests_total")
            .description("Total number of blocked requests")
            .register(meterRegistry);
        
        rateLimitedRequestsCounter = Counter.builder("gateway_rate_limited_requests_total")
            .description("Total number of rate limited requests")
            .register(meterRegistry);
        
        errorRequestsCounter = Counter.builder("gateway_error_requests_total")
            .description("Total number of error requests")
            .register(meterRegistry);
        
        stateTransitionCounter = Counter.builder("gateway_state_transitions_total")
            .description("Total number of state transitions")
            .register(meterRegistry);
        
        trafficPushSuccessCounter = Counter.builder("gateway_traffic_push_success_total")
            .description("Total number of successful traffic pushes")
            .register(meterRegistry);
        
        trafficPushFailureCounter = Counter.builder("gateway_traffic_push_failure_total")
            .description("Total number of failed traffic pushes")
            .register(meterRegistry);
        
        defenseLogPushSuccessCounter = Counter.builder("gateway_defense_log_push_success_total")
            .description("Total number of successful defense log pushes")
            .register(meterRegistry);
        
        defenseLogPushFailureCounter = Counter.builder("gateway_defense_log_push_failure_total")
            .description("Total number of failed defense log pushes")
            .register(meterRegistry);
        
        blacklistPushSuccessCounter = Counter.builder("gateway_blacklist_push_success_total")
            .description("Total number of successful blacklist pushes")
            .register(meterRegistry);
        
        blacklistPushFailureCounter = Counter.builder("gateway_blacklist_push_failure_total")
            .description("Total number of failed blacklist pushes")
            .register(meterRegistry);
        
        normalStateCounter = Counter.builder("gateway_state_changes_total")
            .tag("state", "NORMAL")
            .description("Total number of transitions to NORMAL state")
            .register(meterRegistry);
        
        suspiciousStateCounter = Counter.builder("gateway_state_changes_total")
            .tag("state", "SUSPICIOUS")
            .description("Total number of transitions to SUSPICIOUS state")
            .register(meterRegistry);
        
        attackingStateCounter = Counter.builder("gateway_state_changes_total")
            .tag("state", "ATTACKING")
            .description("Total number of transitions to ATTACKING state")
            .register(meterRegistry);
        
        defendedStateCounter = Counter.builder("gateway_state_changes_total")
            .tag("state", "DEFENDED")
            .description("Total number of transitions to DEFENDED state")
            .register(meterRegistry);
        
        cooldownStateCounter = Counter.builder("gateway_state_changes_total")
            .tag("state", "COOLDOWN")
            .description("Total number of transitions to COOLDOWN state")
            .register(meterRegistry);
        
        requestProcessingTimer = Timer.builder("gateway_request_processing_duration")
            .description("Time taken to process requests")
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .register(meterRegistry);
        
        pushLatencyTimer = Timer.builder("gateway_push_latency")
            .description("Time taken to push data to monitor service")
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(60))
            .register(meterRegistry);
        
        Gauge.builder("gateway_total_ip_count", totalIpCount, AtomicLong::get)
            .description("Total number of IPs being tracked")
            .register(meterRegistry);
        
        Gauge.builder("gateway_active_ip_count", activeIpCount, AtomicLong::get)
            .description("Number of IPs with recent activity")
            .register(meterRegistry);
        
        Gauge.builder("gateway_queue_memory_bytes", queueMemoryBytes, AtomicLong::get)
            .description("Memory used by traffic queues in bytes")
            .register(meterRegistry);
        
        Gauge.builder("gateway_normal_state_count", gatewayMetrics, m -> m.getSnapshot().getNormalStateCount())
            .description("Current count of IPs in NORMAL state")
            .register(meterRegistry);
        
        Gauge.builder("gateway_suspicious_state_count", gatewayMetrics, m -> m.getSnapshot().getSuspiciousStateCount())
            .description("Current count of IPs in SUSPICIOUS state")
            .register(meterRegistry);
        
        Gauge.builder("gateway_attacking_state_count", gatewayMetrics, m -> m.getSnapshot().getAttackingStateCount())
            .description("Current count of IPs in ATTACKING state")
            .register(meterRegistry);
        
        Gauge.builder("gateway_defended_state_count", gatewayMetrics, m -> m.getSnapshot().getDefendedStateCount())
            .description("Current count of IPs in DEFENDED state")
            .register(meterRegistry);
        
        Gauge.builder("gateway_cooldown_state_count", gatewayMetrics, m -> m.getSnapshot().getCooldownStateCount())
            .description("Current count of IPs in COOLDOWN state")
            .register(meterRegistry);
        
        log.info("Prometheus metrics initialized successfully");
    }

    public void recordRequest() {
        totalRequestsCounter.increment();
        gatewayMetrics.incrementTotalRequests();
    }

    public void recordBlockedRequest() {
        blockedRequestsCounter.increment();
        gatewayMetrics.incrementBlocked();
    }

    public void recordRateLimitedRequest() {
        rateLimitedRequestsCounter.increment();
        gatewayMetrics.incrementRateLimited();
    }

    public void recordErrorRequest() {
        errorRequestsCounter.increment();
        gatewayMetrics.incrementErrors();
    }

    public void recordStateTransition(String fromState, String toState) {
        stateTransitionCounter.increment();
        gatewayMetrics.incrementStateTransitions();
        
        switch (toState) {
            case "NORMAL":
                normalStateCounter.increment();
                break;
            case "SUSPICIOUS":
                suspiciousStateCounter.increment();
                break;
            case "ATTACKING":
                attackingStateCounter.increment();
                break;
            case "DEFENDED":
                defendedStateCounter.increment();
                break;
            case "COOLDOWN":
                cooldownStateCounter.increment();
                break;
        }
        
        log.debug("State transition recorded: {} -> {}", fromState, toState);
    }

    public void recordTrafficPushSuccess() {
        trafficPushSuccessCounter.increment();
        gatewayMetrics.incrementTrafficPushSuccess();
    }

    public void recordTrafficPushFailure() {
        trafficPushFailureCounter.increment();
        gatewayMetrics.incrementTrafficPushFailure();
    }

    public void recordDefenseLogPushSuccess() {
        defenseLogPushSuccessCounter.increment();
        gatewayMetrics.incrementDefenseLogPushSuccess();
    }

    public void recordDefenseLogPushFailure() {
        defenseLogPushFailureCounter.increment();
        gatewayMetrics.incrementDefenseLogPushFailure();
    }

    public void recordBlacklistPushSuccess() {
        blacklistPushSuccessCounter.increment();
    }

    public void recordBlacklistPushFailure() {
        blacklistPushFailureCounter.increment();
    }

    public Timer.Sample startRequestTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopRequestTimer(Timer.Sample sample) {
        sample.stop(requestProcessingTimer);
    }

    public Timer.Sample startPushTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopPushTimer(Timer.Sample sample) {
        sample.stop(pushLatencyTimer);
    }

    public void updateIpQueueSize(String ip, long size) {
        AtomicLong sizeGauge = ipQueueSizes.computeIfAbsent(ip, k -> {
            AtomicLong gauge = new AtomicLong(0);
            Gauge.builder("gateway_ip_queue_size", gauge, AtomicLong::get)
                .tag("ip", ip)
                .description("Queue size for specific IP")
                .register(meterRegistry);
            return gauge;
        });
        sizeGauge.set(size);
    }

    public void updateIpState(String ip, int state) {
        AtomicLong stateGauge = ipStateGauges.computeIfAbsent(ip, k -> {
            AtomicLong gauge = new AtomicLong(0);
            Gauge.builder("gateway_ip_state", gauge, AtomicLong::get)
                .tag("ip", ip)
                .description("Current state of IP (0=NORMAL, 1=SUSPICIOUS, 2=ATTACKING, 3=DEFENDED, 4=COOLDOWN)")
                .register(meterRegistry);
            return gauge;
        });
        stateGauge.set(state);
    }

    public void updateTotalIpCount(long count) {
        totalIpCount.set(count);
    }

    public void updateActiveIpCount(long count) {
        activeIpCount.set(count);
    }

    public void updateQueueMemoryBytes(long bytes) {
        queueMemoryBytes.set(bytes);
    }

    public void recordCustomCounter(String name, double amount) {
        Counter.builder(name)
            .register(meterRegistry)
            .increment(amount);
    }

    public void recordCustomGauge(String name, double value) {
        AtomicLong gaugeValue = new AtomicLong((long) value);
        Gauge.builder(name, gaugeValue, AtomicLong::get)
            .register(meterRegistry);
    }

    public GatewayMetrics.MetricsSnapshot getMetricsSnapshot() {
        return gatewayMetrics.getSnapshot();
    }

    public String getMetricsSummary() {
        return gatewayMetrics.getSummary();
    }
}
