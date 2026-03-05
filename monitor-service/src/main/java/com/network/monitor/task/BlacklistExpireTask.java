package com.network.monitor.task;

import com.network.monitor.service.BlacklistManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 黑名单过期清理定时任务
 * 定时清理过期的黑名单 IP，同步更新至网关服务
 */
@Slf4j
@Component
public class BlacklistExpireTask {

    @Autowired
    private BlacklistManageService blacklistManageService;

    /**
     * 每 5 分钟清理一次过期黑名单
     */
    @Scheduled(cron = "${blacklist.clean.cron:0 */5 * * * ?}")
    public void cleanExpiredBlacklist() {
        try {
            int count = blacklistManageService.cleanExpiredBlacklist();
            
            if (count > 0) {
                log.info("定时清理过期黑名单完成，清理数量：{}", count);
            }
        } catch (Exception e) {
            log.error("定时清理过期黑名单失败：", e);
        }
    }

    /**
     * 每天凌晨 3 点深度清理黑名单（可选）
     */
    @Scheduled(cron = "${blacklist.deep.clean.cron:0 0 3 * * ?}")
    public void deepClean() {
        try {
            log.info("执行黑名单深度清理任务");
            // 深度清理逻辑（可选）
        } catch (Exception e) {
            log.error("黑名单深度清理失败：", e);
        }
    }
}
