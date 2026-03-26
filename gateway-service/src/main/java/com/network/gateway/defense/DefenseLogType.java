package com.network.gateway.defense;

import lombok.Getter;

@Getter
public enum DefenseLogType {

    RATE_LIMIT("RATE_LIMIT", "限流", "请求频率超限", false),
    ADD_BLACKLIST("ADD_BLACKLIST", "加入黑名单", "IP加入黑名单封禁", true),
    REMOVE_BLACKLIST("REMOVE_BLACKLIST", "移除黑名单", "IP从黑名单移除", true),
    BLOCK_REQUEST("BLOCK_REQUEST", "拦截请求", "恶意请求被拦截", true),
    BLOCK_USER_AGENT("BLOCK_USER_AGENT", "拦截UA", "恶意User-Agent被拦截", true),
    BLOCK_URI("BLOCK_URI", "拦截URI", "恶意URI被拦截", true),
    COMPOSITE("COMPOSITE", "组合防御", "多种防御措施组合", true);

    private final String code;
    private final String name;
    private final String description;
    private final boolean unique;

    DefenseLogType(String code, String name, String description, boolean unique) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.unique = unique;
    }

    public static DefenseLogType fromCode(String code) {
        for (DefenseLogType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public boolean needsAggregation() {
        return !unique;
    }
}
