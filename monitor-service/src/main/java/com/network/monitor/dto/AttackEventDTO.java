package com.network.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttackEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sourceIp;
    private String attackType;
    private String riskLevel;
    private int confidence;
    private String eventId;
    
    private String ruleName;
    private String ruleId;
    private String attackContent;
    private String targetUri;
    private String httpMethod;
    private String userAgent;
    
    private String requestId;
    private Map<String, String> queryParams;
    private Map<String, String> requestHeaders;
    private String requestBody;
    
    private String description;
    private Long timestamp;
    private String traceId;
}
