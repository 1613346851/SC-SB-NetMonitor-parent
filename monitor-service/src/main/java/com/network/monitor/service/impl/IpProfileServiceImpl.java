package com.network.monitor.service.impl;

import com.network.monitor.dto.AttackChainDTO;
import com.network.monitor.dto.GeoIpDTO;
import com.network.monitor.dto.IpProfileDTO;
import com.network.monitor.entity.AttackEventEntity;
import com.network.monitor.entity.AttackMonitorEntity;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.entity.IpBlacklistEntity;
import com.network.monitor.entity.TrafficMonitorEntity;
import com.network.monitor.mapper.AttackEventMapper;
import com.network.monitor.mapper.AttackMonitorMapper;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.mapper.IpBlacklistMapper;
import com.network.monitor.mapper.TrafficMonitorMapper;
import com.network.monitor.service.GeoIpService;
import com.network.monitor.service.IpProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IpProfileServiceImpl implements IpProfileService {

    @Autowired
    private GeoIpService geoIpService;

    @Autowired
    private AttackMonitorMapper attackMonitorMapper;

    @Autowired
    private AttackEventMapper attackEventMapper;

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    @Autowired
    private TrafficMonitorMapper trafficMonitorMapper;

    @Autowired
    private IpBlacklistMapper ipBlacklistMapper;

    @Override
    public IpProfileDTO getIpProfile(String ip) {
        GeoIpDTO geoInfo = geoIpService.lookup(ip);

        Long totalAttackCount = countAttacksByIp(ip);
        Long totalBlockCount = countBlocksByIp(ip);
        Long totalRequestCount = countRequestsByIp(ip);

        LocalDateTime firstSeen = findFirstSeen(ip);
        LocalDateTime lastSeen = findLastSeen(ip);

        Integer riskScore = calculateRiskScore(ip);
        String riskLevel = getRiskLevel(riskScore);

        IpBlacklistEntity blacklist = findActiveBlacklist(ip);

        List<IpProfileDTO.AttackTypeStats> attackTypeStats = getAttackTypeStats(ip);
        List<IpProfileDTO.HourlyStats> hourlyStats = getHourlyStats(ip);
        List<IpProfileDTO.DailyStats> dailyStats = getDailyStats(ip, 7);

        AttackMonitorEntity lastAttack = findLastAttack(ip);

        return IpProfileDTO.builder()
                .ip(ip)
                .geoInfo(geoInfo)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .currentStatus(getCurrentStatus(ip))
                .currentState(getCurrentState(ip))
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .totalAttackCount(totalAttackCount)
                .totalBlockCount(totalBlockCount)
                .totalRequestCount(totalRequestCount)
                .totalTrafficBytes(0L)
                .attackTypeStats(attackTypeStats)
                .hourlyStats(hourlyStats)
                .dailyStats(dailyStats)
                .lastAttackTime(lastAttack != null ? lastAttack.getCreateTime() : null)
                .lastAttackType(lastAttack != null ? lastAttack.getAttackType() : null)
                .isBlacklisted(blacklist != null)
                .blacklistedAt(blacklist != null ? blacklist.getCreateTime() : null)
                .blacklistExpireTime(blacklist != null ? blacklist.getCurrentExpireTime() : null)
                .blacklistReason(blacklist != null ? blacklist.getIpLocation() : null)
                .build();
    }

    @Override
    public Integer calculateRiskScore(String ip) {
        int score = 0;

        Long attackCount = countAttacksByIp(ip);
        if (attackCount > 100) {
            score += 40;
        } else if (attackCount > 50) {
            score += 30;
        } else if (attackCount > 20) {
            score += 20;
        } else if (attackCount > 10) {
            score += 10;
        } else if (attackCount > 0) {
            score += 5;
        }

        Long blockCount = countBlocksByIp(ip);
        if (blockCount > 10) {
            score += 30;
        } else if (blockCount > 5) {
            score += 20;
        } else if (blockCount > 0) {
            score += 10;
        }

        IpBlacklistEntity blacklist = findActiveBlacklist(ip);
        if (blacklist != null) {
            score += 20;
        }

        AttackMonitorEntity lastAttack = findLastAttack(ip);
        if (lastAttack != null) {
            LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
            if (lastAttack.getCreateTime().isAfter(oneDayAgo)) {
                score += 10;
            }
        }

        return Math.min(score, 100);
    }

    @Override
    public String getRiskLevel(Integer score) {
        if (score == null) {
            return "LOW";
        }
        if (score >= 80) {
            return "CRITICAL";
        } else if (score >= 60) {
            return "HIGH";
        } else if (score >= 40) {
            return "MEDIUM";
        } else if (score >= 20) {
            return "LOW";
        } else {
            return "MINIMAL";
        }
    }

    @Override
    public AttackChainDTO getAttackChain(String ip, LocalDateTime startTime, LocalDateTime endTime) {
        List<AttackChainDTO.TimelineEvent> timeline = new ArrayList<>();

        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectBySourceIp(
                ip, startTime, endTime, 0, 100, "create_time DESC"
        );

        for (AttackMonitorEntity attack : attacks) {
            timeline.add(AttackChainDTO.TimelineEvent.builder()
                    .time(attack.getCreateTime())
                    .eventType("ATTACK")
                    .eventTypeName("攻击事件")
                    .title(getAttackTitle(attack))
                    .description(buildAttackDescription(attack))
                    .severity(attack.getRiskLevel())
                    .attackType(attack.getAttackType())
                    .eventId(attack.getId())
                    .build());
        }

        List<DefenseLogEntity> defenses = defenseLogMapper.selectByDefenseTarget(
                ip, startTime, endTime, 0, 100, "create_time", "DESC"
        );

        for (DefenseLogEntity defense : defenses) {
            timeline.add(AttackChainDTO.TimelineEvent.builder()
                    .time(defense.getCreateTime())
                    .eventType("DEFENSE")
                    .eventTypeName("防御动作")
                    .title(getDefenseTitle(defense))
                    .description(buildDefenseDescription(defense))
                    .severity("INFO")
                    .defenseAction(defense.getDefenseType())
                    .eventId(defense.getId())
                    .build());
        }

        timeline.sort(Comparator.comparing(AttackChainDTO.TimelineEvent::getTime).reversed());

        AttackChainDTO.AttackSummary summary = buildSummary(attacks, defenses, startTime, endTime);

        return AttackChainDTO.builder()
                .ip(ip)
                .startTime(startTime)
                .endTime(endTime)
                .totalEvents(timeline.size())
                .timeline(timeline)
                .summary(summary)
                .build();
    }

    @Override
    public AttackChainDTO getRecentAttackChain(String ip, int limit) {
        List<AttackChainDTO.TimelineEvent> timeline = new ArrayList<>();

        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectBySourceIp(
                ip, null, null, 0, limit, "create_time DESC"
        );

        for (AttackMonitorEntity attack : attacks) {
            timeline.add(AttackChainDTO.TimelineEvent.builder()
                    .time(attack.getCreateTime())
                    .eventType("ATTACK")
                    .eventTypeName("攻击事件")
                    .title(getAttackTitle(attack))
                    .description(buildAttackDescription(attack))
                    .severity(attack.getRiskLevel())
                    .attackType(attack.getAttackType())
                    .eventId(attack.getId())
                    .build());
        }

        List<DefenseLogEntity> defenses = defenseLogMapper.selectByDefenseTarget(
                ip, null, null, 0, limit, "create_time", "DESC"
        );

        for (DefenseLogEntity defense : defenses) {
            timeline.add(AttackChainDTO.TimelineEvent.builder()
                    .time(defense.getCreateTime())
                    .eventType("DEFENSE")
                    .eventTypeName("防御动作")
                    .title(getDefenseTitle(defense))
                    .description(buildDefenseDescription(defense))
                    .severity("INFO")
                    .defenseAction(defense.getDefenseType())
                    .eventId(defense.getId())
                    .build());
        }

        timeline.sort(Comparator.comparing(AttackChainDTO.TimelineEvent::getTime).reversed());

        List<AttackChainDTO.TimelineEvent> limitedTimeline = timeline.size() > limit 
                ? timeline.subList(0, limit) 
                : timeline;

        return AttackChainDTO.builder()
                .ip(ip)
                .totalEvents(limitedTimeline.size())
                .timeline(limitedTimeline)
                .build();
    }

    private Long countAttacksByIp(String ip) {
        return attackMonitorMapper.countBySourceIp(ip, null, null);
    }

    private Long countBlocksByIp(String ip) {
        Long blockCount = defenseLogMapper.countBlocksByIp(ip);
        return blockCount != null ? blockCount : 0;
    }

    private Long countRequestsByIp(String ip) {
        return trafficMonitorMapper.sumRequestCountByCondition(ip, null, null, null, null, null);
    }

    private LocalDateTime findFirstSeen(String ip) {
        LocalDateTime firstAttack = null;
        LocalDateTime firstTraffic = null;

        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectBySourceIp(
                ip, null, null, 0, 1, "create_time ASC"
        );
        if (!attacks.isEmpty()) {
            firstAttack = attacks.get(0).getCreateTime();
        }

        List<TrafficMonitorEntity> traffic = trafficMonitorMapper.selectByCondition(
                ip, null, null, null, null, null, null, null, null, 0, 1, "create_time ASC"
        );
        if (!traffic.isEmpty()) {
            firstTraffic = traffic.get(0).getCreateTime();
        }

        if (firstAttack == null) {
            return firstTraffic;
        }
        if (firstTraffic == null) {
            return firstAttack;
        }
        return firstAttack.isBefore(firstTraffic) ? firstAttack : firstTraffic;
    }

    private LocalDateTime findLastSeen(String ip) {
        LocalDateTime lastAttack = null;
        LocalDateTime lastTraffic = null;

        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectBySourceIp(
                ip, null, null, 0, 1, "create_time DESC"
        );
        if (!attacks.isEmpty()) {
            lastAttack = attacks.get(0).getCreateTime();
        }

        List<TrafficMonitorEntity> traffic = trafficMonitorMapper.selectByCondition(
                ip, null, null, null, null, null, null, null, null, 0, 1, "create_time DESC"
        );
        if (!traffic.isEmpty()) {
            lastTraffic = traffic.get(0).getCreateTime();
        }

        if (lastAttack == null) {
            return lastTraffic;
        }
        if (lastTraffic == null) {
            return lastAttack;
        }
        return lastAttack.isAfter(lastTraffic) ? lastAttack : lastTraffic;
    }

    private IpBlacklistEntity findActiveBlacklist(String ip) {
        List<IpBlacklistEntity> blacklists = ipBlacklistMapper.selectActiveByIp(ip);
        return blacklists.isEmpty() ? null : blacklists.get(0);
    }

    private List<IpProfileDTO.AttackTypeStats> getAttackTypeStats(String ip) {
        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectBySourceIp(
                ip, null, null, 0, 1000, null
        );

        Map<String, Long> typeCount = attacks.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getAttackType() != null ? a.getAttackType() : "UNKNOWN",
                        Collectors.counting()
                ));

        long total = attacks.size();
        if (total == 0) {
            return new ArrayList<>();
        }

        return typeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> IpProfileDTO.AttackTypeStats.builder()
                        .attackType(e.getKey())
                        .attackTypeName(getAttackTypeName(e.getKey()))
                        .count(e.getValue())
                        .percentage((double) e.getValue() / total * 100)
                        .build())
                .collect(Collectors.toList());
    }

    private List<IpProfileDTO.HourlyStats> getHourlyStats(String ip) {
        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectBySourceIp(
                ip, null, null, 0, 10000, null
        );

        Map<Integer, Long> hourCount = attacks.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCreateTime().getHour(),
                        Collectors.counting()
                ));

        List<IpProfileDTO.HourlyStats> result = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            result.add(IpProfileDTO.HourlyStats.builder()
                    .hour(i)
                    .count(hourCount.getOrDefault(i, 0L))
                    .build());
        }
        return result;
    }

    private List<IpProfileDTO.DailyStats> getDailyStats(String ip, int days) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);

        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectBySourceIp(
                ip, startTime, endTime, 0, 10000, null
        );

        List<TrafficMonitorEntity> traffic = trafficMonitorMapper.selectByCondition(
                ip, null, null, null, null, null, null, startTime, endTime, 0, 10000, null
        );

        Map<String, Long> attackByDate = attacks.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCreateTime().toLocalDate().toString(),
                        Collectors.counting()
                ));

        Map<String, Long> trafficByDate = traffic.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCreateTime().toLocalDate().toString(),
                        Collectors.counting()
                ));

        List<IpProfileDTO.DailyStats> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime date = endTime.minusDays(i);
            String dateStr = date.toLocalDate().toString();
            result.add(IpProfileDTO.DailyStats.builder()
                    .date(dateStr)
                    .attackCount(attackByDate.getOrDefault(dateStr, 0L))
                    .requestCount(trafficByDate.getOrDefault(dateStr, 0L))
                    .build());
        }
        return result;
    }

    private AttackMonitorEntity findLastAttack(String ip) {
        List<AttackMonitorEntity> attacks = attackMonitorMapper.selectBySourceIp(
                ip, null, null, 0, 1, "create_time DESC"
        );
        return attacks.isEmpty() ? null : attacks.get(0);
    }

    private String getCurrentStatus(String ip) {
        IpBlacklistEntity blacklist = findActiveBlacklist(ip);
        if (blacklist != null) {
            return "已封禁";
        }

        AttackEventEntity ongoingEvent = attackEventMapper.selectOngoingEventByIp(ip);
        if (ongoingEvent != null) {
            return "攻击中";
        }

        AttackMonitorEntity lastAttack = findLastAttack(ip);
        if (lastAttack != null) {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            if (lastAttack.getCreateTime().isAfter(oneHourAgo)) {
                return "活跃";
            }
            LocalDateTime sixHoursAgo = LocalDateTime.now().minusHours(6);
            if (lastAttack.getCreateTime().isAfter(sixHoursAgo)) {
                return "冷却中";
            }
        }

        return "正常";
    }

    private Integer getCurrentState(String ip) {
        IpBlacklistEntity blacklist = findActiveBlacklist(ip);
        if (blacklist != null) {
            return 3;
        }

        AttackEventEntity ongoingEvent = attackEventMapper.selectOngoingEventByIp(ip);
        if (ongoingEvent != null) {
            return 2;
        }

        AttackMonitorEntity lastAttack = findLastAttack(ip);
        if (lastAttack != null) {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            if (lastAttack.getCreateTime().isAfter(oneHourAgo)) {
                return 2;
            }
            LocalDateTime sixHoursAgo = LocalDateTime.now().minusHours(6);
            if (lastAttack.getCreateTime().isAfter(sixHoursAgo)) {
                return 4;
            }
        }

        return 0;
    }

    private String getAttackTitle(AttackMonitorEntity attack) {
        return String.format("%s攻击 - %s",
                getAttackTypeName(attack.getAttackType()),
                attack.getTargetUri() != null ? attack.getTargetUri() : "未知目标");
    }

    private String buildAttackDescription(AttackMonitorEntity attack) {
        return String.format("风险等级: %s, 来源IP: %s, 目标: %s",
                getRiskLevelName(attack.getRiskLevel()),
                attack.getSourceIp(),
                attack.getTargetUri() != null ? attack.getTargetUri() : "未知");
    }

    private String getDefenseTitle(DefenseLogEntity defense) {
        return String.format("防御动作 - %s", getDefenseTypeName(defense.getDefenseType()));
    }

    private String buildDefenseDescription(DefenseLogEntity defense) {
        return String.format("类型: %s, 目标: %s, 状态: %s",
                getDefenseTypeName(defense.getDefenseType()),
                defense.getDefenseTarget(),
                defense.getExecuteStatus() == 1 ? "成功" : "失败");
    }

    private AttackChainDTO.AttackSummary buildSummary(
            List<AttackMonitorEntity> attacks,
            List<DefenseLogEntity> defenses,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        Map<String, Long> attackTypeCount = attacks.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getAttackType() != null ? a.getAttackType() : "UNKNOWN",
                        Collectors.counting()
                ));

        String mostFrequentAttackType = attackTypeCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        Map<String, Long> targetCount = attacks.stream()
                .filter(a -> a.getTargetUri() != null)
                .collect(Collectors.groupingBy(
                        AttackMonitorEntity::getTargetUri,
                        Collectors.counting()
                ));

        String mostFrequentTarget = targetCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        int successfulDefenses = (int) defenses.stream()
                .filter(d -> d.getExecuteStatus() != null && d.getExecuteStatus() == 1)
                .count();

        long durationSeconds = 0;
        if (startTime != null && endTime != null) {
            durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
        }

        return AttackChainDTO.AttackSummary.builder()
                .totalAttacks(attacks.size())
                .totalDefenses(defenses.size())
                .successfulDefenses(successfulDefenses)
                .blockedRequests(0)
                .mostFrequentAttackType(mostFrequentAttackType)
                .mostFrequentTarget(mostFrequentTarget)
                .totalDurationSeconds(durationSeconds)
                .build();
    }

    private String getAttackTypeName(String attackType) {
        if (attackType == null) {
            return "未知";
        }
        switch (attackType) {
            case "DDOS": return "DDoS攻击";
            case "SQL_INJECTION": return "SQL注入";
            case "XSS": return "XSS攻击";
            case "PATH_TRAVERSAL": return "路径遍历";
            case "COMMAND_INJECTION": return "命令注入";
            case "RATE_LIMIT": return "限流触发";
            default: return attackType;
        }
    }

    private String getRiskLevelName(String riskLevel) {
        if (riskLevel == null) {
            return "未知";
        }
        switch (riskLevel) {
            case "CRITICAL": return "严重";
            case "HIGH": return "高危";
            case "MEDIUM": return "中危";
            case "LOW": return "低危";
            default: return riskLevel;
        }
    }

    private String getDefenseTypeName(String defenseType) {
        if (defenseType == null) {
            return "未知";
        }
        switch (defenseType) {
            case "BLACKLIST": return "加入黑名单";
            case "RATE_LIMIT": return "限流";
            case "BLOCK": return "拦截";
            default: return defenseType;
        }
    }
}
