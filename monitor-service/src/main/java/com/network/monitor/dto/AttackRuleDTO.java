package com.network.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttackRuleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String ruleName;

    private String attackType;

    private String ruleContent;

    private String description;

    private String riskLevel;

    private Integer enabled;

    private Integer priority;

    private Long timestamp;

    private String operation;
}
