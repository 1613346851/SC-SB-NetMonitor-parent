package com.network.monitor.task;

import com.network.monitor.service.AttackEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AttackEventExpireTask {

    @Autowired
    private AttackEventService attackEventService;

    @Value("${event.expire.minutes:5}")
    private int expireMinutes;

    @Scheduled(cron = "${event.expire.check.cron:0 */1 * * * ?}")
    public void checkAndEndExpiredEvents() {
        try {
            int endedCount = attackEventService.endExpiredEvents(expireMinutes);
            if (endedCount > 0) {
                log.info("过期事件检查完成：共结束{}个事件", endedCount);
            }
        } catch (Exception e) {
            log.error("检查过期事件时发生异常", e);
        }
    }
}
