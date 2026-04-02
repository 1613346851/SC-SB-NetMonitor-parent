package com.network.gateway.util;

import com.network.gateway.bo.DefenseResultBO;
import com.network.gateway.defense.DefenseAction;
import com.network.gateway.defense.DefenseLogType;
import com.network.gateway.defense.RateLimitCounter;
import com.network.gateway.dto.DefenseLogDTO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DefenseLogUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static DefenseLogDTO buildDefenseLog(DefenseResultBO defenseResult) {
        if (defenseResult == null) {
            throw new IllegalArgumentException("防御结果不能为空");
        }

        return defenseResult.toDefenseLogDTO();
    }

    public static DefenseLogDTO buildBlacklistLog(String targetIp, String eventId, 
                                                DefenseResultBO.RiskLevel riskLevel, String triggerReason) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.ADD_BLACKLIST.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setDefenseAction(DefenseLogType.ADD_BLACKLIST.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("IP已被加入黑名单，拒绝访问");
        logDTO.setOperator("SYSTEM");
        logDTO.setExecuteTime(System.currentTimeMillis());
        if (riskLevel != null) {
            logDTO.setRiskLevel(riskLevel.name());
        }
        
        return logDTO;
    }

    public static DefenseLogDTO buildBlacklistLog(String targetIp, String eventId, 
                                                String triggerReason, Integer confidence,
                                                Long expireTimestamp) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.ADD_BLACKLIST.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setDefenseAction(DefenseLogType.ADD_BLACKLIST.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("IP已被加入黑名单，拒绝访问");
        logDTO.setOperator("SYSTEM");
        logDTO.setConfidence(confidence);
        logDTO.setExecuteTime(System.currentTimeMillis());
        logDTO.setRiskLevel(calculateRiskLevelByConfidence(confidence).name());
        
        if (expireTimestamp != null) {
            LocalDateTime expireDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expireTimestamp), ZoneId.systemDefault());
            logDTO.setExpireTime(expireDateTime.format(FORMATTER));
        }
        
        return logDTO;
    }

    public static DefenseLogDTO buildRateLimitLog(String targetIp, String eventId,
                                                DefenseResultBO.RiskLevel riskLevel, String triggerReason,
                                                int currentRate, int limitRate) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.RATE_LIMIT.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setDefenseAction(DefenseLogType.RATE_LIMIT.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult(String.format("请求频率过高(%d次/秒 > %d次/秒)，已实施限流措施", currentRate, limitRate));
        logDTO.setOperator("SYSTEM");
        logDTO.setExecuteTime(System.currentTimeMillis());
        if (riskLevel != null) {
            logDTO.setRiskLevel(riskLevel.name());
        }
        
        return logDTO;
    }

    public static DefenseLogDTO buildRateLimitAggregatedLog(String targetIp, String eventId,
                                                            String triggerReason, Integer confidence,
                                                            RateLimitCounter counter) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.RATE_LIMIT.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setDefenseAction(DefenseLogType.RATE_LIMIT.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setConfidence(confidence);
        logDTO.setExecuteTime(System.currentTimeMillis());
        logDTO.setRiskLevel(calculateRiskLevelByConfidence(confidence).name());
        
        if (counter != null) {
            logDTO.setRateLimitCount(counter.getCount());
            logDTO.setTimeWindow(counter.getTimeWindow());
            logDTO.setExecuteResult(String.format("时间段内累计限流 %d 次", counter.getCount()));
        } else {
            logDTO.setExecuteResult("已实施限流措施");
        }
        
        logDTO.setOperator("SYSTEM");
        
        return logDTO;
    }

    public static DefenseLogDTO buildBlockLog(String targetIp, String eventId,
                                            DefenseResultBO.RiskLevel riskLevel, String triggerReason) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.BLOCK_REQUEST.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setDefenseAction(DefenseLogType.BLOCK_REQUEST.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("检测到恶意请求，已拦截");
        logDTO.setOperator("SYSTEM");
        logDTO.setExecuteTime(System.currentTimeMillis());
        if (riskLevel != null) {
            logDTO.setRiskLevel(riskLevel.name());
        }
        
        return logDTO;
    }

    public static DefenseLogDTO buildBlockLog(String targetIp, String eventId,
                                            String triggerReason, Integer confidence,
                                            String requestUri, String httpMethod) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.BLOCK_REQUEST.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setDefenseAction(DefenseLogType.BLOCK_REQUEST.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("检测到恶意请求，已拦截");
        logDTO.setOperator("SYSTEM");
        logDTO.setConfidence(confidence);
        logDTO.setExecuteTime(System.currentTimeMillis());
        logDTO.setRequestUri(requestUri);
        logDTO.setHttpMethod(httpMethod);
        logDTO.setRiskLevel(calculateRiskLevelByConfidence(confidence).name());
        
        return logDTO;
    }

    public static DefenseLogDTO buildCompositeLog(String targetIp, String eventId, Long attackId,
                                                  String triggerReason, Integer confidence,
                                                  List<DefenseAction> actions) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setAttackId(attackId);
        logDTO.setDefenseType(DefenseLogType.COMPOSITE.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(triggerReason);
        logDTO.setExecuteStatus(1);
        logDTO.setConfidence(confidence);
        logDTO.setExecuteTime(System.currentTimeMillis());
        logDTO.setOperator("SYSTEM");
        logDTO.setRiskLevel(calculateRiskLevelByConfidence(confidence).name());
        
        logDTO.setDefenseAction(buildDefenseActionSummary(actions));
        logDTO.setExecuteResult(buildCompositeDescription(actions));
        
        for (DefenseAction action : actions) {
            if (DefenseLogType.ADD_BLACKLIST.getCode().equals(action.getType()) 
                && action.getExpireTime() != null) {
                logDTO.setExpireTime(action.getExpireTime());
                break;
            }
            if (DefenseLogType.RATE_LIMIT.getCode().equals(action.getType())) {
                logDTO.setRateLimitCount(action.getCount());
                logDTO.setTimeWindow(action.getTimeWindow());
            }
        }
        
        return logDTO;
    }

    private static String buildCompositeDescription(List<DefenseAction> actions) {
        StringBuilder sb = new StringBuilder("组合防御措施: ");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            DefenseAction action = actions.get(i);
            sb.append(action.getDescription());
            if (action.getCount() > 1) {
                sb.append("(").append(action.getCount()).append("次)");
            }
        }
        return sb.toString();
    }

    private static String buildDefenseActionSummary(List<DefenseAction> actions) {
        StringBuilder sb = new StringBuilder();
        for (DefenseAction action : actions) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(action.getType());
            if (action.getCount() > 1) {
                sb.append("(").append(action.getCount()).append(")");
            }
        }
        return sb.toString();
    }

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

    public static boolean isValidDefenseLog(DefenseLogDTO logDTO) {
        if (logDTO == null) {
            return false;
        }

        return logDTO.getDefenseTarget() != null && !logDTO.getDefenseTarget().isEmpty() &&
               logDTO.getDefenseType() != null && !logDTO.getDefenseType().isEmpty();
    }

    public static String formatTimestamp(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(FORMATTER);
    }

    public static DefenseLogDTO buildManualBanLog(String targetIp, String eventId,
                                                  String operator, String reason,
                                                  Long expireTimestamp) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.MANUAL_BAN.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(reason);
        logDTO.setDefenseAction(DefenseLogType.MANUAL_BAN.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("管理员手动封禁IP");
        logDTO.setOperator(operator != null ? operator : "ADMIN");
        logDTO.setExecuteTime(System.currentTimeMillis());
        
        if (expireTimestamp != null) {
            LocalDateTime expireDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expireTimestamp), ZoneId.systemDefault());
            logDTO.setExpireTime(expireDateTime.format(FORMATTER));
        }
        
        return logDTO;
    }

    public static DefenseLogDTO buildManualUnbanLog(String targetIp, String eventId,
                                                    String operator, String reason) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.MANUAL_UNBAN.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(reason);
        logDTO.setDefenseAction(DefenseLogType.MANUAL_UNBAN.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("管理员手动解封IP");
        logDTO.setOperator(operator != null ? operator : "ADMIN");
        logDTO.setExecuteTime(System.currentTimeMillis());
        
        return logDTO;
    }

    public static DefenseLogDTO buildTempBanLog(String targetIp, String eventId,
                                                String reason, Integer confidence,
                                                Long expireTimestamp) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.TEMP_BAN.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(reason);
        logDTO.setDefenseAction(DefenseLogType.TEMP_BAN.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("临时封禁IP，到期自动解封");
        logDTO.setOperator("SYSTEM");
        logDTO.setConfidence(confidence);
        logDTO.setExecuteTime(System.currentTimeMillis());
        
        if (expireTimestamp != null) {
            LocalDateTime expireDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expireTimestamp), ZoneId.systemDefault());
            logDTO.setExpireTime(expireDateTime.format(FORMATTER));
        }
        
        return logDTO;
    }

    public static DefenseLogDTO buildStateResetLog(String targetIp, String eventId,
                                                   String operator, String reason,
                                                   int fromState, int toState) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.STATE_RESET.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(reason);
        logDTO.setDefenseAction(DefenseLogType.STATE_RESET.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult(String.format("状态重置: %s -> %s", 
            getStateNameZh(fromState), getStateNameZh(toState)));
        logDTO.setOperator(operator != null ? operator : "ADMIN");
        logDTO.setExecuteTime(System.currentTimeMillis());
        logDTO.setFromState(fromState);
        logDTO.setToState(toState);
        
        return logDTO;
    }

    public static DefenseLogDTO buildWhitelistAddLog(String targetIp, String eventId,
                                                     String operator, String reason) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.WHITELIST_ADD.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(reason);
        logDTO.setDefenseAction(DefenseLogType.WHITELIST_ADD.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("IP已加入白名单");
        logDTO.setOperator(operator != null ? operator : "ADMIN");
        logDTO.setExecuteTime(System.currentTimeMillis());
        
        return logDTO;
    }

    public static DefenseLogDTO buildWhitelistRemoveLog(String targetIp, String eventId,
                                                        String operator, String reason) {
        DefenseLogDTO logDTO = new DefenseLogDTO();
        logDTO.setEventId(eventId);
        logDTO.setDefenseType(DefenseLogType.WHITELIST_REMOVE.getCode());
        logDTO.setDefenseTarget(targetIp);
        logDTO.setDefenseReason(reason);
        logDTO.setDefenseAction(DefenseLogType.WHITELIST_REMOVE.getCode());
        logDTO.setExecuteStatus(1);
        logDTO.setExecuteResult("IP已从白名单移除");
        logDTO.setOperator(operator != null ? operator : "ADMIN");
        logDTO.setExecuteTime(System.currentTimeMillis());
        
        return logDTO;
    }

    private static String getStateNameZh(int state) {
        switch (state) {
            case 0: return "正常";
            case 1: return "可疑";
            case 2: return "攻击中";
            case 3: return "已防御";
            case 4: return "冷却期";
            default: return "未知";
        }
    }

    private static DefenseResultBO.RiskLevel calculateRiskLevelByConfidence(Integer confidence) {
        if (confidence == null) {
            return DefenseResultBO.RiskLevel.MEDIUM;
        }
        if (confidence >= 90) {
            return DefenseResultBO.RiskLevel.CRITICAL;
        } else if (confidence >= 70) {
            return DefenseResultBO.RiskLevel.HIGH;
        } else if (confidence >= 50) {
            return DefenseResultBO.RiskLevel.MEDIUM;
        } else {
            return DefenseResultBO.RiskLevel.LOW;
        }
    }
}
