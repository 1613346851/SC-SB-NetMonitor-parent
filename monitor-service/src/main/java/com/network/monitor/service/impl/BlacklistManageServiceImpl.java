package com.network.monitor.service.impl;

import com.network.monitor.cache.BlacklistCache;
import com.network.monitor.client.GatewayApiClient;
import com.network.monitor.common.constant.DefenseTypeConstant;
import com.network.monitor.dto.DefenseCommandDTO;
import com.network.monitor.entity.DefenseMonitorEntity;
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

/**
 * IP 黑名单管理服务实现类
 * 实现 IP 黑名单的全生命周期管理，维护内存中的黑名单缓存，与网关服务的黑名单缓存保持同步
 */
@Slf4j
@Service
public class BlacklistManageServiceImpl implements BlacklistManageService {

    @Autowired
    private BlacklistCache blacklistCache;

    @Autowired
    private GatewayApiClient gatewayApiClient;

    @Autowired
    private DefenseLogService defenseLogService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void addToBlacklist(String ip, String reason, LocalDateTime expireTime, String operator) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }

        try {
            // 添加到本地缓存
            blacklistCache.add(ip, reason, expireTime, operator);

            // 同步到网关服务
            syncToGateway(ip, "ADD");

            // 记录防御日志
            recordDefenseLog(ip, "ADD", reason, operator, expireTime);

            log.info("添加 IP 到黑名单成功：ip={}, reason={}, expireTime={}, operator={}", 
                    ip, reason, expireTime.format(TIME_FORMATTER), operator);
        } catch (Exception e) {
            log.error("添加 IP 到黑名单失败：ip={}", ip, e);
            throw new RuntimeException("添加黑名单失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void removeFromBlacklist(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }

        try {
            // 从本地缓存移除
            blacklistCache.remove(ip);

            // 同步到网关服务
            syncToGateway(ip, "REMOVE");

            // 记录防御日志
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
    public List<Map<String, Object>> getBlacklist() {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            List<String> allIps = blacklistCache.getAllIps();
            for (String ip : allIps) {
                BlacklistCache.BlacklistInfo info = blacklistCache.getBlacklistInfo(ip);
                if (info != null) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("ip", info.getIp());
                    item.put("reason", info.getReason());
                    item.put("expireTime", info.getExpireTime());
                    item.put("createTime", info.getCreateTime());
                    item.put("operator", info.getOperator());
                    result.add(item);
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
            }
        } catch (Exception e) {
            log.error("获取黑名单信息失败：ip={}", ip, e);
        }

        return result;
    }

    @Override
    public int cleanExpiredBlacklist() {
        try {
            int count = blacklistCache.cleanExpired();
            
            // 同步清理网关服务
            // 网关服务会自行清理过期的黑名单
            
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

    /**
     * 记录防御日志
     */
    private void recordDefenseLog(String ip, String action, String reason, String operator, LocalDateTime expireTime) {
        try {
            DefenseMonitorEntity defenseLog = new DefenseMonitorEntity();
            defenseLog.setAttackId(null); // 黑名单操作不关联具体攻击
            defenseLog.setTrafficId(null); // 不关联具体流量
            defenseLog.setDefenseType(DefenseTypeConstant.IP_BLOCK);
            defenseLog.setDefenseAction(action);
            defenseLog.setDefenseTarget(ip);
            defenseLog.setDefenseReason(reason);
            defenseLog.setExpireTime(expireTime);
            defenseLog.setExecuteStatus(1); // 执行成功
            defenseLog.setExecuteResult("黑名单" + ("ADD".equals(action) ? "添加" : "移除") + "成功");
            defenseLog.setOperator(operator);
            defenseLog.setCreateTime(LocalDateTime.now());
            defenseLog.setUpdateTime(LocalDateTime.now());

            defenseLogService.saveDefenseLog(defenseLog);

            log.debug("记录黑名单防御日志成功：ip={}, action={}, operator={}", ip, action, operator);
        } catch (Exception e) {
            log.error("记录黑名单防御日志失败：ip={}", ip, e);
            // 防御日志记录失败不影响主流程
        }
    }
}
