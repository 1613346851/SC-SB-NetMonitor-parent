package com.network.monitor.service.impl;

import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.mapper.DefenseMonitorMapper;
import com.network.monitor.mapper.TrafficMonitorMapper;
import com.network.monitor.mapper.VulnerabilityMonitorMapper;
import com.network.monitor.service.DashboardStatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 仪表盘统计数据服务实现类
 */
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
    private DefenseMonitorMapper defenseMonitorMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 总流量数
            long totalTraffic = trafficMonitorMapper.countByCondition(null, null, null, null);
            stats.put("totalTraffic", totalTraffic);

            // 总攻击次数
            long totalAttacks = attackMonitorMapper.countByCondition(null, null, null, null, null, null);
            stats.put("totalAttacks", totalAttacks);

            // 高危攻击数
            long highRiskAttacks = attackMonitorMapper.countByCondition(null, "HIGH", null, null, null, null);
            stats.put("highRiskAttacks", highRiskAttacks);

            // 今日流量数
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            long todayTraffic = trafficMonitorMapper.countByCondition(null, null, todayStart, null);
            stats.put("todayTraffic", todayTraffic);

            // 今日攻击数
            long todayAttacks = attackMonitorMapper.countByCondition(null, null, null, null, todayStart, null);
            stats.put("todayAttacks", todayAttacks);

            // 计算昨日数据用于对比
            LocalDateTime yesterdayStart = todayStart.minusDays(1);
            long yesterdayTraffic = trafficMonitorMapper.countByCondition(null, null, yesterdayStart, todayStart);
            long yesterdayAttacks = attackMonitorMapper.countByCondition(null, null, null, null, yesterdayStart, todayStart);

            // 计算增长率
            double trafficChange = calculateGrowthRate(todayTraffic, yesterdayTraffic);
            double attackChange = calculateGrowthRate(todayAttacks, yesterdayAttacks);

            stats.put("trafficChange", trafficChange);
            stats.put("attackChange", attackChange);

            stats.put("updateTime", LocalDateTime.now().format(FORMATTER));
        } catch (Exception e) {
            log.error("获取仪表盘统计数据失败：", e);
            stats.put("error", "获取统计数据失败");
        }

        return stats;
    }

    /**
     * 计算增长率
     */
    private double calculateGrowthRate(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round(((double) (current - previous) / previous) * 100.0 * 100.0) / 100.0;
    }

    @Override
    public List<Map<String, Object>> getTrafficTrend(String startTime, String endTime) {
        // 兼容旧接口，调用新方法
        return getTrafficTrend("7d", "1h", false, false);
    }

    public List<Map<String, Object>> getTrafficTrend(String timeRange, String interval, boolean includeAttacks, boolean includeDefenses) {
        try {
            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime = parseTimeRange(timeRange);
            int intervalMinutes = parseIntervalMinutes(interval);
            
            log.info("流量趋势查询：时间范围={}, 统计精度={}分钟, 开始时间={}, 结束时间={}", 
                timeRange, intervalMinutes, startDateTime, endDateTime);
            
            List<TrafficMonitorMapper.TimeStat> trafficStats = 
                trafficMonitorMapper.countByTimeInterval(startDateTime, endDateTime, intervalMinutes);
            
            List<Map<String, Object>> result = generateCompleteTimeSeries(
                trafficStats, startDateTime, endDateTime, intervalMinutes, "traffic");
            
            if (includeAttacks) {
                List<AttackMonitorMapper.TrendStat> attackStats = 
                    attackMonitorMapper.countAttackTrend(startDateTime, endDateTime);
                mergeAttackData(result, attackStats, intervalMinutes);
            }
            
            if (includeDefenses) {
                List<DefenseMonitorMapper.TrendStat> defenseStats = 
                    defenseMonitorMapper.countDefenseTrend(startDateTime, endDateTime);
                mergeDefenseData(result, defenseStats, intervalMinutes);
            }
            
            log.info("流量趋势查询结果：共{}个数据点", result.size());
            return result;
        } catch (Exception e) {
            log.error("获取流量趋势失败：", e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析时间范围字符串
     */
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

    /**
     * 解析统计精度为分钟数
     * @param interval 间隔字符串，如 "5m", "30m", "1h", "1d"
     * @return 分钟数
     */
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

    /**
     * 生成完整时间序列，无数据的时间点补 0
     */
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

    /**
     * 生成时间序列点
     */
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

    /**
     * 将时间对齐到指定的间隔边界
     */
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

    /**
     * 格式化时间用于显示
     */
    private String formatTimeForDisplay(LocalDateTime time, int intervalMinutes) {
        if (intervalMinutes >= 1440) {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
    }

    /**
     * 格式化时间用于查询匹配
     */
    private String formatTimeForQuery(LocalDateTime time, int intervalMinutes) {
        if (intervalMinutes >= 1440) {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
    }

    /**
     * 合并攻击数据到结果中
     */
    private void mergeAttackData(List<Map<String, Object>> result, 
                                  List<AttackMonitorMapper.TrendStat> attackStats,
                                  int intervalMinutes) {
        Map<String, Long> attackMap = new HashMap<>();
        for (AttackMonitorMapper.TrendStat stat : attackStats) {
            if (stat.getTime() != null && stat.getCount() != null) {
                String timeKey = formatTimeForQuery(stat.getTime(), intervalMinutes);
                attackMap.merge(timeKey, stat.getCount(), Long::sum);
            }
        }
        
        for (Map<String, Object> dataPoint : result) {
            String timeStr = (String) dataPoint.get("time");
            dataPoint.put("attacks", attackMap.getOrDefault(timeStr, 0L));
        }
    }

    /**
     * 合并防御数据到结果中
     */
    private void mergeDefenseData(List<Map<String, Object>> result, 
                                   List<DefenseMonitorMapper.TrendStat> defenseStats,
                                   int intervalMinutes) {
        Map<String, Long> defenseMap = new HashMap<>();
        for (DefenseMonitorMapper.TrendStat stat : defenseStats) {
            if (stat.getTime() != null && stat.getCount() != null) {
                String timeKey = formatTimeForQuery(stat.getTime(), intervalMinutes);
                defenseMap.merge(timeKey, stat.getCount(), Long::sum);
            }
        }
        
        for (Map<String, Object> dataPoint : result) {
            String timeStr = (String) dataPoint.get("time");
            dataPoint.put("defenses", defenseMap.getOrDefault(timeStr, 0L));
        }
    }

    /**
     * 解析日期时间字符串
     */
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
                item.put("ip", stat.getSourceIp());
                item.put("count", stat.getCount());
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
            // 解析时间参数
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);

            // 默认查询最近 24 小时
            if (startDateTime == null) {
                startDateTime = LocalDateTime.now().minusHours(24);
            }
            if (endDateTime == null) {
                endDateTime = LocalDateTime.now();
            }

            // 查询攻击趋势数据
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
            return trafficMonitorMapper.countByCondition(null, null, startDateTime, endDateTime);
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
            return attackMonitorMapper.countByCondition(null, null, null, null, startDateTime, endDateTime);
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
            // 防御日志统计（假设有 defenseLogMapper）
            // 暂时返回 0，后续可根据实际情况实现
            return 0;
        } catch (Exception e) {
            log.error("获取总防御次数失败：", e);
            return 0;
        }
    }
}
