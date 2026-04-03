package com.network.monitor.service.impl;

import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.mapper.TrafficMonitorMapper;
import com.network.monitor.mapper.VulnerabilityMonitorMapper;
import com.network.monitor.service.DashboardStatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class DashboardStatServiceImpl implements DashboardStatService {

    @Autowired
    private TrafficMonitorMapper trafficMonitorMapper;

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    @Autowired
    private VulnerabilityMonitorMapper vulnerabilityMonitorMapper;

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            long totalTraffic = trafficMonitorMapper.sumRequestCountByCondition(null, null, null, null, null, null);
            stats.put("totalTraffic", totalTraffic);

            long totalAttacks = attackMonitorMapper.countByCondition(null, null, null, null, null, null, null);
            stats.put("totalAttack", totalAttacks);

            long highRiskAttacks = attackMonitorMapper.countByCondition(null, "HIGH", null, null, null, null, null);
            stats.put("highRiskAttacks", highRiskAttacks);

            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            long todayTraffic = trafficMonitorMapper.sumRequestCountByCondition(null, null, null, null, todayStart, null);
            stats.put("todayTraffic", todayTraffic);

            long todayAttacks = attackMonitorMapper.countByCondition(null, null, null, null, null, todayStart, null);
            stats.put("todayAttacks", todayAttacks);

            LocalDateTime yesterdayStart = todayStart.minusDays(1);
            long yesterdayTraffic = trafficMonitorMapper.sumRequestCountByCondition(null, null, null, null, yesterdayStart, todayStart);
            long yesterdayAttacks = attackMonitorMapper.countByCondition(null, null, null, null, null, yesterdayStart, todayStart);

            double trafficChange = calculateGrowthRate(todayTraffic, yesterdayTraffic);
            double attackChange = calculateGrowthRate(todayAttacks, yesterdayAttacks);

            stats.put("trafficChange", trafficChange);
            stats.put("attackChange", attackChange);

            long totalDefenses = defenseLogMapper.countAll();
            stats.put("totalDefense", totalDefenses);

            long todayDefenses = defenseLogMapper.countByCondition(null, null, null, null, todayStart, null);
            long yesterdayDefenses = defenseLogMapper.countByCondition(null, null, null, null, yesterdayStart, todayStart);
            double defenseChange = calculateGrowthRate(todayDefenses, yesterdayDefenses);
            stats.put("defenseChange", defenseChange);

            long totalVulnerabilities = vulnerabilityMonitorMapper.countByCondition(null, null, null);
            stats.put("totalVulnerability", totalVulnerabilities);

            long highRiskVulnerabilities = vulnerabilityMonitorMapper.countByCondition(null, "HIGH", null);
            long verifiedVulnerabilities = vulnerabilityMonitorMapper.countByCondition(null, null, 1);
            stats.put("vulnerabilityChange", verifiedVulnerabilities);

            stats.put("updateTime", LocalDateTime.now().format(FORMATTER));
        } catch (Exception e) {
            log.error("获取仪表盘统计数据失败：", e);
            stats.put("error", "获取统计数据失败");
        }

        return stats;
    }

    private double calculateGrowthRate(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round(((double) (current - previous) / previous) * 100.0 * 100.0) / 100.0;
    }

    @Override
    public List<Map<String, Object>> getTrafficTrend(String startTime, String endTime) {
        return getTrafficTrend("7d", "1h", false, false);
    }

    public List<Map<String, Object>> getTrafficTrend(String timeRange, String interval, boolean includeAttacks, boolean includeDefenses) {
        LocalDateTime endDateTime = LocalDateTime.now();
        LocalDateTime startDateTime = parseTimeRange(timeRange);
        int intervalMinutes = parseIntervalMinutes(interval);
        
        log.info("流量趋势查询：时间范围={}, 统计精度={}分钟, 开始时间={}, 结束时间={}", 
            timeRange, intervalMinutes, startDateTime, endDateTime);
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        try {
            List<TrafficMonitorMapper.TimeStat> trafficStats = 
                trafficMonitorMapper.countByTimeInterval(startDateTime, endDateTime, intervalMinutes);
            
            result = generateCompleteTimeSeries(
                trafficStats, startDateTime, endDateTime, intervalMinutes, "traffic");
        } catch (Exception e) {
            log.error("获取流量趋势失败：", e);
            result = generateEmptyTimeSeries(startDateTime, endDateTime, intervalMinutes);
        }
        
        if (includeAttacks) {
            try {
                List<AttackMonitorMapper.TrendStat> attackStats = 
                    attackMonitorMapper.countAttackTrend(startDateTime, endDateTime);
                mergeAttackData(result, attackStats, intervalMinutes);
            } catch (Exception e) {
                log.error("获取攻击趋势失败：", e);
                for (Map<String, Object> dataPoint : result) {
                    dataPoint.put("attacks", 0L);
                }
            }
        }
        
        if (includeDefenses) {
            try {
                List<DefenseLogMapper.TrendStat> defenseStats = 
                    defenseLogMapper.countDefenseTrend(startDateTime, endDateTime);
                mergeDefenseData(result, defenseStats, intervalMinutes);
            } catch (Exception e) {
                log.error("获取防御趋势失败：", e);
                for (Map<String, Object> dataPoint : result) {
                    dataPoint.put("defenses", 0L);
                }
            }
        }
        
        log.info("流量趋势查询结果：共{}个数据点", result.size());
        return result;
    }

    private List<Map<String, Object>> generateEmptyTimeSeries(LocalDateTime startTime, LocalDateTime endTime, int intervalMinutes) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<LocalDateTime> timePoints = generateTimePoints(startTime, endTime, intervalMinutes);
        
        for (LocalDateTime time : timePoints) {
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("time", formatTimeForDisplay(time, intervalMinutes));
            dataPoint.put("traffic", 0L);
            dataPoint.put("attacks", 0L);
            dataPoint.put("defenses", 0L);
            result.add(dataPoint);
        }
        
        return result;
    }

    private LocalDateTime parseTimeRange(String timeRange) {
        if (timeRange == null || timeRange.isEmpty()) {
            return LocalDateTime.now().minusDays(7);
        }
        
        try {
            int amount = Integer.parseInt(timeRange.replaceAll("[a-zA-Z]", ""));
            String unit = timeRange.replaceAll("[0-9]", "");
            
            switch (unit) {
                case "h": return LocalDateTime.now().minusHours(amount);
                case "d": return LocalDateTime.now().minusDays(amount);
                default: return LocalDateTime.now().minusDays(7);
            }
        } catch (Exception e) {
            return LocalDateTime.now().minusDays(7);
        }
    }

    private int parseIntervalMinutes(String interval) {
        if (interval == null || interval.isEmpty()) {
            return 60;
        }
        
        try {
            int amount = Integer.parseInt(interval.replaceAll("[a-zA-Z]", ""));
            String unit = interval.replaceAll("[0-9]", "");
            
            switch (unit) {
                case "m": return amount;
                case "h": return amount * 60;
                case "d": return amount * 24 * 60;
                default: return 60;
            }
        } catch (Exception e) {
            log.warn("解析统计精度失败：{}，使用默认值60分钟", interval);
            return 60;
        }
    }

    private List<Map<String, Object>> generateCompleteTimeSeries(
            List<TrafficMonitorMapper.TimeStat> originalStats,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int intervalMinutes,
            String dataType) {
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        List<LocalDateTime> timePoints = generateTimePoints(startTime, endTime, intervalMinutes);
        
        if (originalStats == null || originalStats.isEmpty()) {
            for (LocalDateTime time : timePoints) {
                Map<String, Object> dataPoint = new HashMap<>();
                dataPoint.put("time", formatTimeForDisplay(time, intervalMinutes));
                dataPoint.put("traffic", 0L);
                result.add(dataPoint);
            }
            return result;
        }
        
        Map<String, Long> dataMap = new HashMap<>();
        for (TrafficMonitorMapper.TimeStat stat : originalStats) {
            if (stat.getTime() != null && stat.getCount() != null) {
                dataMap.put(stat.getTime(), stat.getCount());
            }
        }
        
        for (LocalDateTime time : timePoints) {
            Map<String, Object> dataPoint = new HashMap<>();
            String timeKey = formatTimeForQuery(time, intervalMinutes);
            dataPoint.put("time", formatTimeForDisplay(time, intervalMinutes));
            dataPoint.put("traffic", dataMap.getOrDefault(timeKey, 0L));
            result.add(dataPoint);
        }
        
        return result;
    }

    private List<LocalDateTime> generateTimePoints(LocalDateTime startTime, LocalDateTime endTime, int intervalMinutes) {
        List<LocalDateTime> timePoints = new ArrayList<>();
        
        LocalDateTime alignedStart = alignToInterval(startTime, intervalMinutes);
        
        LocalDateTime current = alignedStart;
        while (!current.isAfter(endTime)) {
            timePoints.add(current);
            current = current.plusMinutes(intervalMinutes);
        }
        
        return timePoints;
    }

    private LocalDateTime alignToInterval(LocalDateTime time, int intervalMinutes) {
        if (intervalMinutes >= 1440) {
            return time.withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else if (intervalMinutes >= 60) {
            int hours = intervalMinutes / 60;
            int alignedHour = (time.getHour() / hours) * hours;
            return time.withHour(alignedHour).withMinute(0).withSecond(0).withNano(0);
        } else {
            int alignedMinute = (time.getMinute() / intervalMinutes) * intervalMinutes;
            return time.withMinute(alignedMinute).withSecond(0).withNano(0);
        }
    }

    private String formatTimeForDisplay(LocalDateTime time, int intervalMinutes) {
        if (intervalMinutes >= 1440) {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
    }

    private String formatTimeForQuery(LocalDateTime time, int intervalMinutes) {
        if (intervalMinutes >= 1440) {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
    }

    private void mergeAttackData(List<Map<String, Object>> result, 
                                  List<AttackMonitorMapper.TrendStat> attackStats,
                                  int intervalMinutes) {
        Map<String, Long> attackMap = new HashMap<>();
        for (AttackMonitorMapper.TrendStat stat : attackStats) {
            if (stat.getTime() != null && stat.getCount() != null) {
                LocalDateTime alignedTime = alignToInterval(stat.getTime(), intervalMinutes);
                String timeKey = formatTimeForQuery(alignedTime, intervalMinutes);
                attackMap.merge(timeKey, stat.getCount(), (a, b) -> a + b);
            }
        }
        
        for (Map<String, Object> dataPoint : result) {
            String timeStr = (String) dataPoint.get("time");
            dataPoint.put("attacks", attackMap.getOrDefault(timeStr, 0L));
        }
    }

    private void mergeDefenseData(List<Map<String, Object>> result, 
                                   List<DefenseLogMapper.TrendStat> defenseStats,
                                   int intervalMinutes) {
        Map<String, Long> defenseMap = new HashMap<>();
        for (DefenseLogMapper.TrendStat stat : defenseStats) {
            if (stat.getTime() != null && stat.getCount() != null) {
                LocalDateTime alignedTime = alignToInterval(stat.getTime(), intervalMinutes);
                String timeKey = formatTimeForQuery(alignedTime, intervalMinutes);
                defenseMap.merge(timeKey, stat.getCount(), (a, b) -> a + b);
            }
        }
        
        for (Map<String, Object> dataPoint : result) {
            String timeStr = (String) dataPoint.get("time");
            dataPoint.put("defenses", defenseMap.getOrDefault(timeStr, 0L));
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getAttackTypeDistribution() {
        try {
            List<AttackMonitorMapper.AttackTypeStat> stats = attackMonitorMapper.countByAttackType();
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (AttackMonitorMapper.AttackTypeStat stat : stats) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", stat.getAttackType());
                item.put("value", stat.getCount());
                result.add(item);
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取攻击类型分布失败：", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> getRiskLevelDistribution() {
        try {
            List<AttackMonitorMapper.RiskLevelStat> stats = attackMonitorMapper.countByRiskLevel();
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (AttackMonitorMapper.RiskLevelStat stat : stats) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", stat.getRiskLevel());
                item.put("value", stat.getCount());
                result.add(item);
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取风险等级分布失败：", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> getTopSourceIps() {
        try {
            List<AttackMonitorMapper.SourceIpStat> stats = attackMonitorMapper.countTopSourceIps(10);
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (AttackMonitorMapper.SourceIpStat stat : stats) {
                Map<String, Object> item = new HashMap<>();
                item.put("sourceIp", stat.getSourceIp());
                item.put("attackCount", stat.getCount());
                item.put("riskLevel", "HIGH");
                result.add(item);
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取攻击源 IP 统计失败：", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> getTopTargetUris() {
        try {
            List<AttackMonitorMapper.TargetUriStat> stats = attackMonitorMapper.countTopTargetUris(10);
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (AttackMonitorMapper.TargetUriStat stat : stats) {
                Map<String, Object> item = new HashMap<>();
                item.put("uri", stat.getTargetUri());
                item.put("count", stat.getCount());
                result.add(item);
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取目标 URI 统计失败：", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> getAttackTrend(String startTime, String endTime) {
        try {
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);

            if (startDateTime == null) {
                startDateTime = LocalDateTime.now().minusHours(24);
            }
            if (endDateTime == null) {
                endDateTime = LocalDateTime.now();
            }

            List<AttackMonitorMapper.TrendStat> stats = attackMonitorMapper.countAttackTrend(startDateTime, endDateTime);
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (AttackMonitorMapper.TrendStat stat : stats) {
                Map<String, Object> dataPoint = new HashMap<>();
                dataPoint.put("time", stat.getTime().format(FORMATTER));
                dataPoint.put("count", stat.getCount());
                result.add(dataPoint);
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取攻击趋势失败：", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> getVulnerabilityLevelDistribution() {
        try {
            List<VulnerabilityMonitorMapper.VulnLevelStat> stats = vulnerabilityMonitorMapper.countByVulnLevel();
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (VulnerabilityMonitorMapper.VulnLevelStat stat : stats) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", stat.getVulnLevel());
                item.put("value", stat.getCount());
                result.add(item);
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取漏洞等级分布失败：", e);
            return new ArrayList<>();
        }
    }

    @Override
    public long getTotalTraffic(String startTime, String endTime) {
        try {
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);
            return trafficMonitorMapper.sumRequestCountByCondition(null, null, null, null, startDateTime, endDateTime);
        } catch (Exception e) {
            log.error("获取总流量数失败：", e);
            return 0;
        }
    }

    @Override
    public long getTotalAttacks(String startTime, String endTime) {
        try {
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);
            return attackMonitorMapper.countByCondition(null, null, null, null, null, startDateTime, endDateTime);
        } catch (Exception e) {
            log.error("获取总攻击次数失败：", e);
            return 0;
        }
    }

    @Override
    public long getTotalVulnerabilities(String startTime, String endTime) {
        try {
            return vulnerabilityMonitorMapper.countByCondition(null, null, null);
        } catch (Exception e) {
            log.error("获取总漏洞数失败：", e);
            return 0;
        }
    }

    @Override
    public long getTotalDefenses(String startTime, String endTime) {
        try {
            return defenseLogMapper.countAll();
        } catch (Exception e) {
            log.error("获取总防御次数失败：", e);
            return 0;
        }
    }
}
