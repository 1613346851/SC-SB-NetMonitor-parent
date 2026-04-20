package com.network.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 漏洞推断结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VulnInferenceResult {

    /**
     * 漏洞类型
     */
    private String vulnType;

    /**
     * 推断原因
     */
    private String reason;

    /**
     * 推断的具体规则ID列表（细粒度：每个规则对应一个具体漏洞）
     */
    private List<Long> ruleIds;

    public VulnInferenceResult(String vulnType, String reason) {
        this.vulnType = vulnType;
        this.reason = reason;
    }
}
