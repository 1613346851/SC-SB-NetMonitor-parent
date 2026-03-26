package com.network.gateway.enums;

public enum TrafficPushStrategy {
    
    REALTIME("realtime", "实时推送"),
    
    SAMPLING("sampling", "采样推送"),
    
    BATCH("batch", "批量推送"),
    
    SKIP("skip", "跳过推送"),
    
    DELAYED_BATCH("delayed_batch", "延迟批量推送"),
    
    COUNTER_ONLY("counter_only", "仅计数推送"),
    
    AGGREGATE("aggregate", "聚合推送");

    private final String code;
    private final String description;

    TrafficPushStrategy(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static TrafficPushStrategy fromCode(String code) {
        if (code == null) {
            return REALTIME;
        }
        for (TrafficPushStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code)) {
                return strategy;
            }
        }
        return REALTIME;
    }
}
