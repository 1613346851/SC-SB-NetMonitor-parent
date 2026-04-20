package com.network.gateway.health;

import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class HealthCheckService {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);

    @Autowired
    private IpAttackStateCache stateCache;

    @Autowired
    private GatewayMetrics metrics;

    private volatile boolean healthy = true;
    private volatile String lastHealthCheck = "OK";
    private volatile long lastHealthCheckTime = System.currentTimeMillis();
    
    private final AtomicLong lastTrafficTime = new AtomicLong(System.currentTimeMillis());
    
    private long maxMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
    private double memoryWarningThreshold = 0.85;
    private double memoryCriticalThreshold = 0.95;

    public HealthStatus checkHealth() {
        HealthStatus status = new HealthStatus();
        status.setTimestamp(System.currentTimeMillis());
        
        checkMemoryStatus(status);
        checkStateCacheStatus(status);
        checkMetricsStatus(status);
        
        status.setHealthy(status.getMemoryStatus().isHealthy() 
            && status.getStateCacheStatus().isHealthy()
            && status.getMetricsStatus().isHealthy());
        
        this.healthy = status.isHealthy();
        this.lastHealthCheckTime = status.getTimestamp();
        this.lastHealthCheck = status.isHealthy() ? "OK" : "DEGRADED";
        
        return status;
    }

    public void onTrafficReceived() {
        lastTrafficTime.set(System.currentTimeMillis());
        
        long idleTime = System.currentTimeMillis() - lastHealthCheckTime;
        if (idleTime > 30000) {
            HealthStatus status = checkHealth();
            if (!status.isHealthy()) {
                logger.warn("健康检查发现问题: {}", status.getSummary());
            }
        }
    }

    private void checkMemoryStatus(HealthStatus status) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsage = (double) usedMemory / maxMemoryMB / (1024 * 1024);
        
        ComponentHealth memoryHealth = new ComponentHealth();
        memoryHealth.setName("memory");
        memoryHealth.setHealthy(memoryUsage < memoryCriticalThreshold);
        memoryHealth.setMetric("usedMB", usedMemory / (1024 * 1024));
        memoryHealth.setMetric("totalMB", totalMemory / (1024 * 1024));
        memoryHealth.setMetric("maxMB", maxMemoryMB);
        memoryHealth.setMetric("usagePercent", String.format("%.2f", memoryUsage * 100));
        
        if (memoryUsage >= memoryCriticalThreshold) {
            memoryHealth.setStatus("CRITICAL");
            memoryHealth.setMessage("Memory usage is critical");
        } else if (memoryUsage >= memoryWarningThreshold) {
            memoryHealth.setStatus("WARNING");
            memoryHealth.setMessage("Memory usage is high");
        } else {
            memoryHealth.setStatus("OK");
            memoryHealth.setMessage("Memory usage is normal");
        }
        
        status.setMemoryStatus(memoryHealth);
    }

    private void checkStateCacheStatus(HealthStatus status) {
        ComponentHealth cacheHealth = new ComponentHealth();
        cacheHealth.setName("stateCache");
        
        try {
            int totalEntries = stateCache.size();
            int normalCount = 0;
            int suspiciousCount = 0;
            int attackingCount = 0;
            int defendedCount = 0;
            int cooldownCount = 0;
            
            for (var entry : stateCache.getAllEntries().values()) {
                switch (entry.getState()) {
                    case 0: normalCount++; break;
                    case 1: suspiciousCount++; break;
                    case 2: attackingCount++; break;
                    case 3: defendedCount++; break;
                    case 4: cooldownCount++; break;
                }
            }
            
            cacheHealth.setHealthy(true);
            cacheHealth.setStatus("OK");
            cacheHealth.setMessage("State cache is operational");
            cacheHealth.setMetric("totalEntries", totalEntries);
            cacheHealth.setMetric("normalCount", normalCount);
            cacheHealth.setMetric("suspiciousCount", suspiciousCount);
            cacheHealth.setMetric("attackingCount", attackingCount);
            cacheHealth.setMetric("defendedCount", defendedCount);
            cacheHealth.setMetric("cooldownCount", cooldownCount);
            
            metrics.updateStateCount(normalCount, suspiciousCount, attackingCount, defendedCount, cooldownCount);
            
        } catch (Exception e) {
            cacheHealth.setHealthy(false);
            cacheHealth.setStatus("ERROR");
            cacheHealth.setMessage("State cache error: " + e.getMessage());
        }
        
        status.setStateCacheStatus(cacheHealth);
    }

    private void checkMetricsStatus(HealthStatus status) {
        ComponentHealth metricsHealth = new ComponentHealth();
        metricsHealth.setName("metrics");
        
        try {
            GatewayMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            
            metricsHealth.setHealthy(true);
            metricsHealth.setStatus("OK");
            metricsHealth.setMessage("Metrics collection is operational");
            metricsHealth.setMetric("totalRequests", snapshot.getTotalRequests());
            metricsHealth.setMetric("totalBlocked", snapshot.getTotalBlocked());
            metricsHealth.setMetric("blockRate", String.format("%.2f%%", snapshot.getBlockRate()));
            metricsHealth.setMetric("errorRate", String.format("%.2f%%", snapshot.getErrorRate()));
            metricsHealth.setMetric("uptimeSeconds", snapshot.getUptime() / 1000);
            
        } catch (Exception e) {
            metricsHealth.setHealthy(false);
            metricsHealth.setStatus("ERROR");
            metricsHealth.setMessage("Metrics error: " + e.getMessage());
        }
        
        status.setMetricsStatus(metricsHealth);
    }

    public boolean isHealthy() {
        return healthy;
    }

    public String getLastHealthCheck() {
        return lastHealthCheck;
    }

    public long getLastHealthCheckTime() {
        return lastHealthCheckTime;
    }

    public Map<String, Object> getQuickStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("healthy", healthy);
        status.put("lastCheck", lastHealthCheck);
        status.put("lastCheckTime", lastHealthCheckTime);
        status.put("uptime", System.currentTimeMillis() - lastHealthCheckTime);
        return status;
    }

    @lombok.Data
    public static class HealthStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private long timestamp;
        private boolean healthy;
        private ComponentHealth memoryStatus;
        private ComponentHealth stateCacheStatus;
        private ComponentHealth metricsStatus;

        public String getSummary() {
            return String.format("HealthStatus{healthy=%s, memory=%s, cache=%s, metrics=%s}",
                healthy,
                memoryStatus != null ? memoryStatus.getStatus() : "N/A",
                stateCacheStatus != null ? stateCacheStatus.getStatus() : "N/A",
                metricsStatus != null ? metricsStatus.getStatus() : "N/A");
        }
    }

    @lombok.Data
    public static class ComponentHealth implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String name;
        private boolean healthy;
        private String status;
        private String message;
        private Map<String, Object> metrics = new HashMap<>();

        public void setMetric(String key, Object value) {
            metrics.put(key, value);
        }
    }
}
