package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 扫描目标配置实体类
 * 对应数据库表：sys_scan_target
 */
@Data
public class ScanTargetEntity {

    /**
     * 目标ID
     */
    private Long id;

    /**
     * 目标名称
     */
    private String targetName;

    /**
     * 目标URL
     */
    private String targetUrl;

    /**
     * 目标类型（PRODUCTION/TEST/DEMO）
     */
    private String targetType;

    /**
     * 描述
     */
    private String description;

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
