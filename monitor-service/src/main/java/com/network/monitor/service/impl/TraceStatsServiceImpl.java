package com.network.monitor.service.impl;

import com.network.monitor.dto.TraceStatsDTO;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.entity.IpBlacklistEntity;
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.mapper.IpBlacklistMapper;
import com.network.monitor.service.GeoIpService;
import com.network.monitor.service.TraceStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TraceStatsServiceImpl implements TraceStatsService {

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    @Autowired
    private IpBlacklistMapper blacklistMapper;

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    @Autowired
    private GeoIpService geoIpService;

    @Override
    public TraceStatsDTO getTraceStats(LocalDateTime startTime, LocalDateTime endTime) {
        TraceStatsDTO stats = new TraceStatsDTO();
        
        stats.setOverview(calculateOverviewStats(startTime, endTime));
        stats.setAttackTypeStats(calculateAttackTypeStats(startTime, endTime));
        stats.setTrendStats(calculateTrendStats(startTime, endTime));
        stats.setRiskLevelStats(calculateRiskLevelStats(startTime, endTime));
        stats.setGeoStats(calculateGeoStats(startTime, endTime));
        stats.setTraceDepth(calculateTraceDepth());
        
        return stats;
    }

    private TraceStatsDTO.OverviewStats calculateOverviewStats(LocalDateTime startTime, LocalDateTime endTime) {
        TraceStatsDTO.OverviewStats overview = new TraceStatsDTO.OverviewStats();
        
        long totalAttacks = attackMonitorMapper.countByCondition(
            null, null, null, null, null, startTime, endTime
        );
        
        long defenseSuccessCount = defenseLogMapper.countByCondition(
            null, null, null, 1, startTime, endTime
        );
        
        List<AttackMonitorEntity> highRiskAttacks = attackMonitorMapper.selectByCondition(
            null, null, "HIGH", null, null, startTime, endTime,
            null, null, null
        );
        highRiskAttacks.addAll(attackMonitorMapper.selectByCondition(
            null, null, "CRITICAL", null, null, startTime, endTime,
            null, null, null
        ));
        
        Set<String> highRiskIps = highRiskAttacks.stream()
            .map(AttackMonitorEntity::getSourceIp)
            .collect(Collectors.toSet());
        
        List<IpBlacklistEntity> blacklist = blacklistMapper.selectAll();
        Set<String> blacklistedIps = blacklist.stream()
            .map(IpBlacklistEntity::getIpAddress)
            .collect(Collectors.toSet());
        
        double interceptRate = totalAttacks > 0 ? (defenseSuccessCount * 100.0 / totalAttacks) : 0;
        if (interceptRate > 100) interceptRate = 100;
        
        overview.setSuccessRate(Math.round(interceptRate * 100.0) / 100.0);
        overview.setAvgTraceTime(150L);
        overview.setHighRiskIpCount(highRiskIps.size());
        overview.setBlacklistedIpCount(blacklistedIps.size());
        overview.setTotalAttacks((int) totalAttacks);
        overview.setTracedAttacks((int) defenseSuccessCount);
        
        return overview;
    }

    private List<TraceStatsDTO.AttackTypeStats> calculateAttackTypeStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectByCondition(
            null, null, null, null, null, startTime, endTime,
            null, null, null
        );
        
        Map<String, Long> typeCount = attacks.stream()
            .collect(Collectors.groupingBy(
                a -> a.getAttackType() != null ? a.getAttackType() : "UNKNOWN",
                Collectors.counting()
            ));
        
        long total = attacks.size();
        
        Map<String, String> typeNames = new HashMap<>();
        typeNames.put("DDOS", "DDoS攻击");
        typeNames.put("SQL_INJECTION", "SQL注入");
        typeNames.put("XSS", "XSS攻击");
        typeNames.put("COMMAND_INJECTION", "命令注入");
        typeNames.put("PATH_TRAVERSAL", "路径遍历");
        typeNames.put("RATE_LIMIT", "限流触发");
        typeNames.put("UNKNOWN", "未知");
        
        List<TraceStatsDTO.AttackTypeStats> stats = new ArrayList<>();
        typeCount.forEach((type, count) -> {
            TraceStatsDTO.AttackTypeStats stat = new TraceStatsDTO.AttackTypeStats();
            stat.setAttackType(type);
            stat.setAttackTypeName(typeNames.getOrDefault(type, type));
            stat.setCount(count.intValue());
            stat.setPercentage(total > 0 ? Math.round(count * 10000.0 / total) / 100.0 : 0);
            stats.add(stat);
        });
        
        stats.sort((a, b) -> b.getCount().compareTo(a.getCount()));
        
        return stats;
    }

    private List<TraceStatsDTO.TrendStats> calculateTrendStats(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectByCondition(
            null, null, null, null, null, startTime, endTime,
            null, null, null
        );
        
        Map<LocalDate, List<AttackMonitorEntity>> groupedByDate = attacks.stream()
            .collect(Collectors.groupingBy(
                a -> a.getCreateTime().toLocalDate()
            ));
        
        List<TraceStatsDTO.TrendStats> trendStats = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        
        LocalDate start = startTime.toLocalDate();
        LocalDate end = endTime.toLocalDate();
        
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            TraceStatsDTO.TrendStats stat = new TraceStatsDTO.TrendStats();
            stat.setDate(date.format(formatter));
            
            List<AttackMonitorEntity> dayAttacks = groupedByDate.getOrDefault(date, new ArrayList<>());
            stat.setTotalAttacks(dayAttacks.size());
            stat.setTracedAttacks(dayAttacks.size());
            
            long highRiskCount = dayAttacks.stream()
                .filter(a -> "HIGH".equals(a.getRiskLevel()) || "CRITICAL".equals(a.getRiskLevel()))
                .count();
            stat.setHighRiskAttacks((int) highRiskCount);
            
            trendStats.add(stat);
        }
        
        return trendStats;
    }

    private List<TraceStatsDTO.RiskLevelStats> calculateRiskLevelStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectByCondition(
            null, null, null, null, null, startTime, endTime,
            null, null, null
        );
        
        Map<String, Long> levelCount = attacks.stream()
            .collect(Collectors.groupingBy(
                a -> a.getRiskLevel() != null ? a.getRiskLevel() : "UNKNOWN",
                Collectors.counting()
            ));
        
        long total = attacks.size();
        
        Map<String, String> levelNames = new HashMap<>();
        levelNames.put("CRITICAL", "严重");
        levelNames.put("HIGH", "高风险");
        levelNames.put("MEDIUM", "中风险");
        levelNames.put("LOW", "低风险");
        levelNames.put("UNKNOWN", "未知");
        
        List<TraceStatsDTO.RiskLevelStats> stats = new ArrayList<>();
        List<String> levelOrder = Arrays.asList("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN");
        
        for (String level : levelOrder) {
            Long count = levelCount.get(level);
            if (count != null && count > 0) {
                TraceStatsDTO.RiskLevelStats stat = new TraceStatsDTO.RiskLevelStats();
                stat.setRiskLevel(level);
                stat.setRiskLevelName(levelNames.get(level));
                stat.setCount(count.intValue());
                stat.setPercentage(total > 0 ? Math.round(count * 10000.0 / total) / 100.0 : 0);
                stats.add(stat);
            }
        }
        
        return stats;
    }

    private List<TraceStatsDTO.GeoStats> calculateGeoStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectByCondition(
            null, null, null, null, null, startTime, endTime,
            null, null, null
        );
        
        Map<String, Long> locationCount = new HashMap<>();
        
        for (AttackMonitorEntity attack : attacks) {
            try {
                var geoInfo = geoIpService.lookup(attack.getSourceIp());
                if (geoInfo != null && geoInfo.isValid()) {
                    String location = geoInfo.getCountry();
                    if (geoInfo.getProvince() != null && !geoInfo.getProvince().isEmpty()) {
                        location = geoInfo.getProvince();
                    }
                    locationCount.merge(location, 1L, Long::sum);
                }
            } catch (Exception e) {
                log.debug("获取地理位置失败: ip={}", attack.getSourceIp());
            }
        }
        
        long total = attacks.size();
        
        List<TraceStatsDTO.GeoStats> stats = new ArrayList<>();
        locationCount.forEach((location, count) -> {
            TraceStatsDTO.GeoStats stat = new TraceStatsDTO.GeoStats();
            stat.setLocation(location);
            stat.setCount(count.intValue());
            stat.setPercentage(total > 0 ? Math.round(count * 10000.0 / total) / 100.0 : 0);
            stats.add(stat);
        });
        
        stats.sort((a, b) -> b.getCount().compareTo(a.getCount()));
        
        if (stats.size() > 10) {
            return stats.subList(0, 10);
        }
        
        return stats;
    }

    private TraceStatsDTO.TraceDepthStats calculateTraceDepth() {
        TraceStatsDTO.TraceDepthStats depth = new TraceStatsDTO.TraceDepthStats();
        
        depth.setIpInfoCompleteness(85);
        depth.setAttackChainCompleteness(90);
        depth.setCorrelationDepth(75);
        depth.setHistoryAnalysisDepth(80);
        depth.setRiskAssessmentAccuracy(88);
        
        return depth;
    }
}
