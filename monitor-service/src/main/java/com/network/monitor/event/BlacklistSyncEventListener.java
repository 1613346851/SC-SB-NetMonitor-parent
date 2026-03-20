package com.network.monitor.event;

import com.network.monitor.service.BlacklistManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 黑名单同步事件监听器
 * 监听 BlacklistSyncEvent 并执行黑名单同步操作
 */
@Slf4j
@Component
public class BlacklistSyncEventListener {

    @Autowired
    private BlacklistManageService blacklistManageService;

    @EventListener
    public void handleBlacklistSync(BlacklistSyncEvent event) {
        String targetIp = event.getTargetIp();
        
        log.info("收到黑名单同步事件：ip={}, action={}", targetIp, event.getAction());
        
        try {
            if (event.getAction() == BlacklistSyncEvent.SyncAction.ADD) {
                blacklistManageService.addToBlacklist(
                    targetIp,
                    event.getReason(),
                    event.getExpireTime(),
                    event.getOperator()
                );
                log.info("事件驱动添加黑名单成功：ip={}, reason={}", targetIp, event.getReason());
                
            } else if (event.getAction() == BlacklistSyncEvent.SyncAction.REMOVE) {
                blacklistManageService.removeFromBlacklist(targetIp);
                log.info("事件驱动移除黑名单成功：ip={}", targetIp);
            }
            
        } catch (Exception e) {
            log.error("事件驱动同步黑名单失败：ip={}, action={}", targetIp, event.getAction(), e);
        }
    }
}
