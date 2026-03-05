package com.network.monitor.service.impl;

import com.network.monitor.common.constant.AttackTypeConstant;
import com.network.monitor.common.constant.RiskLevelConstant;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.service.DDoSDetectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DDoS 攻击专项检测服务实现类
 * 基于内存 ConcurrentHashMap 实现 1 分钟固定窗口计数
 */
@Slf4j
@Service
public class DDoSDetectServiceImpl implements DDoSDetectService {

    /**
     * DDoS 检测阈值：每分钟请求数
     */
    @Value("${ddos.threshold:100}")
    private int ddosThreshold;

    /**
     * 源 IP 请求计数器
     * Key: sourceIp_minuteTimestamp
     * Value: requestCount
     */
    private final Map<String, AtomicInteger> requestCounter = new ConcurrentHashMap<>();

    /**
     * 时间窗口格式化器
     */
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Override
    public AttackMonitorDTO detect(TrafficMonitorDTO trafficDTO) {
        if (trafficDTO == null || trafficDTO.getSourceIp() == null) {
            return null;
        }

        String sourceIp = trafficDTO.getSourceIp();
        String currentTimeWindow = getCurrentMinuteWindow();
        String counterKey = sourceIp + "_" + currentTimeWindow;

        // 原子性增加计数
        AtomicInteger count = requestCounter.computeIfAbsent(counterKey, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        // 检查是否超过阈值
        if (currentCount > ddosThreshold) {
            log.warn("检测到 DDoS 攻击！sourceIp={}, 当前窗口请求数={}, 阈值={}", 
                sourceIp, currentCount, ddosThreshold);
            
            return buildDDoSAttackDTO(trafficDTO, currentCount);
        }

        return null;
    }

    @Override
    public void resetCounter(String sourceIp, String timeWindow) {
        String key = sourceIp + "_" + timeWindow;
        requestCounter.remove(key);
        log.debug("重置 DDoS 计数器：key={}", key);
    }

    /**
     * 获取当前分钟级时间窗口标识
     */
    private String getCurrentMinuteWindow() {
        return LocalDateTime.now().format(MINUTE_FORMATTER);
    }

    /**
     * 构建 DDoS 攻击记录
     */
    private AttackMonitorDTO buildDDoSAttackDTO(TrafficMonitorDTO trafficDTO, int requestCount) {
        AttackMonitorDTO dto = new AttackMonitorDTO();
        
        dto.setTrafficId(null); // 后续由 Controller 填充
        dto.setAttackType(AttackTypeConstant.DDOS);
        dto.setRiskLevel(RiskLevelConstant.HIGH);
        dto.setConfidence(Math.min(90 + (requestCount - ddosThreshold) / 10, 100)); // 超过越多置信度越高
        dto.setRuleId(null); // DDoS 是频率检测，不依赖规则
        dto.setRuleContent("1 分钟内请求次数超过 " + ddosThreshold + " 次");
        dto.setSourceIp(trafficDTO.getSourceIp());
        dto.setTargetUri(trafficDTO.getRequestUri());
        dto.setAttackContent("请求频率：" + requestCount + " 次/分钟");
        
        return dto;
    }

    /**
     * 定时清理过期的计数器（每分钟执行一次）
     */
    public void cleanExpiredCounters() {
        String currentWindow = getCurrentMinuteWindow();
        
        requestCounter.keySet().removeIf(key -> {
            // 如果不是当前分钟的计数器，则删除
            return !key.endsWith("_" + currentWindow);
        });
        
        log.info("清理过期 DDoS 计数器完成");
    }
}
