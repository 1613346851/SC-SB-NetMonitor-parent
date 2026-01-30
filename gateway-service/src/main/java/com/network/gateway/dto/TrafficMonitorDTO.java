package com.network.gateway.dto;
import lombok.Data;
import java.time.LocalDateTime;


@Data
public class TrafficMonitorDTO {
    // 流量ID（用UUID生成）
    private String id;
    // 源IP地址
    private String sourceIp;
    // 目标服务IP（暂时存网关IP，后续转发到靶场后替换）
    private String targetServiceIp;
    // 请求方法（GET/POST等）
    private String method;
    // 请求URI
    private String uri;
    // 查询参数（比如?name=test）
    private String queryParams;
    // 请求体（POST请求的参数）
    private String requestBody;
    // 响应状态码（200/404等）
    private Integer responseCode;
    // 响应时间（毫秒）
    private Long responseTime;
    // 是否异常流量（响应时间>3s/请求体>100KB标记为true）
    private Boolean isAbnormal;
    // 采集时间
    private LocalDateTime createTime;
}
