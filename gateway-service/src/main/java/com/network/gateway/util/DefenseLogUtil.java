package com.network.gateway.util;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.dto.DefenseLogDTO;

/**
 * 防御日志封装工具类
 * 将防御执行结果转换为标准的日志DTO
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class DefenseLogUtil {

    /**
     * 从防御结果构建防御日志DTO
     *
     * @param defenseResult 防御结果对象
     * @return DefenseLogDTO
     */
    public static DefenseLogDTO buildDefenseLog(DefenseResultBO defenseResult) {
        if (defenseResult == null) {
            throw new IllegalArgumentException("防御结果不能为空");
        }

        return defenseResult.toDefenseLogDTO();
    }

    /**
     * 构建IP黑名单防御日志
     *
     * @param targetIp 目标IP
     * @param eventId 事件ID
     * @param riskLevel 风险等级
     * @param triggerReason 触发原因
     * @return DefenseLogDTO
     */
    public static DefenseLogDTO buildBlacklistLog(String targetIp, String eventId, 
                                                DefenseResultBO.RiskLevel riskLevel, String triggerReason) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType("BLOCK_IP");
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setDefenseAction("ADD_BLACKLIST");
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("IP已被加入黑名单，拒绝访问");
        logDTO.setOperator("SYSTEM");
        
        return logDTO;
    }

    /**
     * 构建请求限流防御日志
     *
     * @param targetIp 目标IP
     * @param eventId 事件ID
     * @param riskLevel 风险等级
     * @param triggerReason 触发原因
     * @param currentRate 当前请求速率
     * @param limitRate 限制速率
     * @return DefenseLogDTO
     */
    public static DefenseLogDTO buildRateLimitLog(String targetIp, String eventId,
                                                DefenseResultBO.RiskLevel riskLevel, String triggerReason,
                                                int currentRate, int limitRate) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType("RATE_LIMIT");
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setDefenseAction("RATE_LIMIT");
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult(String.format("请求频率过高(%d次/秒 > %d次/秒)，已实施限流措施", currentRate, limitRate));
        logDTO.setOperator("SYSTEM");
        
        return logDTO;
    }

    /**
     * 构建恶意请求拦截日志
     *
     * @param targetIp 目标IP
     * @param eventId 事件ID
     * @param riskLevel 风险等级
     * @param triggerReason 触发原因
     * @return DefenseLogDTO
     */
    public static DefenseLogDTO buildBlockLog(String targetIp, String eventId,
                                            DefenseResultBO.RiskLevel riskLevel, String triggerReason) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType("BLOCK_REQUEST");
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setDefenseAction("BLOCK_REQUEST");
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("检测到恶意请求，已拦截");
        logDTO.setOperator("SYSTEM");
        
        return logDTO;
    }

    /**
     * 构建防御执行摘要
     *
     * @param defenseResult 防御结果
     * @return 执行摘要
     */
    public static String buildExecutionSummary(DefenseResultBO defenseResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("防御执行摘要: ");
        sb.append("类型[").append(defenseResult.getDefenseType().getDescription()).append("] ");
        sb.append("IP[").append(defenseResult.getTargetIp()).append("] ");
        sb.append("事件[").append(defenseResult.getEventId()).append("] ");
        sb.append("状态[").append(defenseResult.getSuccess() ? "成功" : "失败").append("] ");
        
        if (defenseResult.getProcessingTime() != null) {
            sb.append("耗时[").append(defenseResult.getProcessingTime()).append("ms] ");
        }
        
        if (!defenseResult.getSuccess() && defenseResult.getErrorMessage() != null) {
            sb.append("错误[").append(defenseResult.getErrorMessage()).append("]");
        }

        return sb.toString();
    }

    /**
     * 验证防御日志的有效性
     *
     * @param logDTO 防御日志DTO
     * @return true表示有效
     */
    public static boolean isValidDefenseLog(DefenseLogDTO logDTO) {
        if (logDTO == null) {
            return false;
        }

        return logDTO.getDefenseTarget() != null && !logDTO.getDefenseTarget().isEmpty() &&
               logDTO.getDefenseType() != null && !logDTO.getDefenseType().isEmpty();
    }
}
