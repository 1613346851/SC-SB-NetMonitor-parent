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

            // 查询实际流量数据
            List<TrafficMonitorMapper.HourlyStat> stats = trafficMonitorMapper.countHourlyTraffic(startDateTime, endDateTime);
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (TrafficMonitorMapper.HourlyStat stat : stats) {
                Map<String, Object> dataPoint = new HashMap<>();
                dataPoint.put("time", stat.getTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                dataPoint.put("count", stat.getCount());
                result.add(dataPoint);
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取流量趋势失败：", e);
            return new ArrayList<>();
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
