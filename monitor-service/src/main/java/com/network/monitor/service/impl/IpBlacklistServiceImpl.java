package com.network.monitor.service.impl;

import com.network.monitor.cache.IpAttackStateCache;
import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.dto.DefenseCommandDTO;
import com.network.monitor.entity.DefenseLogEntity;
import com.network.monitor.entity.IpBlacklistEntity;
import com.network.monitor.entity.IpBlacklistHistoryEntity;
import com.network.monitor.mapper.DefenseLogMapper;
import com.network.monitor.mapper.IpBlacklistHistoryMapper;
import com.network.monitor.mapper.IpBlacklistMapper;
import com.network.monitor.service.IpBlacklistService;
import com.network.monitor.service.LocalCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class IpBlacklistServiceImpl implements IpBlacklistService {

    @Autowired
    private IpBlacklistMapper ipBlacklistMapper;

    @Autowired
    private IpBlacklistHistoryMapper ipBlacklistHistoryMapper;

    @Autowired
    private DefenseLogMapper defenseLogMapper;

    @Autowired
    private LocalCacheService localCacheService;

    @Autowired
    private GatewayApiClient gatewayApiClient;

    @Autowired
    private IpAttackStateCache attackStateCache;

    private static final String BLACKLIST_CACHE_PREFIX = "cache:blacklist:";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, IpBlacklistEntity> blacklistCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadFromDatabase();
        log.info("IP黑名单缓存初始化完成");
    }

    private void loadFromDatabase() {
        try {
            List<IpBlacklistEntity> allBlacklists = ipBlacklistMapper.selectBanning();
            for (IpBlacklistEntity entity : allBlacklists) {
                blacklistCache.put(entity.getIpAddress(), entity);
                String cacheKey = BLACKLIST_CACHE_PREFIX + entity.getIpAddress();
                localCacheService.put(cacheKey, entity, -1);
            }
            log.info("从数据库加载IP黑名单数据完成，共{}条", allBlacklists.size());
        } catch (Exception e) {
            log.error("从数据库加载IP黑名单数据失败", e);
        }
    }

    @Override
    @Transactional
    public void addToBlacklist(String ip, String reason, LocalDateTime expireTime, String operator, String banType, Long attackId, Long trafficId, Long ruleId) {
        addToBlacklist(ip, reason, expireTime, operator, banType, attackId, trafficId, ruleId, null);
    }

    @Override
    @Transactional
    public void addToBlacklist(String ip, String reason, LocalDateTime expireTime, String operator, String banType, Long attackId, Long trafficId, Long ruleId, String eventId) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }

        try {
            IpBlacklistEntity existingEntity = ipBlacklistMapper.selectByIpAddress(ip);
            LocalDateTime now = LocalDateTime.now();

            if (expireTime == null) {
                if (existingEntity != null && existingEntity.isPermanent()) {
                    log.warn("IP 已永久封禁，无需重复添加：ip={}", ip);
                    return;
                }

                if (existingEntity == null) {
                    existingEntity = new IpBlacklistEntity();
                    existingEntity.setIpAddress(ip);
                    existingEntity.setTotalBanCount(0);
                    existingEntity.setCreateTime(now);
                }

                existingEntity.setCurrentExpireTime(null);
                existingEntity.setTotalBanCount(existingEntity.getTotalBanCount() + 1);
                existingEntity.setLastBanTime(now);
                if (existingEntity.getFirstBanTime() == null) {
                    existingEntity.setFirstBanTime(now);
                }
                existingEntity.setUpdateTime(now);
                existingEntity.setStatus(1);

                if (existingEntity.getId() == null) {
                    ipBlacklistMapper.insert(existingEntity);
                } else {
                    ipBlacklistMapper.updateById(existingEntity);
                }

                createHistoryRecord(existingEntity.getId(), attackId, trafficId, ruleId, banType, reason, null, null, operator);
                recordDefenseLog("BLOCK_IP", "ADD", ip, attackId, trafficId, ruleId, reason, null, 1, "永久封禁成功", operator, eventId);

                blacklistCache.put(ip, existingEntity);
                syncToGateway(ip, "ADD");

                log.info("添加 IP 到黑名单成功（永久封禁）：ip={}, reason={}, operator={}", ip, reason, operator);
                return;
            }

            LocalDateTime baseTime = now;
            boolean hasActiveRecord = false;

            if (existingEntity != null) {
                if (existingEntity.isPermanent()) {
                    log.warn("IP 已永久封禁，无需延长：ip={}", ip);
                    return;
                }
                if (existingEntity.getCurrentExpireTime() != null && existingEntity.getCurrentExpireTime().isAfter(now)) {
                    hasActiveRecord = true;
                    baseTime = existingEntity.getCurrentExpireTime();
                }
            }

            LocalDateTime finalExpireTime;
            if (hasActiveRecord) {
                long extendSeconds = java.time.Duration.between(now, expireTime).getSeconds();
                finalExpireTime = baseTime.plusSeconds(extendSeconds);
                log.info("IP 存在生效中的封禁记录，延长封禁时间：ip={}, baseTime={}, extendSeconds={}, finalExpireTime={}",
                    ip, baseTime.format(TIME_FORMATTER), extendSeconds, finalExpireTime.format(TIME_FORMATTER));
            } else {
                finalExpireTime = expireTime;
                log.info("IP 无生效中的封禁记录，创建新封禁记录：ip={}, expireTime={}", ip, finalExpireTime.format(TIME_FORMATTER));
            }

            if (existingEntity == null) {
                existingEntity = new IpBlacklistEntity();
                existingEntity.setIpAddress(ip);
                existingEntity.setTotalBanCount(0);
                existingEntity.setCreateTime(now);
            }

            existingEntity.setCurrentExpireTime(finalExpireTime);
            existingEntity.setTotalBanCount(existingEntity.getTotalBanCount() + 1);
            existingEntity.setLastBanTime(now);
            if (existingEntity.getFirstBanTime() == null) {
                existingEntity.setFirstBanTime(now);
            }
            existingEntity.setUpdateTime(now);
            existingEntity.setStatus(1);

            if (existingEntity.getId() == null) {
                ipBlacklistMapper.insert(existingEntity);
            } else {
                ipBlacklistMapper.updateById(existingEntity);
            }

            Long banDuration = java.time.Duration.between(now, finalExpireTime).getSeconds();
            createHistoryRecord(existingEntity.getId(), attackId, trafficId, ruleId, banType, reason, banDuration, finalExpireTime, operator);
            recordDefenseLog("BLOCK_IP", "ADD", ip, attackId, trafficId, ruleId, reason, finalExpireTime, 1, "封禁成功", operator, eventId);

            blacklistCache.put(ip, existingEntity);
            syncToGateway(ip, "ADD");

            log.info("添加 IP 到黑名单成功：ip={}, reason={}, expireTime={}, operator={}",
                    ip, reason, finalExpireTime.format(TIME_FORMATTER), operator);
        } catch (Exception e) {
            log.error("添加 IP 到黑名单失败：ip={}", ip, e);
            throw new RuntimeException("添加黑名单失败：" + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void extendBlacklist(String ip, Long extendSeconds, String operator) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }

        try {
            IpBlacklistEntity existingEntity = ipBlacklistMapper.selectByIpAddress(ip);
            LocalDateTime now = LocalDateTime.now();

            if (existingEntity != null && existingEntity.isPermanent()) {
                throw new RuntimeException("永久封禁的IP无法延长");
            }

            LocalDateTime baseTime = now;
            if (existingEntity != null && existingEntity.getCurrentExpireTime() != null && existingEntity.getCurrentExpireTime().isAfter(now)) {
                baseTime = existingEntity.getCurrentExpireTime();
            }

            LocalDateTime newExpireTime = baseTime.plusSeconds(extendSeconds);

            if (existingEntity == null) {
                existingEntity = new IpBlacklistEntity();
                existingEntity.setIpAddress(ip);
                existingEntity.setTotalBanCount(0);
                existingEntity.setCreateTime(now);
            }

            existingEntity.setCurrentExpireTime(newExpireTime);
            existingEntity.setTotalBanCount(existingEntity.getTotalBanCount() + 1);
            existingEntity.setLastBanTime(now);
            if (existingEntity.getFirstBanTime() == null) {
                existingEntity.setFirstBanTime(now);
            }
            existingEntity.setUpdateTime(now);
            existingEntity.setStatus(1);

            if (existingEntity.getId() == null) {
                ipBlacklistMapper.insert(existingEntity);
            } else {
                ipBlacklistMapper.updateById(existingEntity);
            }

            Long banDuration = java.time.Duration.between(now, newExpireTime).getSeconds();
            createHistoryRecord(existingEntity.getId(), null, null, null, "MANUAL", "延长封禁时间", banDuration, newExpireTime, operator);
            recordDefenseLog("BLOCK_IP", "UPDATE", ip, null, null, null, "延长封禁时间", newExpireTime, 1, "延长成功", operator);

            blacklistCache.put(ip, existingEntity);
            syncToGateway(ip, "ADD");

            log.info("延长黑名单过期时间成功：ip={}, baseTime={}, extendSeconds={}, newExpireTime={}",
                ip, baseTime.format(TIME_FORMATTER), extendSeconds, newExpireTime.format(TIME_FORMATTER));
        } catch (Exception e) {
            log.error("延长黑名单过期时间失败：ip={}", ip, e);
            throw new RuntimeException("延长黑名单过期时间失败：" + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void removeFromBlacklist(String ip, String unbanReason, String operator) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        try {
            IpBlacklistEntity existingEntity = ipBlacklistMapper.selectByIpAddress(ip);
            if (existingEntity == null) {
                log.warn("IP 不在黑名单中：ip={}", ip);
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            existingEntity.setStatus(0);
            existingEntity.setUpdateTime(now);
            ipBlacklistMapper.updateById(existingEntity);

            List<IpBlacklistHistoryEntity> historyList = ipBlacklistHistoryMapper.selectBanningByBlacklistId(existingEntity.getId());
            for (IpBlacklistHistoryEntity history : historyList) {
                history.setProcessStatus(2);
                history.setUnbanExecuteTime(now);
                history.setUnbanReason(unbanReason);
                ipBlacklistHistoryMapper.updateById(history);
            }

            recordDefenseLog("BLOCK_IP", "REMOVE", ip, null, null, null, unbanReason, null, 1, "解封成功", operator);

            blacklistCache.remove(ip);
            
            attackStateCache.markAsCooldown(ip);
            log.info("IP状态已更新为COOLDOWN: ip={}", ip);
            
            syncToGateway(ip, "REMOVE");

            log.info("从黑名单移除 IP 成功：ip={}, unbanReason={}, operator={}", ip, unbanReason, operator);
        } catch (Exception e) {
            log.error("从黑名单移除 IP 失败：ip={}", ip, e);
            throw new RuntimeException("移除黑名单失败：" + e.getMessage(), e);
        }
    }

    @Override
    public boolean isInBlacklist(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        IpBlacklistEntity entity = blacklistCache.get(ip);
        if (entity == null) {
            entity = ipBlacklistMapper.selectByIpAddress(ip);
            if (entity != null) {
                blacklistCache.put(ip, entity);
            }
        }

        if (entity == null) {
            return false;
        }

        if (entity.isExpired()) {
            blacklistCache.remove(ip);
            return false;
        }

        return entity.isBanned();
    }

    @Override
    public IpBlacklistEntity getBlacklistByIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        return ipBlacklistMapper.selectByIpAddress(ip);
    }

    @Override
    public List<IpBlacklistEntity> getAllBlacklists() {
        return ipBlacklistMapper.selectAll();
    }

    @Override
    public List<IpBlacklistEntity> getBanningBlacklists() {
        return ipBlacklistMapper.selectBanning();
    }

    @Override
    public List<IpBlacklistHistoryEntity> getHistoryByIp(String ip) {
        IpBlacklistEntity entity = ipBlacklistMapper.selectByIpAddress(ip);
        if (entity == null) {
            return null;
        }
        return ipBlacklistHistoryMapper.selectByBlacklistId(entity.getId());
    }

    @Override
    @Transactional
    public int cleanExpiredBlacklists() {
        try {
            List<IpBlacklistEntity> expiredList = ipBlacklistMapper.selectExpired();
            LocalDateTime now = LocalDateTime.now();

            for (IpBlacklistEntity entity : expiredList) {
                entity.setStatus(0);
                entity.setUpdateTime(now);
                ipBlacklistMapper.updateById(entity);

                List<IpBlacklistHistoryEntity> historyList = ipBlacklistHistoryMapper.selectBanningByBlacklistId(entity.getId());
                for (IpBlacklistHistoryEntity history : historyList) {
                    history.setProcessStatus(2);
                    history.setUnbanExecuteTime(now);
                    history.setUnbanReason("过期自动解封");
                    ipBlacklistHistoryMapper.updateById(history);
                }

                blacklistCache.remove(entity.getIpAddress());
                syncToGateway(entity.getIpAddress(), "REMOVE");
            }

            log.info("清理过期黑名单完成，数量：{}", expiredList.size());
            return expiredList.size();
        } catch (Exception e) {
            log.error("清理过期黑名单失败", e);
            return 0;
        }
    }

    @Override
    public void syncToGateway(String ip, String action) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        try {
            DefenseCommandDTO commandDTO = new DefenseCommandDTO();
            commandDTO.setSourceIp(ip);
            commandDTO.setDefenseType(DefenseCommandDTO.DefenseType.BLACKLIST);
            commandDTO.setRiskLevel(DefenseCommandDTO.RiskLevel.HIGH);

            if ("ADD".equals(action)) {
                IpBlacklistEntity entity = ipBlacklistMapper.selectByIpAddress(ip);
                if (entity != null && entity.getCurrentExpireTime() != null) {
                    commandDTO.setExpireTimestamp(
                        entity.getCurrentExpireTime()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    );
                }
                commandDTO.setDescription("IP黑名单添加：" + ip);
            } else {
                commandDTO.setDescription("IP黑名单移除：" + ip);
            }

            gatewayApiClient.pushDefenseCommand(commandDTO);
            log.info("同步黑名单到网关成功：ip={}, action={}", ip, action);
        } catch (Exception e) {
            log.error("同步黑名单到网关失败：ip={}, action={}", ip, action, e);
        }
    }

    @Override
    public void refreshCache() {
        try {
            blacklistCache.clear();
            localCacheService.clearByPrefix(BLACKLIST_CACHE_PREFIX);
            loadFromDatabase();
            log.info("刷新黑名单缓存完成");
        } catch (Exception e) {
            log.error("刷新黑名单缓存失败", e);
        }
    }

    @Override
    @Transactional
    public void clear() {
        try {
            List<IpBlacklistEntity> allBlacklists = ipBlacklistMapper.selectAll();
            for (IpBlacklistEntity entity : allBlacklists) {
                syncToGateway(entity.getIpAddress(), "REMOVE");
            }

            blacklistCache.clear();
            localCacheService.clearByPrefix(BLACKLIST_CACHE_PREFIX);

            log.info("清空所有黑名单完成");
        } catch (Exception e) {
            log.error("清空所有黑名单失败", e);
        }
    }

    @Override
    public int getTotalCount() {
        return (int) ipBlacklistMapper.countAll();
    }

    @Override
    public int getBanningCount() {
        return (int) ipBlacklistMapper.countBanning();
    }

    private void createHistoryRecord(Long blacklistId, Long attackId, Long trafficId, Long ruleId, String banType, String banReason, Long banDuration, LocalDateTime expireTime, String operator) {
        IpBlacklistHistoryEntity history = new IpBlacklistHistoryEntity();
        history.setBlacklistId(blacklistId);
        history.setAttackId(attackId);
        history.setTrafficId(trafficId);
        history.setRuleId(ruleId);
        history.setBanType(banType != null ? banType : "MANUAL");
        history.setBanReason(banReason);
        history.setBanDuration(banDuration);
        history.setExpireTime(expireTime);
        history.setProcessStatus(1);
        history.setOperator(operator);
        history.setBanExecuteTime(LocalDateTime.now());
        history.setCreateTime(LocalDateTime.now());
        ipBlacklistHistoryMapper.insert(history);
    }

    private void recordDefenseLog(String defenseType, String defenseAction, String defenseTarget, Long attackId, Long trafficId, Long ruleId, String defenseReason, LocalDateTime expireTime, Integer executeStatus, String executeResult, String operator, String eventId) {
        try {
            DefenseLogEntity defenseLog = new DefenseLogEntity();
            defenseLog.setDefenseType(defenseType);
            defenseLog.setDefenseAction(defenseAction);
            defenseLog.setDefenseTarget(defenseTarget);
            defenseLog.setAttackId(attackId);
            defenseLog.setTrafficId(trafficId);
            defenseLog.setRuleId(ruleId);
            defenseLog.setDefenseReason(defenseReason);
            defenseLog.setExpireTime(expireTime);
            defenseLog.setExecuteStatus(executeStatus);
            defenseLog.setExecuteResult(executeResult);
            defenseLog.setOperator(operator);
            defenseLog.setEventId(eventId);
            defenseLog.setExecuteTime(LocalDateTime.now());
            defenseLog.setCreateTime(LocalDateTime.now());
            defenseLog.setUpdateTime(LocalDateTime.now());
            defenseLogMapper.insert(defenseLog);
        } catch (Exception e) {
            log.error("记录防御日志失败：defenseTarget={}", defenseTarget, e);
        }
    }

    private void recordDefenseLog(String defenseType, String defenseAction, String defenseTarget, Long attackId, Long trafficId, Long ruleId, String defenseReason, LocalDateTime expireTime, Integer executeStatus, String executeResult, String operator) {
        recordDefenseLog(defenseType, defenseAction, defenseTarget, attackId, trafficId, ruleId, defenseReason, expireTime, executeStatus, executeResult, operator, null);
    }
}
