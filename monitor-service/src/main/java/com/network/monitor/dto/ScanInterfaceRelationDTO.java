package com.network.monitor.dto;

import lombok.Data;

/**
 * 扫描接口关联信息DTO
 */
@Data
public class ScanInterfaceRelationDTO {

    private Long interfaceId;

    private String interfaceName;

    private String interfacePath;

    private String vulnType;

    private Integer ruleCount;

    private Integer vulnCount;

    private Integer verifiedCount;

    private Integer defenseRuleStatus;

    private Integer defenseRuleCount;

    private Integer enabled;

    public ScanInterfaceRelationDTO() {
    }

    public ScanInterfaceRelationDTO(Long interfaceId, String interfaceName, String interfacePath, 
                                    String vulnType, Integer ruleCount, Integer vulnCount, 
                                    Integer verifiedCount, Integer defenseRuleStatus, Integer defenseRuleCount, Integer enabled) {
        this.interfaceId = interfaceId;
        this.interfaceName = interfaceName;
        this.interfacePath = interfacePath;
        this.vulnType = vulnType;
        this.ruleCount = ruleCount;
        this.vulnCount = vulnCount;
        this.verifiedCount = verifiedCount;
        this.defenseRuleStatus = defenseRuleStatus;
        this.defenseRuleCount = defenseRuleCount;
        this.enabled = enabled;
    }
}
