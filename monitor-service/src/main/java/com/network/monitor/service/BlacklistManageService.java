package com.network.monitor.service;

import com.network.monitor.dto.BlacklistInfoDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * IP 黑名单管理服务接口
 */
public interface BlacklistManageService {

    /**
     * 添加 IP 到黑名单
     *
     * @param ip         IP 地址
     * @param reason     拉黑原因
     * @param expireTime 过期时间
     * @param operator   操作人
     */
    void addToBlacklist(String ip, String reason, LocalDateTime expireTime, String operator);

    /**
     * 从黑名单移除 IP
     *
     * @param ip IP 地址
     */
    void removeFromBlacklist(String ip);

    /**
     * 检查 IP 是否在黑名单中
     *
     * @param ip IP 地址
     * @return 是否在黑名单中
     */
    boolean isInBlacklist(String ip);

    /**
     * 获取黑名单列表
     *
     * @return 黑名单列表
     */
    List<BlacklistInfoDTO> getBlacklist();

    /**
     * 根据 IP 获取黑名单信息
     *
     * @param ip IP 地址
     * @return 黑名单信息
     */
    Map<String, Object> getBlacklistInfo(String ip);

    /**
     * 清理过期的黑名单
     *
     * @return 清理的数量
     */
    int cleanExpiredBlacklist();

    /**
     * 同步黑名单到网关服务
     *
     * @param ip IP 地址
     * @param action 动作（ADD/REMOVE）
     */
    void syncToGateway(String ip, String action);

    /**
     * 刷新黑名单缓存
     */
    void refreshCache();

    /**
     * 清空所有黑名单
     */
    void clear();

    /**
     * 获取黑名单总数
     *
     * @return 黑名单总数
     */
    int getSize();
}
