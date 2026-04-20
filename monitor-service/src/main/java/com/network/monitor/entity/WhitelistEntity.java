package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检测白名单实体类
 * 对应数据库表：sys_detection_whitelist
 */
@Data
public class WhitelistEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 白名单类型（PATH-路径白名单，HEADER-请求头白名单，IP-IP白名单）
     */
    private String whitelistType;

    /**
     * 白名单值（路径模式/请求头名称/IP地址）
     */
    private String whitelistValue;

    /**
     * 描述
     */
    private String description;

    /**
     * 启用状态（0-禁用，1-启用）
     */
    private Integer enabled = 1;

    /**
     * 优先级（数字越小优先级越高）
     */
    private Integer priority = 100;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
