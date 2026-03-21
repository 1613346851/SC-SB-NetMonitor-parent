package com.network.monitor.common.constant;

public class IpAttackStateConstant {

    public static final int NORMAL = 0;
    public static final int SUSPICIOUS = 1;
    public static final int ATTACKING = 2;
    public static final int DEFENDED = 3;
    public static final int COOLDOWN = 4;

    public static final long COOLDOWN_DURATION_MS = 300_000L;
    public static final long STATE_EXPIRE_MS = 600_000L;

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

    private IpAttackStateConstant() {
    }
}
