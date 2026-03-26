package com.network.gateway.task;

import com.network.gateway.cache.IpAttackStateCache;
import com.network.gateway.cache.IpAttackStateEntry;
import com.network.gateway.cache.RequestAggregate;
import com.network.gateway.client.MonitorServiceTrafficClient;
import com.network.gateway.constant.IpAttackStateConstant;
import com.network.gateway.dto.TrafficMonitorDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PeriodPushTask {

    @Autowired
    private IpAttackStateCache ipAttackStateCache;

    @Autowired
    private MonitorServiceTrafficClient trafficClient;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Scheduled(fixedRate = 1000)
    public void checkAndPushPeriodData() {
        try {
            Map<String, IpAttackStateEntry> allEntries = ipAttackStateCache.getAllEntries();
            
            for (Map.Entry<String, IpAttackStateEntry> entry : allEntries.entrySet()) {
                String ip = entry.getKey();
                IpAttackStateEntry stateEntry = entry.getValue();
                
                if (stateEntry.getState() == IpAttackStateConstant.NORMAL) {
                    continue;
                }
                
                if (stateEntry.shouldPushByPeriod(IpAttackStateConstant.PERIOD_PUSH_INTERVAL_MS)) {
                    pushPeriodData(ip, stateEntry);
                }
            }
        } catch (Exception e) {
            log.error("周期推送检查任务异常", e);
        }
    }

    private void pushPeriodData(String ip, IpAttackStateEntry stateEntry) {
        try {
            Map<String, RequestAggregate> aggregates = stateEntry.getAndResetAggregates();
            
            if (aggregates.isEmpty()) {
                stateEntry.updateLastPushTime();
                return;
            }

            int totalRequests = aggregates.values().stream()
                .mapToInt(RequestAggregate::getCount)
                .sum();

            List<TrafficMonitorDTO> pushList = new ArrayList<>();
            
            for (RequestAggregate aggregate : aggregates.values()) {
                TrafficMonitorDTO dto = convertAggregateToDTO(aggregate, stateEntry);
                pushList.add(dto);
            }

            for (TrafficMonitorDTO dto : pushList) {
                try {
                    trafficClient.pushTraffic(dto);
                } catch (Exception e) {
                    log.error("周期推送流量数据失败: ip={}, uri={}", ip, dto.getRequestUri(), e);
                }
            }

            stateEntry.updateLastPushTime();
            
            log.info("周期推送完成: ip={}, state={}, groups={}, totalRequests={}", 
                ip, IpAttackStateConstant.getStateNameZh(stateEntry.getState()), 
                aggregates.size(), totalRequests);
                
        } catch (Exception e) {
            log.error("周期推送数据异常: ip={}", ip, e);
        }
    }

    private TrafficMonitorDTO convertAggregateToDTO(RequestAggregate aggregate, IpAttackStateEntry stateEntry) {
        TrafficMonitorDTO dto = new TrafficMonitorDTO();
        dto.setSourceIp(stateEntry.getIp());
        dto.setTargetIp("0.0.0.0");
        dto.setRequestUri(aggregate.getRequestUri());
        dto.setHttpMethod(aggregate.getHttpMethod());
        dto.setContentType(aggregate.getContentType());
        dto.setStateTag(IpAttackStateConstant.getStateName(stateEntry.getState()));
        dto.setRequestCount(aggregate.getCount());
        dto.setErrorCount(aggregate.getErrorCount());
        dto.setAvgProcessingTime((long) aggregate.getAverageProcessingTime());
        dto.setIsAggregated(true);
        dto.setAggregateStartTime(formatDateTime(aggregate.getFirstRequestTime()));
        dto.setAggregateEndTime(formatDateTime(aggregate.getLastRequestTime()));
        dto.setRequestId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        dto.setRequestTime(formatDateTime(LocalDateTime.now()));
        dto.setSuccess(aggregate.getErrorCount() == 0);
        dto.setResponseStatus(200);
        
        if (!aggregate.getSamples().isEmpty()) {
            TrafficMonitorDTO firstSample = aggregate.getSamples().get(0);
            dto.setRequestBody(firstSample.getRequestBody());
            dto.setRequestHeaders(firstSample.getRequestHeaders());
            dto.setUserAgent(firstSample.getUserAgent());
        }
        
        return dto;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
