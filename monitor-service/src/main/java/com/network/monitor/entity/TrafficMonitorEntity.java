package com.network.monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流量监测实体类
 * 对应数据库表：sys_traffic_monitor
 */
@Data
public class TrafficMonitorEntity {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 请求时间
     */
    private LocalDateTime requestTime;

    /**
     * 源 IP 地址
     */
    private String sourceIp;

    /**
     * 目标 IP 地址
     */
    private String targetIp;

    /**
     * 源端口
     */
    private Integer sourcePort;

    /**
     * 目标端口
     */
    private Integer targetPort;

    /**
     * HTTP 方法
     */
    private String httpMethod;

    /**
     * 协议类型（HTTP/1.0、HTTP/1.1、HTTP/2、HTTPS 等）
     */
    private String protocol;

    /**
     * 请求 URI
     */
    private String requestUri;

    /**
     * 查询参数
     */
    private String queryParams;

    /**
     * 请求头（JSON 格式）
     */
    private String requestHeaders;

    /**
     * 请求体
     */
    private String requestBody;

    /**
     * 响应状态码
     */
    private Integer responseStatus;

    /**
     * 响应体
     */
    private String responseBody;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTime;

    /**
     * 内容类型
     */
    private String contentType;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * Cookie 信息
     */
    private String cookie;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
