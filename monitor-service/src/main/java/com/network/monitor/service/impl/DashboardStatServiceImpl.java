package com.network.monitor.service.impl;

import com.network.monitor.mapper.AttackMonitorMapper;
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
            // 解析时间范围和统计精度
            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime = parseTimeRange(timeRange);
            String dateFormat = parseInterval(interval);
            
            log.info("流量趋势查询：时间范围={}, 统计精度={}, 包含攻击={}, 包含防御={}", 
                timeRange, interval, includeAttacks, includeDefenses);
            
            // 查询流量数据
            List<TrafficMonitorMapper.TimeStat> trafficStats = 
                trafficMonitorMapper.countByTimeInterval(startDateTime, endDateTime, dateFormat);
            
            // 生成完整时间序列（无数据补 0）
            List<Map<String, Object>> result = generateCompleteTimeSeries(
                trafficStats, startDateTime, endDateTime, dateFormat, "traffic");
            
            // 如果需要包含攻击趋势
            if (includeAttacks) {
                List<AttackMonitorMapper.TrendStat> attackStats = 
                    attackMonitorMapper.countAttackTrend(startDateTime, endDateTime);
                mergeAttackData(result, attackStats, dateFormat);
            }
            
            // 如果需要包含防御趋势
            if (includeDefenses) {
                // TODO: 防御数据查询待实现
                log.warn("防御趋势数据查询尚未实现");
            }
            
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
     * 解析统计精度为日期格式
     */
    private String parseInterval(String interval) {
        if (interval == null || interval.isEmpty()) {
            return "%Y-%m-%d %H:00"; // 默认按小时
        }
        
        try {
            int minutes = Integer.parseInt(interval.replaceAll("[a-zA-Z]", ""));
            String unit = interval.replaceAll("[0-9]", "");
            
            if ("d".equals(unit)) {
                return "%Y-%m-%d"; // 按天
            } else if ("m".equals(unit)) {
                if (minutes >= 60) {
                    return "%Y-%m-%d %H:00"; // 按小时
                } else {
                    return "%Y-%m-%d %H:%i"; // 按分钟
                }
            }
            return "%Y-%m-%d %H:00";
        } catch (Exception e) {
            return "%Y-%m-%d %H:00";
        }
    }

    /**
     * 生成完整时间序列，无数据的时间点补 0
     */
    private List<Map<String, Object>> generateCompleteTimeSeries(
            List<TrafficMonitorMapper.TimeStat> originalStats,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String dateFormat,
            String dataType) {
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        if (originalStats == null || originalStats.isEmpty()) {
            // 完全没有数据，生成完整时间序列，值都为 0
            List<LocalDateTime> timePoints = generateTimePoints(startTime, endTime, dateFormat);
            for (LocalDateTime time : timePoints) {
                Map<String, Object> dataPoint = new HashMap<>();
                dataPoint.put("time", time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                dataPoint.put("traffic", 0L);
                result.add(dataPoint);
            }
            return result;
        }
        
        // 创建时间点到数据的映射
        Map<LocalDateTime, Long> dataMap = new HashMap<>();
        for (TrafficMonitorMapper.TimeStat stat : originalStats) {
            dataMap.put(stat.getTime(), stat.getCount());
        }
        
        // 生成完整时间序列
        List<LocalDateTime> timePoints = generateTimePoints(startTime, endTime, dateFormat);
        for (LocalDateTime time : timePoints) {
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("time", time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            dataPoint.put("traffic", dataMap.getOrDefault(time, 0L));
            result.add(dataPoint);
        }
        
        return result;
    }

    /**
     * 生成时间序列点
     */
    private List<LocalDateTime> generateTimePoints(LocalDateTime startTime, LocalDateTime endTime, String dateFormat) {
        List<LocalDateTime> timePoints = new ArrayList<>();
        LocalDateTime current = startTime;
        
        // 根据日期格式确定时间间隔
        if (dateFormat.contains("%H:%i")) {
            // 按分钟统计
            while (!current.isAfter(endTime)) {
                timePoints.add(current);
                current = current.plusMinutes(1);
            }
        } else if (dateFormat.contains("%H:00")) {
            // 按小时统计
            while (!current.isAfter(endTime)) {
                timePoints.add(current.withMinute(0).withSecond(0).withNano(0));
                current = current.plusHours(1);
            }
        } else {
            // 按天统计
            while (!current.isAfter(endTime)) {
                timePoints.add(current.withHour(0).withMinute(0).withSecond(0).withNano(0));
                current = current.plusDays(1);
            }
        }
        
        return timePoints;
    }

    /**
     * 合并攻击数据到结果中
     */
    private void mergeAttackData(List<Map<String, Object>> result, 
                                  List<AttackMonitorMapper.TrendStat> attackStats,
                                  String dateFormat) {
        // 创建攻击数据映射
        Map<LocalDateTime, Long> attackMap = new HashMap<>();
        for (AttackMonitorMapper.TrendStat stat : attackStats) {
            attackMap.put(stat.getTime(), stat.getCount());
        }
        
        // 合并到结果中
        for (Map<String, Object> dataPoint : result) {
            String timeStr = (String) dataPoint.get("time");
            LocalDateTime time = LocalDateTime.parse(timeStr.replace(" ", "T"));
            
            // 根据时间精度匹配
            LocalDateTime attackTime = attackMap.keySet().stream()
                .filter(t -> isSameTimePeriod(t, time, dateFormat))
                .findFirst()
                .orElse(null);
            
            if (attackTime != null) {
                dataPoint.put("attacks", attackMap.get(attackTime));
            } else {
                dataPoint.put("attacks", 0L);
            }
        }
    }

    /**
     * 判断两个时间是否在同一统计周期内
     */
    private boolean isSameTimePeriod(LocalDateTime t1, LocalDateTime t2, String dateFormat) {
        if (dateFormat.contains("%Y-%m-%d")) {
            // 按天统计
            return t1.toLocalDate().equals(t2.toLocalDate());
        } else if (dateFormat.contains("%H")) {
            // 按小时统计
            return t1.toLocalDate().equals(t2.toLocalDate()) && t1.getHour() == t2.getHour();
        } else {
            // 按分钟统计
            return t1.toLocalDate().equals(t2.toLocalDate()) && 
                   t1.getHour() == t2.getHour() && t1.getMinute() == t2.getMinute();
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
