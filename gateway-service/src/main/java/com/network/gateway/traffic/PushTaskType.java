package com.network.gateway.traffic;

public enum PushTaskType {

    PERIODIC_FLUSH("周期性刷新"),
    STATE_TRANSITION("状态转换"),
    MANUAL_FLUSH("手动刷新"),
    ERROR_RETRY("错误重试");

    private final String description;

    PushTaskType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
