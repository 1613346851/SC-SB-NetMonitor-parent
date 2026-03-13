package com.network.monitor.service.impl;

import com.network.monitor.cache.BlacklistCache;
import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.common.constant.DefenseTypeConstant;
import com.network.monitor.dto.BlacklistInfoDTO;
import com.network.monitor.dto.DefenseCommandDTO;
import com.network.monitor.entity.DefenseMonitorEntity;
import com.network.monitor.mapper.DefenseMonitorMapper;
import com.network.monitor.service.BlacklistManageService;
import com.network.monitor.service.DefenseLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BlacklistManageServiceImpl implements BlacklistManageService {

    @Autowired
    private BlacklistCache blacklistCache;

    @Autowired
    private GatewayApiClient gatewayApiClient;

    @Autowired
    private DefenseLogService defenseLogService;

    @Autowired
    private DefenseMonitorMapper defenseMonitorMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void addToBlacklist(String ip, String reason, LocalDateTime expireTime, String operator) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }

        try {
            BlacklistCache.BlacklistInfo info = blacklistCache.getBlacklistInfoWithoutRemove(ip);
            
            if (info != null && !info.isExpired()) {
                LocalDateTime currentExpireTime = info.getExpireTime() != null ? 
                    LocalDateTime.parse(info.getExpireTime(), TIME_FORMATTER) : null;
                
                if (currentExpireTime == null) {
                    log.warn("IP 已永久封禁，无需延长：ip={}", ip);
                    return;
                }
                
                long extendSeconds = java.time.Duration.between(LocalDateTime.now(), expireTime).getSeconds();
                if (extendSeconds <= 0) {
                    log.warn("延长时间无效，跳过：ip={}, extendSeconds={}", ip, extendSeconds);
                    return;
                }
                
                log.info("IP 已在黑名单中且生效中，延长封禁时间：ip={}, extendSeconds={}", ip, extendSeconds);
                
                extendBlacklistExpireTime(ip, extendSeconds);
                recordDefenseLog(ip, "UPDATE", "延长封禁时间（添加时触发）", operator, expireTime);
                return;
            }

            blacklistCache.add(ip, reason, expireTime, operator);

            syncToGateway(ip, "ADD");

            recordDefenseLog(ip, "ADD", reason, operator, expireTime);

            log.info("添加 IP 到黑名单成功：ip={}, reason={}, expireTime={}, operator={}", 
                    ip, reason, expireTime != null ? expireTime.format(TIME_FORMATTER) : "永久", operator);
        } catch (Exception e) {
            log.error("添加 IP 到黑名单失败：ip={}", ip, e);
            throw new RuntimeException("添加黑名单失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void extendBlacklistExpireTime(String ip, Long extendSeconds) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }

        try {
            List<DefenseMonitorEntity> historyRecords = defenseMonitorMapper.selectBlacklistsByIp(ip);
            if (historyRecords != null && !historyRecords.isEmpty()) {
                DefenseMonitorEntity latestRecord = historyRecords.get(0);
                
                LocalDateTime currentExpireTime = latestRecord.getExpireTime();
                if (currentExpireTime == null) {
                    throw new RuntimeException("永久封禁的IP无法延长");
                }
                
                LocalDateTime newExpireTime = currentExpireTime.plusSeconds(extendSeconds);
                
                latestRecord.setExpireTime(newExpireTime);
                latestRecord.setUpdateTime(LocalDateTime.now());
                defenseMonitorMapper.updateById(latestRecord);
                
                BlacklistCache.BlacklistInfo info = blacklistCache.getBlacklistInfoWithoutRemove(ip);
                if (info != null) {
                    blacklistCache.add(ip, info.getReason(), newExpireTime, info.getOperator());
                }
                
                syncToGateway(ip, "ADD");
                
                log.info("延长黑名单过期时间成功：ip={}, currentExpireTime={}, extendSeconds={}, newExpireTime={}", 
                    ip, currentExpireTime.format(TIME_FORMATTER), extendSeconds, newExpireTime.format(TIME_FORMATTER));
            } else {
                throw new RuntimeException("IP 不在黑名单中");
            }
        } catch (Exception e) {
            log.error("延长黑名单过期时间失败：ip={}", ip, e);
            throw new RuntimeException("延长黑名单过期时间失败：" + e.getMessage(), e);
        }
    }

    @Override
    public int deleteAllBlacklistsByIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return 0;
        }

        try {
            int count = defenseMonitorMapper.deleteAllBlacklistsByIp(ip);
            
            blacklistCache.remove(ip);
            syncToGateway(ip, "REMOVE");
            
            recordDefenseLog(ip, "REMOVE", "删除所有封禁记录", "MANUAL", null);
            
            log.info("删除 IP 的所有黑名单记录成功：ip={}, count={}", ip, count);
            return count;
        } catch (Exception e) {
            log.error("删除 IP 的所有黑名单记录失败：ip={}", ip, e);
            throw new RuntimeException("删除黑名单记录失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void removeFromBlacklist(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        try {
            blacklistCache.remove(ip);

            syncToGateway(ip, "REMOVE");

            recordDefenseLog(ip, "REMOVE", "手动移除黑名单", "MANUAL", null);

            log.info("从黑名单移除 IP 成功：ip={}", ip);
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

        return blacklistCache.contains(ip);
    }

    @Override
    public List<BlacklistInfoDTO> getBlacklist() {
        List<BlacklistInfoDTO> result = new ArrayList<>();

        try {
            List<String> allIps = blacklistCache.getAllIps();
            for (String ip : allIps) {
                BlacklistCache.BlacklistInfo info = blacklistCache.getBlacklistInfo(ip);
                if (info != null) {
                    BlacklistInfoDTO dto = new BlacklistInfoDTO();
                    dto.setId(info.getId());
                    dto.setIp(info.getIp());
                    dto.setReason(info.getReason());
                    dto.setExpireTime(info.getExpireTime());
                    dto.setCreateTime(info.getCreateTime());
                    dto.setOperator(info.getOperator());
                    dto.setStatus(info.isExpired() ? 2 : 1);
                    
                    result.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("获取黑名单列表失败：", e);
        }

        return result;
    }

    @Override
    public Map<String, Object> getBlacklistInfo(String ip) {
        if (ip == null || ip.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();
        try {
            BlacklistCache.BlacklistInfo info = blacklistCache.getBlacklistInfo(ip);
            if (info != null) {
                result.put("ip", info.getIp());
                result.put("reason", info.getReason());
                result.put("expireTime", info.getExpireTime());
                result.put("createTime", info.getCreateTime());
                result.put("operator", info.getOperator());
                result.put("status", info.isExpired() ? 2 : 1);
            }
        } catch (Exception e) {
            log.error("获取黑名单信息失败：ip={}", ip, e);
        }

        return result;
    }

    @Override
    public Map<String, Object> getBlacklistWithHistory(String ip) {
        if (ip == null || ip.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();
        try {
            List<DefenseMonitorEntity> historyRecords = defenseMonitorMapper.selectBlacklistsByIp(ip);
            if (historyRecords != null && !historyRecords.isEmpty()) {
                DefenseMonitorEntity latestRecord = historyRecords.get(0);
                
                result.put("id", latestRecord.getId());
                result.put("ip", ip);
                result.put("reason", latestRecord.getDefenseReason());
                result.put("expireTime", latestRecord.getExpireTime() != null ? 
                    latestRecord.getExpireTime().format(TIME_FORMATTER) : null);
                result.put("createTime", latestRecord.getCreateTime() != null ? 
                    latestRecord.getCreateTime().format(TIME_FORMATTER) : null);
                result.put("operator", latestRecord.getOperator());
                
                boolean isExpired = latestRecord.getExpireTime() != null && 
                    LocalDateTime.now().isAfter(latestRecord.getExpireTime());
                result.put("status", isExpired ? 2 : 1);
                
                result.put("remainingSeconds", calculateRemainingSeconds(latestRecord.getExpireTime()));
                result.put("remainingTime", formatRemainingTime(latestRecord.getExpireTime()));
                
                List<BlacklistInfoDTO.BlacklistHistoryDTO> historyList = new ArrayList<>();
                for (DefenseMonitorEntity record : historyRecords) {
                    BlacklistInfoDTO.BlacklistHistoryDTO historyDTO = new BlacklistInfoDTO.BlacklistHistoryDTO();
                    historyDTO.setId(record.getId());
                    historyDTO.setReason(record.getDefenseReason());
                    historyDTO.setExpireTime(record.getExpireTime() != null ? 
                        record.getExpireTime().format(TIME_FORMATTER) : null);
                    historyDTO.setCreateTime(record.getCreateTime() != null ? 
                        record.getCreateTime().format(TIME_FORMATTER) : null);
                    historyDTO.setOperator(record.getOperator());
                    
                    boolean expired = record.getExpireTime() != null && 
                        LocalDateTime.now().isAfter(record.getExpireTime());
                    historyDTO.setStatus(expired ? 2 : 1);
                    
                    historyList.add(historyDTO);
                }
                result.put("history", historyList);
            }
        } catch (Exception e) {
            log.error("获取黑名单信息失败：ip={}", ip, e);
        }

        return result;
    }

    @Override
    public List<BlacklistInfoDTO> getBlacklistGroupedByIp() {
        List<BlacklistInfoDTO> result = new ArrayList<>();

        try {
            List<String> allIps = blacklistCache.getAllIps();
            for (String ip : allIps) {
                BlacklistCache.BlacklistInfo info = blacklistCache.getBlacklistInfo(ip);
                if (info != null) {
                    BlacklistInfoDTO dto = new BlacklistInfoDTO();
                    dto.setId(info.getId());
                    dto.setIp(info.getIp());
                    dto.setReason(info.getReason());
                    dto.setExpireTime(info.getExpireTime());
                    dto.setCreateTime(info.getCreateTime());
                    dto.setOperator(info.getOperator());
                    dto.setStatus(info.isExpired() ? 2 : 1);
                    
                    LocalDateTime expireDateTime = null;
                    if (info.getExpireTime() != null && !info.getExpireTime().isEmpty()) {
                        try {
                            expireDateTime = LocalDateTime.parse(info.getExpireTime(), TIME_FORMATTER);
                        } catch (Exception e) {
                            log.warn("解析过期时间失败：{}", info.getExpireTime(), e);
                        }
                    }
                    
                    dto.setRemainingSeconds(calculateRemainingSeconds(expireDateTime));
                    dto.setRemainingTime(formatRemainingTime(expireDateTime));
                    
                    result.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("获取黑名单列表失败：", e);
        }

        return result;
    }

    private Long calculateRemainingSeconds(LocalDateTime expireTime) {
        if (expireTime == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expireTime)) {
            return 0L;
        }
        return java.time.Duration.between(now, expireTime).getSeconds();
    }

    private String formatRemainingTime(LocalDateTime expireTime) {
        Long remainingSeconds = calculateRemainingSeconds(expireTime);
        if (remainingSeconds == null) {
            return "永久";
        }
        if (remainingSeconds <= 0) {
            return "已过期";
        }
        
        long days = remainingSeconds / (24 * 3600);
        long hours = (remainingSeconds % (24 * 3600)) / 3600;
        long minutes = (remainingSeconds % 3600) / 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分");
        }
        if (sb.length() == 0) {
            sb.append("不足1分钟");
        }
        
        return sb.toString();
    }

    @Override
    public int cleanExpiredBlacklist() {
        try {
            int count = blacklistCache.cleanExpired();
            
            log.info("清理过期黑名单完成，数量：{}", count);
            return count;
        } catch (Exception e) {
            log.error("清理过期黑名单失败：", e);
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
            commandDTO.setDefenseType(DefenseTypeConstant.IP_BLOCK);
            commandDTO.setDefenseAction(action);
            commandDTO.setDefenseTarget(ip);
            commandDTO.setDefenseReason("黑名单同步");
            commandDTO.setRiskLevel("HIGH");

            if ("ADD".equals(action)) {
                BlacklistCache.BlacklistInfo info = blacklistCache.getBlacklistInfo(ip);
                if (info != null) {
                    commandDTO.setExpireTime(info.getExpireTime());
                }
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
            log.info("刷新黑名单缓存完成");
        } catch (Exception e) {
            log.error("刷新黑名单缓存失败：", e);
        }
    }

    @Override
    public void clear() {
        try {
            List<String> allIps = blacklistCache.getAllIps();
            for (String ip : allIps) {
                syncToGateway(ip, "REMOVE");
            }
            blacklistCache.clear();
            log.info("清空所有黑名单完成");
        } catch (Exception e) {
            log.error("清空所有黑名单失败：", e);
        }
    }

    @Override
    public int getSize() {
        return blacklistCache.getSize();
    }

    private void recordDefenseLog(String ip, String action, String reason, String operator, LocalDateTime expireTime) {
        try {
            DefenseMonitorEntity defenseLog = new DefenseMonitorEntity();
            defenseLog.setAttackId(null);
            defenseLog.setTrafficId(null);
            defenseLog.setDefenseType(DefenseTypeConstant.IP_BLOCK);
            defenseLog.setDefenseAction(action);
            defenseLog.setDefenseTarget(ip);
            defenseLog.setDefenseReason(reason);
            defenseLog.setExpireTime(expireTime);
            defenseLog.setExecuteStatus(1);
            defenseLog.setExecuteResult("黑名单" + ("ADD".equals(action) ? "添加" : "移除") + "成功");
            defenseLog.setOperator(operator);
            defenseLog.setCreateTime(LocalDateTime.now());
            defenseLog.setUpdateTime(LocalDateTime.now());

            defenseLogService.saveDefenseLog(defenseLog);

            log.debug("记录黑名单防御日志成功：ip={}, action={}, operator={}", ip, action, operator);
        } catch (Exception e) {
            log.error("记录黑名单防御日志失败：ip={}", ip, e);
        }
    }
}
