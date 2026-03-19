package com.network.monitor.service;

import com.network.monitor.dto.TrafficMonitorDTO;

/**
 * 流量分析服务接口
 */
public interface TrafficAnalyzeService {

    /**
     * 预处理流量数据
     */
    void preprocessTraffic(TrafficMonitorDTO trafficDTO);

    /**
     * 解码流量内容
     */
    String decodeContent(String content);
}
