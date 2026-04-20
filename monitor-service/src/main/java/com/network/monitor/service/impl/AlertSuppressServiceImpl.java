package com.network.monitor.service.impl;

import com.network.monitor.service.AlertSuppressService;
import com.network.monitor.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertSuppressServiceImpl implements AlertSuppressService {

    private static final Logger logger = LoggerFactory.getLogger(AlertSuppressServiceImpl.class);

    private final Map<String, SuppressRecord> suppressCache = new ConcurrentHashMap<>();

    @Autowired
    private SysConfigService sysConfigService;

    @Override
    public boolean shouldSuppress(String sourceIp, String attackType) {
        String key = buildKey(sourceIp, attackType);
        SuppressRecord record = suppressCache.get(key);
        
        if (record == null) {
            return false;
        }
        
        int suppressDuration = getSuppressDuration();
        LocalDateTime expireTime = record.getAlertTime().plusSeconds(suppressDuration);
        
        if (LocalDateTime.now().isAfter(expireTime)) {
            suppressCache.remove(key);
            return false;
        }
        
        return true;
    }

    @Override
    public void recordAlert(String sourceIp, String attackType) {
        String key = buildKey(sourceIp, attackType);
        SuppressRecord record = new SuppressRecord(sourceIp, attackType, LocalDateTime.now());
        suppressCache.put(key, record);
        logger.debug("记录告警抑制: key={}", key);
    }

    @Override
    public void clearSuppress(String sourceIp, String attackType) {
        String key = buildKey(sourceIp, attackType);
        suppressCache.remove(key);
        logger.debug("清除告警抑制: key={}", key);
    }

    @Override
    public void clearAllSuppress() {
        suppressCache.clear();
        logger.info("清除所有告警抑制记录");
    }

    private String buildKey(String sourceIp, String attackType) {
        return sourceIp + ":" + (attackType != null ? attackType : "ALL");
    }

    private int getSuppressDuration() {
        String durationStr = sysConfigService.getConfigValue("alert.suppress.duration-seconds");
        if (durationStr != null && !durationStr.isEmpty()) {
            try {
                return Integer.parseInt(durationStr);
            } catch (NumberFormatException e) {
                logger.warn("解析告警抑制时长失败: {}", durationStr);
            }
        }
        return 300;
    }

    private static class SuppressRecord {
        private final String sourceIp;
        private final String attackType;
        private final LocalDateTime alertTime;

        public SuppressRecord(String sourceIp, String attackType, LocalDateTime alertTime) {
            this.sourceIp = sourceIp;
            this.attackType = attackType;
            this.alertTime = alertTime;
        }

        public String getSourceIp() {
            return sourceIp;
        }

        public String getAttackType() {
            return attackType;
        }

        public LocalDateTime getAlertTime() {
            return alertTime;
        }
    }
}
