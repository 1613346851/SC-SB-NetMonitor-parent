package com.network.monitor.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DDoSCounterCleanTask {

    @Scheduled(cron = "${ddos.counter.clean.cron:0 * * * * ?}")
    public void cleanExpiredCounters() {
        log.debug("DDoS计数器清理任务已废弃，由网关服务负责");
    }
}
