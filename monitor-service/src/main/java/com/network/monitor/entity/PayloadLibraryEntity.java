package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Payload库实体类
 * 对应数据库表：sys_payload_library
 */
@Data
public class PayloadLibraryEntity {

    /**
     * Payload ID
     */
    private Long id;

    /**
     * 漏洞类型
     */
    private String vulnType;

    /**
     * Payload级别（BASIC/ADVANCED/CUSTOM）
     */
    private String payloadLevel;

    /**
     * Payload内容
     */
    private String payloadContent;

    /**
     * 匹配关键词（逗号分隔）
     */
    private String matchKeywords;

    /**
     * 描述
     */
    private String description;

    /**
     * 风险等级
     */
    private String riskLevel;

    /**
     * 参考链接
     */
    private String references;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    private Integer enabled = 1;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
