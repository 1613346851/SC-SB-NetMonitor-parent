package com.network.monitor.controller.inner;

import com.network.monitor.cache.RuleCache;
import com.network.monitor.common.ApiResponse;
import com.network.monitor.dto.AttackRuleDTO;
import com.network.monitor.dto.WhitelistDTO;
import com.network.monitor.entity.MonitorRuleEntity;
import com.network.monitor.entity.VulnerabilityMonitorEntity;
import com.network.monitor.entity.WhitelistEntity;
import com.network.monitor.mapper.MonitorRuleMapper;
import com.network.monitor.mapper.VulnerabilityMonitorMapper;
import com.network.monitor.mapper.WhitelistMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/inner/gateway")
public class GatewayRuleController {

    @Autowired
    private MonitorRuleMapper monitorRuleMapper;

    @Autowired
    private WhitelistMapper whitelistMapper;

    @Autowired
    private VulnerabilityMonitorMapper vulnerabilityMonitorMapper;

    @Autowired
    private RuleCache ruleCache;

    @GetMapping("/rules")
    public ApiResponse<List<AttackRuleDTO>> getRulesForGateway() {
        try {
            List<MonitorRuleEntity> enabledRules = monitorRuleMapper.selectAllEnabled();
            
            List<AttackRuleDTO> ruleDTOs = enabledRules.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            log.info("网关拉取规则成功，共{}条", ruleDTOs.size());
            return ApiResponse.success(ruleDTOs);
        } catch (Exception e) {
            log.error("网关拉取规则失败", e);
            return ApiResponse.error("获取规则失败: " + e.getMessage());
        }
    }

    @GetMapping("/whitelists")
    public ApiResponse<List<WhitelistDTO>> getWhitelistsForGateway() {
        try {
            List<WhitelistEntity> enabledWhitelists = whitelistMapper.selectAllEnabled();
            
            List<WhitelistDTO> whitelistDTOs = enabledWhitelists.stream()
                    .map(this::convertWhitelistToDTO)
                    .collect(Collectors.toList());
            
            log.info("网关拉取白名单成功，共{}条", whitelistDTOs.size());
            return ApiResponse.success(whitelistDTOs);
        } catch (Exception e) {
            log.error("网关拉取白名单失败", e);
            return ApiResponse.error("获取白名单失败: " + e.getMessage());
        }
    }

    @GetMapping("/vulnerabilities")
    public ApiResponse<List<Map<String, Object>>> getVulnerabilitiesForGateway() {
        try {
            List<VulnerabilityMonitorEntity> vulns = vulnerabilityMonitorMapper.selectAll();
            
            List<Map<String, Object>> vulnDTOs = vulns.stream()
                    .map(this::convertVulnToMap)
                    .collect(Collectors.toList());
            
            log.info("网关拉取漏洞成功，共{}条", vulnDTOs.size());
            return ApiResponse.success(vulnDTOs);
        } catch (Exception e) {
            log.error("网关拉取漏洞失败", e);
            return ApiResponse.error("获取漏洞失败: " + e.getMessage());
        }
    }

    private AttackRuleDTO convertToDTO(MonitorRuleEntity entity) {
        AttackRuleDTO dto = new AttackRuleDTO();
        dto.setId(entity.getId());
        dto.setRuleName(entity.getRuleName());
        dto.setAttackType(entity.getAttackType());
        dto.setRuleContent(entity.getRuleContent());
        dto.setDescription(entity.getDescription());
        dto.setRiskLevel(entity.getRiskLevel());
        dto.setEnabled(entity.getEnabled());
        dto.setPriority(entity.getPriority() != null ? entity.getPriority() : 100);
        return dto;
    }

    private WhitelistDTO convertWhitelistToDTO(WhitelistEntity entity) {
        WhitelistDTO dto = new WhitelistDTO();
        dto.setId(entity.getId());
        dto.setWhitelistType(entity.getWhitelistType());
        dto.setWhitelistValue(entity.getWhitelistValue());
        dto.setDescription(entity.getDescription());
        dto.setEnabled(entity.getEnabled());
        dto.setPriority(entity.getPriority() != null ? entity.getPriority() : 100);
        return dto;
    }

    private Map<String, Object> convertVulnToMap(VulnerabilityMonitorEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entity.getId());
        map.put("vulnName", entity.getVulnName());
        map.put("vulnType", entity.getVulnType());
        map.put("vulnLevel", entity.getVulnLevel());
        map.put("vulnPath", entity.getVulnPath());
        map.put("verifyStatus", entity.getVerifyStatus());
        map.put("defenseStatus", entity.getDefenseStatus());
        map.put("ruleCount", entity.getRuleCount());
        map.put("ruleIds", entity.getRuleIds());
        return map;
    }
}
