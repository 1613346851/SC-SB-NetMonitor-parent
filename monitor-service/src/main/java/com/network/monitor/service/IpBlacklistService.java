package com.network.monitor.service;

import com.network.monitor.entity.IpBlacklistEntity;
import com.network.monitor.entity.IpBlacklistHistoryEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface IpBlacklistService {

    void addToBlacklist(String ip, String reason, LocalDateTime expireTime, String operator, String banType, Long attackId, Long trafficId, Long ruleId);

    void extendBlacklist(String ip, Long extendSeconds, String operator);

    void removeFromBlacklist(String ip, String unbanReason, String operator);

    boolean isInBlacklist(String ip);

    IpBlacklistEntity getBlacklistByIp(String ip);

    List<IpBlacklistEntity> getAllBlacklists();

    List<IpBlacklistEntity> getBanningBlacklists();

    List<IpBlacklistHistoryEntity> getHistoryByIp(String ip);

    int cleanExpiredBlacklists();

    void syncToGateway(String ip, String action);

    void refreshCache();

    void clear();

    int getTotalCount();

    int getBanningCount();
}
