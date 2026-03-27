package com.network.gateway.constant;

public class IpAttackStateConstant {

    public static final int NORMAL = 0;
    public static final int SUSPICIOUS = 1;
    public static final int ATTACKING = 2;
    public static final int DEFENDED = 3;
    public static final int COOLDOWN = 4;
    public static final int MANUAL_RESET = 5;

    public static final long COOLDOWN_DURATION_MS = 300_000L;
    public static final long STATE_EXPIRE_MS = 600_000L;

    public static final int SUSPICIOUS_RATE_LIMIT_THRESHOLD = 5;
    public static final int ATTACKING_RATE_LIMIT_THRESHOLD = 10;
    public static final long ATTACKING_DURATION_MS = 0L;

    public static final long PERIOD_PUSH_INTERVAL_MS = 3000L;
    public static final int MAX_SAMPLE_SIZE = 5;
    public static final long ATTACK_STOP_THRESHOLD_MS = 10000L;
    public static final long SUSPICIOUS_RECOVERY_MS = 60000L;

    public static final long SUSPICIOUS_STATE_TIMEOUT_MS = 30_000L;
    public static final long COOLDOWN_STATE_TIMEOUT_MS = 600_000L;
    public static final long SLIDING_WINDOW_STEP_MS = 100L;

    public static final int SINGLE_IP_QUEUE_CAPACITY = 50;
    public static final int GLOBAL_QUEUE_CAPACITY = 1000;

    public static final String STATE_NORMAL_NAME = "正常";
    public static final String STATE_NORMAL_DESC = "正常请求，无异常行为";
    public static final String STATE_SUSPICIOUS_NAME = "可疑";
    public static final String STATE_SUSPICIOUS_DESC = "请求频率异常，需要关注";
    public static final String STATE_ATTACKING_NAME = "攻击中";
    public static final String STATE_ATTACKING_DESC = "确认攻击行为，准备防御";
    public static final String STATE_DEFENDED_NAME = "已防御";
    public static final String STATE_DEFENDED_DESC = "已执行防御措施，拦截请求";
    public static final String STATE_COOLDOWN_NAME = "冷却期";
    public static final String STATE_COOLDOWN_DESC = "攻击停止后的观察期";
    public static final String STATE_MANUAL_RESET_NAME = "人工重置";
    public static final String STATE_MANUAL_RESET_DESC = "管理员手动干预重置状态";

    public static final String TRANSITION_REASON_FREQUENCY_ABNORMAL = "请求频率异常，进入可疑状态";
    public static final String TRANSITION_REASON_ATTACK_CONFIRMED = "攻击行为确认，准备防御";
    public static final String TRANSITION_REASON_DEFENSE_EXECUTED = "防御措施已执行";
    public static final String TRANSITION_REASON_ATTACK_STOPPED = "攻击停止，进入冷却期";
    public static final String TRANSITION_REASON_COOLDOWN_ENDED = "冷却期结束，恢复正常";
    public static final String TRANSITION_REASON_RECOVERY = "异常行为停止，恢复正常";
    public static final String TRANSITION_REASON_RATE_LIMIT_THRESHOLD = "连续限流达到阈值，执行防御";
    public static final String TRANSITION_REASON_REATTACK = "冷却期内再次攻击";
    public static final String TRANSITION_REASON_MANUAL_RESET = "人工重置状态";
    public static final String TRANSITION_REASON_MANUAL_BAN = "人工封禁";
    public static final String TRANSITION_REASON_SYSTEM_RESET = "系统重置";

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
            case MANUAL_RESET:
                return "MANUAL_RESET";
            default:
                return "UNKNOWN";
        }
    }

    public static String getStateNameZh(int state) {
        switch (state) {
            case NORMAL:
                return STATE_NORMAL_NAME;
            case SUSPICIOUS:
                return STATE_SUSPICIOUS_NAME;
            case ATTACKING:
                return STATE_ATTACKING_NAME;
            case DEFENDED:
                return STATE_DEFENDED_NAME;
            case COOLDOWN:
                return STATE_COOLDOWN_NAME;
            case MANUAL_RESET:
                return STATE_MANUAL_RESET_NAME;
            default:
                return "未知";
        }
    }

    public static String getStateDescription(int state) {
        switch (state) {
            case NORMAL:
                return STATE_NORMAL_DESC;
            case SUSPICIOUS:
                return STATE_SUSPICIOUS_DESC;
            case ATTACKING:
                return STATE_ATTACKING_DESC;
            case DEFENDED:
                return STATE_DEFENDED_DESC;
            case COOLDOWN:
                return STATE_COOLDOWN_DESC;
            case MANUAL_RESET:
                return STATE_MANUAL_RESET_DESC;
            default:
                return "未知状态";
        }
    }

    public static boolean isValidState(int state) {
        return state >= NORMAL && state <= MANUAL_RESET;
    }

    public static boolean canTransitionTo(int fromState, int toState) {
        if (!isValidState(fromState) || !isValidState(toState)) {
            return false;
        }
        if (fromState == toState) {
            return false;
        }
        switch (fromState) {
            case NORMAL:
                return toState == SUSPICIOUS;
            case SUSPICIOUS:
                return toState == NORMAL || toState == ATTACKING || toState == DEFENDED;
            case ATTACKING:
                return toState == DEFENDED;
            case DEFENDED:
                return toState == COOLDOWN;
            case COOLDOWN:
                return toState == NORMAL || toState == ATTACKING;
            case MANUAL_RESET:
                return toState == NORMAL;
            default:
                return false;
        }
    }

    private IpAttackStateConstant() {
    }
}
