package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 扫描接口配置实体类
 * 对应数据库表：sys_scan_interface
 */
@Data
public class ScanInterfaceEntity {

    /**
     * 接口ID
     */
    private Long id;

    /**
     * 关联目标ID
     */
    private Long targetId;

    /**
     * 接口名称
     */
    private String interfaceName;

    /**
     * 接口路径
     */
    private String interfacePath;

    /**
     * HTTP方法
     */
    private String httpMethod;

    /**
     * 漏洞类型
     */
    private String vulnType;

    /**
     * 风险等级
     */
    private String riskLevel;

    /**
     * 参数配置（JSON）
     */
    private String paramsConfig;

    /**
     * Payload配置（JSON）
     */
    private String payloadConfig;

    /**
     * 匹配规则（JSON）
     */
    private String matchRules;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    private Integer enabled = 1;

    /**
     * 扫描优先级
     */
    private Integer priority = 100;

    /**
     * 防御规则状态（0-未配置，1-部分已配置，2-已配置）
     */
    private Integer defenseRuleStatus = 0;

    /**
     * 关联防御规则数量
     */
    private Integer defenseRuleCount = 0;

    /**
     * 防御规则说明
     */
    private String defenseRuleNote;

    /**
     * 业务功能类型
     * USER_INPUT - 用户输入处理
     * DATA_QUERY - 数据查询
     * DATA_SUBMIT - 数据提交
     * FILE_OPERATION - 文件操作
     * FILE_UPLOAD - 文件上传
     * URL_FETCH - URL获取
     * COMMAND_EXEC - 命令执行
     * AUTH_RELATED - 认证相关
     * XML_PROCESS - XML处理
     * CONFIG_ACCESS - 配置访问
     * API_PROXY - API代理
     */
    private String businessType;

    /**
     * 输入参数描述（JSON数组）
     */
    private String inputParams;

    /**
     * 输出类型（JSON, HTML, XML, FILE, BINARY）
     */
    private String outputType = "JSON";

    /**
     * 是否需要认证（0-否，1-是）
     */
    private Integer authRequired = 0;

    /**
     * 请求内容类型
     */
    private String contentType = "application/json";

    /**
     * 是否发起外部请求（0-否，1-是）
     */
    private Integer externalRequest = 0;

    /**
     * 是否涉及文件操作（0-否，1-是）
     */
    private Integer fileOperation = 0;

    /**
     * 是否涉及数据库操作（0-否，1-是）
     */
    private Integer dbOperation = 0;

    /**
     * 推断的漏洞类型（JSON数组）
     */
    private String inferredVulnTypes;

    /**
     * 扫描状态（PENDING, SCANNING, COMPLETED, FAILED）
     */
    private String scanStatus = "PENDING";

    /**
     * 最后扫描时间
     */
    private LocalDateTime lastScanTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
