package com.network.monitor.task;

import com.network.monitor.service.impl.DDoSDetectServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DDoS 计数器清理定时任务
 */
@Slf4j
@Component
public class DDoSCounterCleanTask {

    @Autowired
    private DDoSDetectServiceImpl ddosDetectService;

    /**
     * 每分钟清理一次过期的 DDoS 计数器
     */
    @Scheduled(cron = "${ddos.counter.clean.cron:0 * * * * ?}")
    public void cleanExpiredCounters() {
        try {
            ddosDetectService.cleanExpiredCounters();
        } catch (Exception e) {
            log.error("清理 DDoS 计数器失败：", e);
        }
    }
}
