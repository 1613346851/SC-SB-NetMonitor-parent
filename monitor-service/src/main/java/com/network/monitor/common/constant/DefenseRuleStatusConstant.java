package com.network.monitor.common.constant;

/**
 * 防御规则状态常量
 */
public class DefenseRuleStatusConstant {

    /**
     * 未配置
     */
    public static final int NOT_CONFIGURED = 0;

    /**
     * 部分已配置
     */
    public static final int PARTIALLY_CONFIGURED = 1;

    /**
     * 已配置
     */
    public static final int FULLY_CONFIGURED = 2;

    /**
     * 根据规则数量计算防御状态
     * @param ruleCount 关联规则数量
     * @param requiredCount 需要的规则数量（如果不知道具体需要多少，传入-1表示只判断是否大于0）
     * @return 防御状态
     */
    public static int calculateStatus(int ruleCount, int requiredCount) {
        if (ruleCount <= 0) {
            return NOT_CONFIGURED;
        }
        if (requiredCount > 0 && ruleCount < requiredCount) {
            return PARTIALLY_CONFIGURED;
        }
        return FULLY_CONFIGURED;
    }

    /**
     * 根据规则数量计算防御状态（简化版本，只判断是否配置）
     * @param ruleCount 关联规则数量
     * @return 防御状态
     */
    public static int calculateStatus(int ruleCount) {
        if (ruleCount <= 0) {
            return NOT_CONFIGURED;
        }
        return FULLY_CONFIGURED;
    }

    /**
     * 获取状态描述
     * @param status 状态值
     * @return 状态描述
     */
    public static String getStatusDesc(int status) {
        switch (status) {
            case NOT_CONFIGURED:
                return "未配置";
            case PARTIALLY_CONFIGURED:
                return "部分已配置";
            case FULLY_CONFIGURED:
                return "已配置";
            default:
                return "未知";
        }
    }
}
