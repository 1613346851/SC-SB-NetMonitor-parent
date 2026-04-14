package com.network.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * 自定义扫描请求DTO
 */
@Data
public class CustomScanRequest {

    /**
     * 要扫描的接口ID列表
     */
    private List<Long> interfaceIds;

    /**
     * 扫描类型（可选，默认为CUSTOM）
     */
    private String scanType;
}
