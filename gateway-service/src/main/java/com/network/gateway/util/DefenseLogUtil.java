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

        // 转换防御结果为日志DTO
        DefenseLogDTO logDTO = defenseResult.toDefenseLogDTO();
        
        // 补充额外的日志信息
        enhanceDefenseLog(logDTO, defenseResult);
        
        return logDTO;
    }

    /**
     * 增强防御日志信息
     *
     * @param logDTO 日志DTO
     * @param defenseResult 防御结果
     */
    private static void enhanceDefenseLog(DefenseLogDTO logDTO, DefenseResultBO defenseResult) {
        // 添加执行时间信息
        if (defenseResult.getExecuteStartTime() != null && defenseResult.getExecuteEndTime() != null) {
            logDTO.setProcessingTime(defenseResult.getExecuteEndTime() - defenseResult.getExecuteStartTime());
        }

        // 构建详细的结果描述
        String detailedDescription = buildDetailedDescription(defenseResult);
        logDTO.setResultDescription(detailedDescription);

        // 设置风险等级
        if (defenseResult.getRiskLevel() != null) {
            logDTO.setRiskLevel(convertRiskLevel(defenseResult.getRiskLevel()));
        } else {
            logDTO.setRiskLevel(DefenseLogDTO.RiskLevel.MEDIUM);
        }
    }

    /**
     * 构建详细的结果描述
     *
     * @param defenseResult 防御结果
     * @return 详细描述
     */
    private static String buildDetailedDescription(DefenseResultBO defenseResult) {
        StringBuilder sb = new StringBuilder();
        
        if (defenseResult.getSuccess()) {
            sb.append("成功执行");
            sb.append(getDefenseTypeDescription(defenseResult.getDefenseType()));
            sb.append("，");
            
            if (defenseResult.getResponseStatusCode() != null) {
                sb.append("返回状态码: ").append(defenseResult.getResponseStatusCode());
            }
            
            if (defenseResult.getProcessingTime() != null) {
                sb.append("，处理耗时: ").append(defenseResult.getProcessingTime()).append("ms");
            }
        } else {
            sb.append("执行失败: ").append(defenseResult.getErrorMessage());
        }

        return sb.toString();
    }

    /**
     * 获取防御类型描述
     *
     * @param defenseType 防御类型
     * @return 描述文本
     */
    private static String getDefenseTypeDescription(DefenseResultBO.DefenseType defenseType) {
        return switch (defenseType) {
            case BLACKLIST -> "IP黑名单拦截";
            case RATE_LIMIT -> "请求限流控制";
            case BLOCK -> "恶意请求拦截";
        };
    }

    /**
     * 转换风险等级
     *
     * @param riskLevel 业务对象风险等级
     * @return DTO风险等级
     */
    private static DefenseLogDTO.RiskLevel convertRiskLevel(DefenseResultBO.RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> DefenseLogDTO.RiskLevel.LOW;
            case MEDIUM -> DefenseLogDTO.RiskLevel.MEDIUM;
            case HIGH -> DefenseLogDTO.RiskLevel.HIGH;
            case CRITICAL -> DefenseLogDTO.RiskLevel.CRITICAL;
        };
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
        DefenseLogDTO logDTO = new DefenseLogDTO(
                DefenseLogDTO.DefenseType.BLACKLIST,
                targetIp,
                eventId,
                triggerReason
        );
        
        logDTO.setResponseInfo(403, "IP已被加入黑名单，拒绝访问");
        logDTO.setRiskLevel(convertRiskLevel(riskLevel));
        logDTO.setProcessingTime(System.currentTimeMillis());
        
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
        DefenseLogDTO logDTO = new DefenseLogDTO(
                DefenseLogDTO.DefenseType.RATE_LIMIT,
                targetIp,
                eventId,
                triggerReason
        );
        
        String resultDesc = String.format("请求频率过高(%d次/秒 > %d次/秒)，已实施限流措施", currentRate, limitRate);
        logDTO.setResponseInfo(429, resultDesc);
        logDTO.setRiskLevel(convertRiskLevel(riskLevel));
        logDTO.setProcessingTime(System.currentTimeMillis());
        
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
        DefenseLogDTO logDTO = new DefenseLogDTO(
                DefenseLogDTO.DefenseType.BLOCK,
                targetIp,
                eventId,
                triggerReason
        );
        
        logDTO.setResponseInfo(400, "检测到恶意请求，已拦截");
        logDTO.setRiskLevel(convertRiskLevel(riskLevel));
        logDTO.setProcessingTime(System.currentTimeMillis());
        
        return logDTO;
    }

    /**
     * 格式化防御日志为可读字符串
     *
     * @param logDTO 防御日志DTO
     * @return 格式化字符串
     */
    public static String formatDefenseLog(DefenseLogDTO logDTO) {
        return String.format(
                "防御日志 - 类型:%s IP:%s 事件:%s 风险:%s 状态:%s 耗时:%dms 描述:%s",
                logDTO.getDefenseType(),
                logDTO.getTargetIp(),
                logDTO.getEventId(),
                logDTO.getRiskLevel(),
                logDTO.getSuccess() ? "成功" : "失败",
                logDTO.getProcessingTime(),
                logDTO.getResultDescription()
        );
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

        return logDTO.getTargetIp() != null && !logDTO.getTargetIp().isEmpty() &&
               logDTO.getEventId() != null && !logDTO.getEventId().isEmpty() &&
               logDTO.getDefenseType() != null &&
               logDTO.getExecuteTimestamp() != null;
    }

    /**
     * 获取风险等级的中文描述
     *
     * @param riskLevel 风险等级
     * @return 中文描述
     */
    public static String getRiskLevelDescription(DefenseLogDTO.RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "低风险";
            case MEDIUM -> "中风险";
            case HIGH -> "高风险";
            case CRITICAL -> "严重风险";
        };
    }
}