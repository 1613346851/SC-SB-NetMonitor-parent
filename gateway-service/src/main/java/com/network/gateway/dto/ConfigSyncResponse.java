package com.network.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSyncResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private String latestVersion;
    private long versionTimestamp;
    private Map<String, String> configs;
    private String handshakeStep;
    private String expectedNextStep;
    private String requestId;
    private long responseTimestamp;

    public static ConfigSyncResponse success(String latestVersion, Map<String, String> configs) {
        ConfigSyncResponse response = new ConfigSyncResponse();
        response.setSuccess(true);
        response.setMessage("配置同步成功");
        response.setLatestVersion(latestVersion);
        response.setVersionTimestamp(System.currentTimeMillis());
        response.setConfigs(configs);
        response.setResponseTimestamp(System.currentTimeMillis());
        return response;
    }

    public static ConfigSyncResponse handshakeStep(String step, String expectedNext, String requestId) {
        ConfigSyncResponse response = new ConfigSyncResponse();
        response.setSuccess(true);
        response.setMessage("握手进行中");
        response.setHandshakeStep(step);
        response.setExpectedNextStep(expectedNext);
        response.setRequestId(requestId);
        response.setResponseTimestamp(System.currentTimeMillis());
        return response;
    }

    public static ConfigSyncResponse noUpdate(String currentVersion) {
        ConfigSyncResponse response = new ConfigSyncResponse();
        response.setSuccess(true);
        response.setMessage("配置已是最新");
        response.setLatestVersion(currentVersion);
        response.setResponseTimestamp(System.currentTimeMillis());
        return response;
    }

    public static ConfigSyncResponse failure(String message) {
        ConfigSyncResponse response = new ConfigSyncResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setResponseTimestamp(System.currentTimeMillis());
        return response;
    }
}
