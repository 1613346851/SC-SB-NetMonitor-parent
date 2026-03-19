package com.network.monitor.service;

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
}
