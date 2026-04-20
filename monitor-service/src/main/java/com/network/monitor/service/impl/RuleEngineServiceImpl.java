package com.network.monitor.service.impl;

import com.network.monitor.common.constant.AttackTypeConstant;
import com.network.monitor.common.constant.RiskLevelConstant;
import com.network.monitor.common.util.AttackContentDecodeUtil;
import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.mapper.MonitorRuleMapper;
import com.network.monitor.service.LocalCacheService;
import com.network.monitor.service.RuleEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 规则引擎服务实现类 - 增强型攻击检测核心
 */
@Slf4j
@Service
public class RuleEngineServiceImpl implements RuleEngineService {

    @Autowired
    private LocalCacheService localCacheService;

    @Autowired
    private MonitorRuleMapper monitorRuleMapper;

    @Override
    public List<AttackMonitorDTO> executeMatching(TrafficMonitorDTO trafficDTO) {
        List<AttackMonitorDTO> allAttacks = new ArrayList<>();

        // 执行各类攻击检测
        allAttacks.addAll(matchSqlInjection(trafficDTO));
        allAttacks.addAll(matchXss(trafficDTO));
        allAttacks.addAll(matchCommandInjection(trafficDTO));
        allAttacks.addAll(matchPathTraversal(trafficDTO));

        if (!allAttacks.isEmpty()) {
            log.info("规则引擎检测到攻击，数量：{}, 源 IP: {}", 
                allAttacks.size(), trafficDTO.getSourceIp());
        }

        return allAttacks;
    }

    @Override
    public List<AttackMonitorDTO> matchSqlInjection(TrafficMonitorDTO trafficDTO) {
        return detectAttack(trafficDTO, AttackTypeConstant.SQL_INJECTION);
    }

    @Override
    public List<AttackMonitorDTO> matchXss(TrafficMonitorDTO trafficDTO) {
        return detectAttack(trafficDTO, AttackTypeConstant.XSS);
    }

    @Override
    public List<AttackMonitorDTO> matchCommandInjection(TrafficMonitorDTO trafficDTO) {
        return detectAttack(trafficDTO, AttackTypeConstant.COMMAND_INJECTION);
    }

    @Override
    public List<AttackMonitorDTO> matchPathTraversal(TrafficMonitorDTO trafficDTO) {
        return detectAttack(trafficDTO, AttackTypeConstant.PATH_TRAVERSAL);
    }

    /**
     * 通用攻击检测逻辑
     */
    private List<AttackMonitorDTO> detectAttack(TrafficMonitorDTO trafficDTO, String attackType) {
        List<AttackMonitorDTO> attacks = new ArrayList<>();

        try {
            @SuppressWarnings("unchecked")
            List<MonitorRuleEntity> rules = (List<MonitorRuleEntity>) 
                localCacheService.get("cache:rule:" + attackType);
            
            if (rules == null || rules.isEmpty()) {
                rules = monitorRuleMapper.selectByAttackType(attackType);
                if (!rules.isEmpty()) {
                    localCacheService.put("cache:rule:" + attackType, rules, -1);
                }
            }

            if (rules == null || rules.isEmpty()) {
                return attacks;
            }

            String requestUri = trafficDTO.getRequestUri();
            String queryParamsStr = buildQueryString(trafficDTO.getQueryParams());
            
            String urlPath = extractUrlPath(requestUri);
            String urlQuery = extractUrlQuery(requestUri);
            if (urlQuery == null && queryParamsStr != null) {
                urlQuery = queryParamsStr;
            }
            
            String[] checkFields = {
                urlPath,
                urlQuery,
                trafficDTO.getRequestBody()
            };
            
            boolean isCommandInjection = AttackTypeConstant.COMMAND_INJECTION.equals(attackType);
            boolean hasHighRiskChars = isCommandInjection && hasHighRiskCharacters(urlPath);

            for (MonitorRuleEntity rule : rules) {
                for (int i = 0; i < checkFields.length; i++) {
                    String fieldContent = checkFields[i];
                    if (fieldContent == null || fieldContent.isEmpty()) {
                        continue;
                    }
                    
                    boolean isUrlPathField = (i == 0);
                    boolean isLowRiskUrlPathCommand = isCommandInjection && isUrlPathField && 
                        isLowRiskCommandInPath(fieldContent, rule);

                    String normalizedContent = AttackContentDecodeUtil.normalize(fieldContent);
                    
                    if (matchRule(fieldContent, rule) || matchRule(normalizedContent, rule)) {
                        AttackMonitorDTO attack = buildAttackDTO(trafficDTO, attackType, rule, fieldContent);
                        
                        if (isLowRiskUrlPathCommand && !hasHighRiskChars) {
                            attack.setRiskLevel(RiskLevelConstant.LOW);
                            String originalContent = attack.getAttackContent();
                            attack.setAttackContent(originalContent + "\n[URL路径中的命令关键字，可能是误报或低风险漏洞]");
                        }
                        
                        attacks.add(attack);
                        
                        log.debug("命中{}规则：ruleId={}, ruleName={}, sourceIp={}, isLowRiskPath={}", 
                            attackType, rule.getId(), rule.getRuleName(), trafficDTO.getSourceIp(), isLowRiskUrlPathCommand);
                        break;
                    }
                }
                
                if (!attacks.isEmpty()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("{}检测失败：", attackType, e);
        }

        return attacks;
    }
    
    private String extractUrlPath(String url) {
        if (url == null) return null;
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            return url.substring(0, queryIndex);
        }
        return url;
    }
    
    private String extractUrlQuery(String url) {
        if (url == null) return null;
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0 && queryIndex < url.length() - 1) {
            return url.substring(queryIndex + 1);
        }
        return null;
    }
    
