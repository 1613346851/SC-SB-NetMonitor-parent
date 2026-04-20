package com.network.monitor.service;

import com.network.monitor.entity.ScanInterfaceEntity;

import java.util.List;
import java.util.Map;

/**
 * 通用扫描执行器服务接口
 */
public interface UniversalScanExecutor {

    /**
     * 执行通用扫描
     * @param entity 扫描接口实体
     * @return 扫描结果
     */
    Map<String, Object> executeScan(ScanInterfaceEntity entity);

    /**
     * 根据推断的漏洞类型执行扫描
     * @param entity 扫描接口实体
     * @param inferredVulnTypes 推断的漏洞类型列表
     * @return 扫描结果
     */
    Map<String, Object> executeScan(ScanInterfaceEntity entity, List<String> inferredVulnTypes);

    /**
     * 获取支持的漏洞类型
     * @return 支持的漏洞类型列表
     */
    List<String> getSupportedVulnTypes();
}
