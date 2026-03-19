package com.network.monitor.service;

import com.network.monitor.dto.AttackMonitorDTO;
import com.network.monitor.dto.DefenseCommandDTO;

/**
 * 防御决策生成服务接口
 */
public interface DefenseDecisionService {

    /**
     * 基于攻击记录生成防御决策
     *
     * @param attackDTO 攻击记录
     * @return 防御指令（如果需要触发防御）
     */
    DefenseCommandDTO generateDefenseDecision(AttackMonitorDTO attackDTO);

    /**
     * 手动生成防御决策
     */
    DefenseCommandDTO generateManualDefense(String defenseType, String target, String reason);
}
