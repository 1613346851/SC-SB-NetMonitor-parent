package com.network.gateway.service;

import java.util.List;

public interface IpStateManualIntervention {

    void forceResetToNormal(String ip, String operator, String reason);

    void forceDefended(String ip, Long duration, String operator, String reason);

    void batchResetToNormal(List<String> ips, String operator, String reason);

    void forceSetState(String ip, int targetState, String operator, String reason);

    int getManualInterventionCount(String ip);
}
