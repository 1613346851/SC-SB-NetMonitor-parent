package com.network.monitor.service;

import com.network.monitor.dto.ConfigSyncStatusDTO;

public interface ConfigSyncService {

    ConfigSyncStatusDTO getSyncStatus();

    ConfigSyncStatusDTO syncAllToGateway();

    ConfigSyncStatusDTO checkGatewayConnection();

    void recordSyncSuccess(int configCount);

    void recordSyncFailure(String message);

    Long getSyncSuccessCount();

    Long getSyncFailureCount();

    Long getLastSyncTime();
}
