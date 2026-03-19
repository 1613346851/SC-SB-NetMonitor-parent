package com.network.monitor.service;

import com.network.monitor.dto.DefenseLogDTO;
import com.network.monitor.entity.DefenseLogEntity;

public interface DefenseLogService {

    void receiveDefenseLog(DefenseLogDTO logDTO);

    void saveDefenseLog(DefenseLogEntity entity);
}
