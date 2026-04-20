package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 扫描历史实体类
 */
@Data
public class ScanHistoryEntity {

    private Long id;

    private String taskId;

    private String scanType;

    private String target;

    private String status;

    private Integer discoveredCount;

    private Integer completedInterfaces;

    private Integer totalInterfaces;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationSeconds;

    private String summary;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
