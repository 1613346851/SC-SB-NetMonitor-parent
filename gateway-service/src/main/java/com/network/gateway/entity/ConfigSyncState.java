package com.network.gateway.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class ConfigSyncState implements Serializable {

    private static final long serialVersionUID = 1L;

    private String gatewayId;
    private String currentVersion;
    private long lastSyncTime;
    private long lastHeartbeatTime;
    private int syncFailCount;
    private String syncStatus;
    private String pendingVersion;
    private long versionTimestamp;
    private int handshakeStep;
    private String requestId;

    public static final String STATUS_SYNCED = "SYNCED";
    public static final String STATUS_SYNCING = "SYNCING";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PENDING = "PENDING";

    public ConfigSyncState() {
        this.syncStatus = STATUS_PENDING;
        this.syncFailCount = 0;
        this.handshakeStep = 0;
    }

    public ConfigSyncState(String gatewayId) {
        this();
        this.gatewayId = gatewayId;
    }

    public void markSynced(String version) {
        this.currentVersion = version;
        this.lastSyncTime = System.currentTimeMillis();
        this.syncStatus = STATUS_SYNCED;
        this.syncFailCount = 0;
        this.handshakeStep = 0;
        this.pendingVersion = null;
    }

    public void markSyncing(String pendingVersion, String requestId) {
        this.pendingVersion = pendingVersion;
        this.requestId = requestId;
        this.syncStatus = STATUS_SYNCING;
        this.lastSyncTime = System.currentTimeMillis();
    }

    public void markFailed() {
        this.syncStatus = STATUS_FAILED;
        this.syncFailCount++;
        this.handshakeStep = 0;
    }

    public void updateHeartbeat() {
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    public boolean needsSync(long intervalMs) {
        return System.currentTimeMillis() - lastSyncTime > intervalMs;
    }

    public boolean isSyncing() {
        return STATUS_SYNCING.equals(syncStatus);
    }

    public boolean isSynced() {
        return STATUS_SYNCED.equals(syncStatus);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(syncStatus);
    }

    public void advanceHandshake() {
        this.handshakeStep++;
    }

    public String getSummary() {
        return String.format("ConfigSyncState{gateway=%s, version=%s, status=%s, failCount=%d, handshake=%d}",
            gatewayId, currentVersion, syncStatus, syncFailCount, handshakeStep);
    }
}
