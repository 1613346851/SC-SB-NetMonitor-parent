package com.network.monitor.service;

import com.network.monitor.dto.AttackChainDTO;
import com.network.monitor.dto.IpProfileDTO;

import java.time.LocalDateTime;

public interface IpProfileService {

    IpProfileDTO getIpProfile(String ip);

    Integer calculateRiskScore(String ip);

    String getRiskLevel(Integer score);

    AttackChainDTO getAttackChain(String ip, LocalDateTime startTime, LocalDateTime endTime);

    AttackChainDTO getRecentAttackChain(String ip, int limit);
}
