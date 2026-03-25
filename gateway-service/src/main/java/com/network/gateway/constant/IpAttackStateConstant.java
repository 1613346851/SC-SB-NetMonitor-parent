package com.network.gateway.constant;

public class IpAttackStateConstant {

    public static final int NORMAL = 0;
    public static final int SUSPICIOUS = 1;
    public static final int ATTACKING = 2;
    public static final int DEFENDED = 3;
    public static final int COOLDOWN = 4;

    public static final long COOLDOWN_DURATION_MS = 300_000L;
    public static final long STATE_EXPIRE_MS = 600_000L;

    public static final int SUSPICIOUS_RATE_LIMIT_THRESHOLD = 2;
    public static final int ATTACKING_RATE_LIMIT_THRESHOLD = 5;
    public static final long ATTACKING_DURATION_MS = 30_000L;

    public static String getStateName(int state) {
        switch (state) {
            case NORMAL:
                return "NORMAL";
            case SUSPICIOUS:
                return "SUSPICIOUS";
            case ATTACKING:
                return "ATTACKING";
            case DEFENDED:
                return "DEFENDED";
            case COOLDOWN:
                return "COOLDOWN";
            default:
                return "UNKNOWN";
        }
    }

    public static String getStateNameZh(int state) {
        switch (state) {
            case NORMAL:
                return "正常";
            case SUSPICIOUS:
                return "可疑";
            case ATTACKING:
                return "攻击中";
            case DEFENDED:
                return "已防御";
            case COOLDOWN:
                return "冷却期";
            default:
                return "未知";
        }
    }

    private IpAttackStateConstant() {
    }
}
