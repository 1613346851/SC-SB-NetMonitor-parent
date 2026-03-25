package com.network.monitor.dto;

import lombok.Data;
import java.util.Map;

/**
 * 流量监测数据传输对象
 */
@Data
public class TrafficMonitorDTO {

    /**
     * 请求唯一标识（网关生成）
     */
    private String requestId;

    /**
     * 请求时间
     */
    private String requestTime;

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
     * 查询参数（Map 格式）
     */
    private Map<String, String> queryParams;

    /**
     * 请求头（JSON 格式）
     */
    private Map<String, String> requestHeaders;

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
     * 是否跳过推送（DEFENDED状态的IP不推送流量）
     */
    private Boolean skipPush;

    /**
     * 请求次数（聚合统计）
     */
    private Integer requestCount;

    /**
     * IP状态标签
     */
    private String stateTag;

    /**
     * 是否为聚合记录
     */
    private Boolean isAggregated;

    /**
     * 聚合开始时间
     */
    private String aggregateStartTime;

    /**
     * 聚合结束时间
     */
    private String aggregateEndTime;

    /**
     * 错误次数（聚合统计）
     */
    private Integer errorCount;

    /**
     * 平均处理时间（毫秒）
     */
    private Long avgProcessingTime;

    /**
     * 判断是否跳过推送
     *
     * @return true表示跳过推送
     */
    public boolean isSkipPush() {
        return skipPush != null && skipPush;
    }

    /**
     * 判断是否为聚合记录
     *
     * @return true表示为聚合记录
     */
    public boolean isAggregated() {
        return isAggregated != null && isAggregated;
    }
}
