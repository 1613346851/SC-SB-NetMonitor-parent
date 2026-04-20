package com.network.monitor.service;

import com.network.monitor.dto.TraceStatsDTO;

import java.time.LocalDateTime;

public interface TraceStatsService {

    TraceStatsDTO getTraceStats(LocalDateTime startTime, LocalDateTime endTime);
}
