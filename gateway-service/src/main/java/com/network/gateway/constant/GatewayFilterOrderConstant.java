package com.network.gateway.constant;

public class GatewayFilterOrderConstant {

    public static final int TRAFFIC_COLLECT_FILTER_ORDER = -100;

    public static final int IP_BLACKLIST_DEFENSE_FILTER_ORDER = 0;

    public static final int ATTACK_RULE_FILTER_ORDER = 5;

    public static final int REQUEST_RATE_LIMIT_FILTER_ORDER = 10;

    public static final int MALICIOUS_REQUEST_BLOCK_FILTER_ORDER = 15;

    private GatewayFilterOrderConstant() {
    }
}