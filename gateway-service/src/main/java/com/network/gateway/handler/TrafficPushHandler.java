package com.network.gateway.handler;

import com.network.gateway.dto.TrafficMonitorDTO;

public interface TrafficPushHandler {

    void handle(TrafficMonitorDTO trafficDTO);

    String getStrategyName();

    void flush();
}
