package com.network.monitor.bo;

import lombok.Data;

/**
 * 原始流量业务对象
 * 用于封装原始流量数据的业务处理逻辑
 */
@Data
public class RawTrafficBO {

    /**
     * 流量 ID
     */
    private Long trafficId;

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
     * 请求方法（GET/POST/PUT/DELETE 等）
     */
    private String method;

    /**
     * 请求 URI
     */
    private String requestUri;

    /**
     * 查询参数
     */
    private String queryParams;

    /**
     * 请求体
     */
    private String requestBody;

    /**
     * 请求头（JSON 格式）
     */
    private String headers;

    /**
     * 协议版本
     */
    private String protocol;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 是否已预处理
     */
    private boolean preprocessed;

    /**
     * 是否有效流量
     */
    private boolean valid;

    /**
     * 标记为已预处理
     */
    public void markPreprocessed() {
        this.preprocessed = true;
    }

    /**
     * 标记为无效流量
     */
    public void markInvalid() {
        this.valid = false;
    }

    /**
     * 标记为有效流量
     */
    public void markValid() {
        this.valid = true;
    }
}
