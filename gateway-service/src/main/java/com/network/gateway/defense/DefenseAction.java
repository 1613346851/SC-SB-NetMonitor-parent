package com.network.gateway.defense;

import lombok.Data;

import java.io.Serializable;

@Data
public class DefenseAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    private String typeName;
    private String description;
    private long timestamp;
    private int count;
    private String timeWindow;
    private Long duration;
    private String expireTime;
    private String details;

    public DefenseAction() {
        this.timestamp = System.currentTimeMillis();
        this.count = 1;
    }

    public DefenseAction(DefenseLogType logType) {
        this();
        this.type = logType.getCode();
        this.typeName = logType.getName();
        this.description = logType.getDescription();
    }

    public static DefenseAction rateLimit(int count, String timeWindow) {
        DefenseAction action = new DefenseAction(DefenseLogType.RATE_LIMIT);
        action.setCount(count);
        action.setTimeWindow(timeWindow);
        action.setDescription(String.format("时间段内累计限流 %d 次", count));
        return action;
    }

    public static DefenseAction addBlacklist(Long duration, String expireTime) {
        DefenseAction action = new DefenseAction(DefenseLogType.ADD_BLACKLIST);
        action.setDuration(duration);
        action.setExpireTime(expireTime);
        action.setDescription("IP加入黑名单封禁");
        return action;
    }

    public static DefenseAction blockRequest(String reason) {
        DefenseAction action = new DefenseAction(DefenseLogType.BLOCK_REQUEST);
        action.setDetails(reason);
        action.setDescription("恶意请求被拦截: " + reason);
        return action;
    }

    public static DefenseAction blockUserAgent(String userAgent) {
        DefenseAction action = new DefenseAction(DefenseLogType.BLOCK_USER_AGENT);
        action.setDetails(userAgent);
        action.setDescription("恶意User-Agent被拦截");
        return action;
    }

    public static DefenseAction blockUri(String uri) {
        DefenseAction action = new DefenseAction(DefenseLogType.BLOCK_URI);
        action.setDetails(uri);
        action.setDescription("恶意URI被拦截");
        return action;
    }

    public void incrementCount() {
        this.count++;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("DefenseAction{type=%s, count=%d, description=%s}", 
            type, count, description);
    }
}
