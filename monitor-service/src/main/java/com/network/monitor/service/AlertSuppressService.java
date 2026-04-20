package com.network.monitor.service;

/**
 * 告警抑制服务接口
 */
public interface AlertSuppressService {

    boolean shouldSuppress(String sourceIp, String attackType);

    void recordAlert(String sourceIp, String attackType);

    void clearSuppress(String sourceIp, String attackType);

    void clearAllSuppress();
}
