package com.network.gateway.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 原始流量业务对象
 * 网关内部使用的流量信息封装对象，用于流量采集和预处理阶段
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawTrafficBO {

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
     * 请求方法
     */
    private String method;

    /**
     * 请求URI
     */
    private String uri;

    /**
     * 查询参数Map
     */
    private Map<String, String> queryParams;

    /**
     * 原始查询参数字符串
     */
    private String rawQueryParams;

    /**
     * 请求体内容
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
     * 响应体大小
     */
    private Long responseBodySize;

    /**
     * 是否为异常流量
     */
    private Boolean abnormalTraffic;

    /**
     * 异常原因
     */
    private String abnormalReason;

    /**
     * 请求处理耗时
     */
    private Long processingTime;

    /**
     * 是否成功处理
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 构造函数（用于创建新的原始流量记录）
     *
     * @param requestId 请求ID
     * @param sourceIp 源IP
     * @param method 请求方法
     * @param uri 请求URI
     * @param requestTimestamp 请求时间戳
     */
    public RawTrafficBO(String requestId, String sourceIp, String method, 
                       String uri, Long requestTimestamp) {
        this.requestId = requestId;
        this.sourceIp = sourceIp;
        this.method = method;
        this.uri = uri;
        this.requestTimestamp = requestTimestamp;
        this.success = true; // 默认成功
        this.abnormalTraffic = false; // 默认不是异常流量
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

    /**
     * 检查是否为有效流量（非健康检查、非管理端点）
     *
     * @return true表示是有效流量
     */
    public boolean isValidTraffic() {
        // 过滤健康检查请求
        if (this.uri != null && this.uri.contains("/actuator/health")) {
            return false;
        }
        
        // 过滤网关管理端点
        if (this.uri != null && this.uri.startsWith("/actuator/")) {
            return false;
        }
        
        // 过滤空请求
        if (this.method == null || this.uri == null) {
            return false;
        }
        
        return true;
    }

    /**
     * 检查是否为大请求体
     *
     * @param maxSize 最大大小（字节）
     * @return true表示请求体过大
     */
    public boolean isLargeRequestBody(int maxSize) {
        if (this.requestBody == null) {
            return false;
        }
        return this.requestBody.getBytes().length > maxSize;
    }

    /**
     * 检查是否为慢响应
     *
     * @param threshold 阈值（毫秒）
     * @return true表示响应过慢
     */
    public boolean isSlowResponse(long threshold) {
        return this.processingTime != null && this.processingTime > threshold;
    }

    /**
     * 获取简化的流量摘要信息
     *
     * @return 流量摘要
     */
    public String getTrafficSummary() {
        return String.format("请求[%s] %s %s 来自IP[%s] 状态码[%d] 耗时[%dms]",
                this.requestId, this.method, this.uri, this.sourceIp, 
                this.statusCode, this.processingTime);
    }

    /**
     * 获取异常流量详情
     *
     * @return 异常详情
     */
    public String getAbnormalDetail() {
        if (!this.abnormalTraffic) {
            return "正常流量";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("异常流量: ").append(this.abnormalReason);
        
        if (this.isSlowResponse(3000)) {
            sb.append(", 响应时间过长(").append(this.processingTime).append("ms)");
        }
        
        if (this.isLargeRequestBody(100 * 1024)) {
            sb.append(", 请求体过大(").append(this.requestBody.length()).append("字符)");
        }
        
        return sb.toString();
    }
}