package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSyncRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String gatewayId;
    private String currentVersion;
    private long requestTimestamp;
    private String syncType;
    private String requestId;

    public static ConfigSyncRequest createSyncRequest(String gatewayId, String currentVersion) {
        ConfigSyncRequest request = new ConfigSyncRequest();
        request.setGatewayId(gatewayId);
        request.setCurrentVersion(currentVersion);
        request.setRequestTimestamp(System.currentTimeMillis());
        request.setSyncType("FULL_SYNC");
        request.setRequestId(generateRequestId(gatewayId));
        return request;
    }

    public static ConfigSyncRequest createHeartbeatRequest(String gatewayId, String currentVersion) {
        ConfigSyncRequest request = new ConfigSyncRequest();
        request.setGatewayId(gatewayId);
        request.setCurrentVersion(currentVersion);
        request.setRequestTimestamp(System.currentTimeMillis());
        request.setSyncType("HEARTBEAT");
        request.setRequestId(generateRequestId(gatewayId));
        return request;
    }

    private static String generateRequestId(String gatewayId) {
        return gatewayId + "_" + System.currentTimeMillis() + "_" + 
            java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
