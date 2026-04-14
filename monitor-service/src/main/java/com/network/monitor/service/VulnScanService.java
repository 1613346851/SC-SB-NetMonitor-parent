package com.network.monitor.service;

import java.util.List;
import java.util.Map;

/**
 * 主动漏洞扫描服务
 */
public interface VulnScanService {

    Map<String, Object> startScan(String scanType);

    Map<String, Object> getScanProgress();

    Map<String, Object> getScanResult();

    Map<String, Object> syncCurrentResults();

    Map<String, Object> controlScan(String action);

    Map<String, Object> getScanInterfaces(String scanType);

    /**
     * 启动自定义扫描
     * @param interfaceIds 要扫描的接口ID列表
     * @return 扫描任务状态
     */
    Map<String, Object> startCustomScan(List<Long> interfaceIds);

    /**
     * 获取可选择的接口列表（用于自定义扫描）
     * @param vulnType 漏洞类型筛选
     * @param riskLevel 风险等级筛选
     * @param targetId 目标ID筛选
     * @return 接口列表
     */
    Map<String, Object> getSelectableInterfaces(String vulnType, String riskLevel, Long targetId);
}
