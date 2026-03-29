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
    COMPOSITE("COMPOSITE", "组合防御", "多种防御措施组合", true),
    MANUAL_BAN("MANUAL_BAN", "人工封禁", "管理员手动封禁IP", true),
    MANUAL_UNBAN("MANUAL_UNBAN", "人工解封", "管理员手动解封IP", true),
    TEMP_BAN("TEMP_BAN", "临时封禁", "临时封禁IP（自动解封）", true),
    STATE_RESET("STATE_RESET", "状态重置", "管理员重置IP状态", true),
    WHITELIST_ADD("WHITELIST_ADD", "加入白名单", "IP加入白名单", true),
    WHITELIST_REMOVE("WHITELIST_REMOVE", "移除白名单", "IP从白名单移除", true);

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

    public boolean isManualOperation() {
        return this == MANUAL_BAN || this == MANUAL_UNBAN || this == STATE_RESET;
    }

    public boolean isTemporaryAction() {
        return this == TEMP_BAN;
    }
}
