package com.network.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * 接口特征DTO（用于漏洞推断）
 */
@Data
public class InterfaceFeatureDTO {

    /**
     * 业务功能类型
     */
    private String businessType;

    /**
     * HTTP方法
     */
    private String httpMethod;

    /**
     * 输出类型
     */
    private String outputType;

    /**
     * 是否需要认证
     */
    private Integer authRequired;

    /**
     * 请求内容类型
     */
    private String contentType;

    /**
     * 是否发起外部请求
     */
    private Integer externalRequest;

    /**
     * 是否涉及文件操作
     */
    private Integer fileOperation;

    /**
     * 是否涉及数据库操作
     */
    private Integer dbOperation;

    /**
     * 输入参数列表
     */
    private List<InputParamDTO> inputParams;
}