    private boolean isLowRiskCommandInPath(String path, MonitorRuleEntity rule) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.contains("?")) {
            return false;
        }
        String ruleName = rule.getRuleName();
        if (ruleName != null && (
            ruleName.contains("常见命令") || 
            ruleName.contains("Windows 命令"))) {
            return true;
        }
        return false;
    }
    
    private boolean hasHighRiskCharacters(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return content.contains("|") || 
               content.contains(";") || 
               content.contains("`") ||
               content.contains("$(") ||
               content.contains("&&") ||
               content.contains("||");
    }
    
    private String buildQueryString(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 规则匹配逻辑（支持变形攻击处理）
     */
    private boolean matchRule(String content, MonitorRuleEntity rule) {
        String ruleContent = rule.getRuleContent();
        if (ruleContent == null || ruleContent.isEmpty()) {
            return false;
        }

        // 标准化处理：解码、过滤空白字符和注释
        String normalizedContent = normalizeContent(content);
        
        try {
            // 尝试正则匹配
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                ruleContent, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(normalizedContent);
            return matcher.find();
        } catch (Exception e) {
            // 正则表达式无效时，使用关键词匹配
            return normalizedContent.toLowerCase().contains(ruleContent.toLowerCase());
        }
    }

    /**
     * 标准化处理内容（变形攻击兼容）
     * 包括：URL 解码、Unicode 解码、过滤空白字符、过滤 SQL 注释
     */
    private String normalizeContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String result = content;
        
        // 1. 多层 URL 解码（最多 3 层）
        for (int i = 0; i < 3; i++) {
            String decoded = java.net.URLDecoder.decode(result, java.nio.charset.StandardCharsets.UTF_8);
            if (decoded.equals(result)) {
                break; // 没有变化则停止解码
            }
            result = decoded;
        }
        
        // 2. Unicode 解码
        result = decodeUnicode(result);
        
        // 3. 过滤空白字符（空格、制表符、换行符等）
        result = filterWhitespace(result);
        
        // 4. 过滤 SQL 注释（--、#、/**/等）
        result = filterSqlComments(result);
        
        return result;
    }

    /**
     * 过滤空白字符
     */
    private String filterWhitespace(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        // 移除所有空白字符：空格、制表符、换行符、回车符等
        return content.replaceAll("\\s+", "");
    }

    /**
     * 过滤 SQL 注释
     */
    private String filterSqlComments(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        String result = content;
        
        // 移除 -- 注释
        result = result.replaceAll("--[^\\n]*", "");
        
        // 移除 # 注释
        result = result.replaceAll("#[^\\n]*", "");
        
        // 移除 /* */ 注释
        result = result.replaceAll("/\\*.*?\\*/", "");
        
        return result;
    }

    /**
     * Unicode 解码
     */
    private String decodeUnicode(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        StringBuilder sb = new StringBuilder();
        int i = 0;
        
        while (i < content.length()) {
            if (i + 5 < content.length() && "\\u".equals(content.substring(i, i + 2))) {
                try {
                    String hex = content.substring(i + 2, i + 6);
                    char ch = (char) Integer.parseInt(hex, 16);
                    sb.append(ch);
                    i += 6;
                } catch (Exception e) {
                    sb.append(content.charAt(i));
                    i++;
                }
            } else {
                sb.append(content.charAt(i));
                i++;
            }
        }
        
        return sb.toString();
    }

    /**
     * 构建攻击监测 DTO
     */
    private AttackMonitorDTO buildAttackDTO(TrafficMonitorDTO trafficDTO, 
                                            String attackType, 
                                            MonitorRuleEntity rule,
                                            String attackContent) {
        AttackMonitorDTO dto = new AttackMonitorDTO();
        
        // TODO: trafficId 需要在流量持久化后获取
        dto.setTrafficId(null);
        dto.setAttackType(attackType);
        dto.setRiskLevel(rule.getRiskLevel() != null ? rule.getRiskLevel() : RiskLevelConstant.MEDIUM);
        dto.setConfidence(calculateConfidence(attackType, trafficDTO));
        dto.setRuleId(rule.getId());
        dto.setRuleContent(rule.getRuleContent());
        dto.setSourceIp(trafficDTO.getSourceIp());
        dto.setTargetUri(trafficDTO.getRequestUri());
        dto.setAttackContent(truncateContent(attackContent, 500));
        
        return dto;
    }

    /**
     * 计算攻击置信度（0-100）
     */
    private int calculateConfidence(String attackType, TrafficMonitorDTO trafficDTO) {
        int confidence = 70; // 基础置信度

        // 多字段同时命中，提升置信度
        int hitCount = 0;
        if (trafficDTO.getRequestUri() != null && !trafficDTO.getRequestUri().isEmpty()) {
            hitCount++;
        }
        if (trafficDTO.getQueryParams() != null && !trafficDTO.getQueryParams().isEmpty()) {
            hitCount++;
        }
        if (trafficDTO.getRequestBody() != null && !trafficDTO.getRequestBody().isEmpty()) {
            hitCount++;
        }

        if (hitCount >= 2) {
            confidence += 15; // 多字段命中，置信度 +15
        }

        if (hitCount >= 3) {
            confidence += 10; // 三个字段都命中，再加 10
        }

        // 特殊攻击类型额外加成
        if (AttackTypeConstant.SQL_INJECTION.equals(attackType) || 
            AttackTypeConstant.COMMAND_INJECTION.equals(attackType)) {
            confidence += 5; // 高危攻击类型，置信度 +5
        }

        return Math.min(confidence, 100); // 不超过 100
    }

    /**
     * 截断内容长度
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
