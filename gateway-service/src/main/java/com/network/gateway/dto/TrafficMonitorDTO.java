package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 流量监控DTO
 * 用于网关向监控服务推送流量信息的数据传输对象
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrafficMonitorDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求唯一标识
     */
    private String requestId;

    /**
     * 源IP地址
     */
    private String sourceIp;

    /**
     * 目标服务IP
     */
    private String targetIp;

    /**
     * HTTP 方法
     */
    private String httpMethod;

    /**
     * 请求 URI
     */
    private String requestUri;

    /**
     * 查询参数
     */
    private Map<String, String> queryParams;

    /**
     * 请求体内容（可能为空）
     */
    private String requestBody;

    /**
     * 请求头信息
     */
    private Map<String, String> requestHeaders;

    /**
     * 用户代理信息
     */
    private String userAgent;

    /**
     * 请求时间
     */
    private String requestTime;

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
     * Cookie 信息
     */
    private String cookie;

    /**
     * 协议类型
     */
    private String protocol;

    /**
     * 源端口
     */
    private Integer sourcePort;

    /**
     * 目标端口
     */
    private Integer targetPort;

    /**
     * 是否为异常流量
     */
    private Boolean abnormalTraffic;

    /**
     * 异常原因（如果abnormalTraffic为true）
     */
    private String abnormalReason;

    /**
     * 请求处理耗时（毫秒）
     */
    private Long processingTime;

    /**
     * 是否成功处理
     */
    private Boolean success;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

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
     * 构造函数（用于创建新的流量监控记录）
     *
     * @param requestId 请求 ID
     * @param sourceIp 源 IP
     * @param httpMethod 请求方法
     * @param requestUri 请求 URI
     * @param requestTime 请求时间
     */
    public TrafficMonitorDTO(String requestId, String sourceIp, String httpMethod, 
                           String requestUri, String requestTime) {
        this.requestId = requestId;
        this.sourceIp = sourceIp;
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.requestTime = requestTime;
        this.success = true; // 默认成功
    }

    /**
     * 设置响应相关信息
     *
     * @param responseStatus 状态码
     * @param responseBody 响应体
     * @param responseTime 响应时间
     */
    public void setResponseInfo(Integer responseStatus, String responseBody, Long responseTime) {
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseTime = responseTime;
    }

    /**
     * 标记为异常流量
     *
     * @param reason 异常原因
     */
    public void markAsAbnormal(String reason) {
        this.abnormalTraffic = true;
        this.abnormalReason = reason;
    }

    /**
     * 标记处理失败
     *
     * @param errorMessage 错误信息
     */
    public void markAsFailed(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
    }

    /**
     * 判断是否跳过推送
     *
     * @return true表示跳过推送
     */
    public boolean isSkipPush() {
        return skipPush != null && skipPush;
    }
}