package com.network.monitor.service.impl;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.TrafficMonitorDTO;
import com.network.monitor.service.DDoSDetectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DDoSDetectServiceImpl implements DDoSDetectService {

    @Override
    public AttackMonitorDTO detect(TrafficMonitorDTO trafficDTO) {
        log.warn("DDoS检测已由网关服务负责，监测服务不再执行检测逻辑。sourceIp={}", 
            trafficDTO != null ? trafficDTO.getSourceIp() : "null");
        return null;
    }

    @Override
    public void resetCounter(String sourceIp, String timeWindow) {
        log.debug("DDoS计数器重置已废弃，由网关负责: sourceIp={}", sourceIp);
    }

    public void cleanExpiredCounters() {
        log.debug("DDoS计数器清理已废弃，由网关负责");
    }

    public int getCurrentRequestCount(String sourceIp) {
        return 0;
    }

    public int getPeakRpsForIp(String sourceIp) {
        return 0;
    }

    public void clearPeakRps(String sourceIp) {
    }
}
