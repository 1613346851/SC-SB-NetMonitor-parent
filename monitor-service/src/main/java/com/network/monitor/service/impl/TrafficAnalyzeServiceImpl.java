package com.network.monitor.service.impl;

import com.network.monitor.common.util.AttackContentDecodeUtil;
import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.service.TrafficAnalyzeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 流量分析服务实现类
 */
@Slf4j
@Service
public class TrafficAnalyzeServiceImpl implements TrafficAnalyzeService {

    @Override
    public void preprocessTraffic(TrafficMonitorDTO trafficDTO) {
        if (trafficDTO == null) {
            return;
        }

        // 对请求 URI、查询参数、请求体进行解码处理
        if (trafficDTO.getRequestUri() != null) {
            trafficDTO.setRequestUri(decodeContent(trafficDTO.getRequestUri()));
        }

        if (trafficDTO.getQueryParams() != null) {
            // Map 类型不需要解码，保持原样
            // 如果需要解码 Map 中的值，可以在这里处理
        }

        if (trafficDTO.getRequestBody() != null) {
            trafficDTO.setRequestBody(decodeContent(trafficDTO.getRequestBody()));
        }

        log.debug("流量预处理完成：sourceIp={}, uri={}", 
            trafficDTO.getSourceIp(), trafficDTO.getRequestUri());
    }

    @Override
    public String decodeContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        return AttackContentDecodeUtil.decode(content);
    }
}
