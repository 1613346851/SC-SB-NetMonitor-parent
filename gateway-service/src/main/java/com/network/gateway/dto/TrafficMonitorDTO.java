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
     * 请求方法 (GET/POST/PUT/DELETE等)
     */
    private String method;

    /**
     * 请求URI
     */
    private String uri;

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
    private Map<String, String> headers;

    /**
     * 用户代理信息
     */
    private String userAgent;

    /**
     * 请求时间戳
     */
    private Long requestTimestamp;

    /**
     * 响应时间戳
     */
    private Long responseTimestamp;

    /**
     * 响应状态码
     */
    private Integer statusCode;

    /**
     * 响应体大小（字节）
     */
    private Long responseBodySize;

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
     * 构造函数（用于创建新的流量监控记录）
     *
     * @param requestId 请求ID
     * @param sourceIp 源IP
     * @param method 请求方法
     * @param uri 请求URI
     * @param requestTimestamp 请求时间戳
     */
    public TrafficMonitorDTO(String requestId, String sourceIp, String method, 
                           String uri, Long requestTimestamp) {
        this.requestId = requestId;
        this.sourceIp = sourceIp;
        this.method = method;
        this.uri = uri;
        this.requestTimestamp = requestTimestamp;
        this.success = true; // 默认成功
    }

    /**
     * 设置响应相关信息
     *
     * @param responseTimestamp 响应时间戳
     * @param statusCode 状态码
     * @param responseBodySize 响应体大小
     */
    public void setResponseInfo(Long responseTimestamp, Integer statusCode, Long responseBodySize) {
        this.responseTimestamp = responseTimestamp;
        this.statusCode = statusCode;
        this.responseBodySize = responseBodySize;
        this.processingTime = responseTimestamp - this.requestTimestamp;
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
}