package com.network.monitor.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 黑名单同步事件
 * 当防御日志需要同步到黑名单时发布此事件
 */
@Getter
public class BlacklistSyncEvent extends ApplicationEvent {

    private final String targetIp;
    private final String reason;
    private final LocalDateTime expireTime;
    private final String operator;
    private final SyncAction action;

    public enum SyncAction {
        ADD,
        REMOVE
    }

    public BlacklistSyncEvent(Object source, String targetIp, String reason, 
                              LocalDateTime expireTime, String operator, SyncAction action) {
        super(source);
        this.targetIp = targetIp;
        this.reason = reason;
        this.expireTime = expireTime;
        this.operator = operator;
        this.action = action;
    }

    public static BlacklistSyncEvent add(Object source, String targetIp, String reason,
                                         LocalDateTime expireTime, String operator) {
        return new BlacklistSyncEvent(source, targetIp, reason, expireTime, operator, SyncAction.ADD);
    }

    public static BlacklistSyncEvent remove(Object source, String targetIp) {
        return new BlacklistSyncEvent(source, targetIp, null, null, null, SyncAction.REMOVE);
    }
}
