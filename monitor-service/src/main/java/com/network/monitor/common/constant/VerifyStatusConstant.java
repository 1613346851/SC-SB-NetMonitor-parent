package com.network.monitor.common.constant;

/**
 * 漏洞验证状态常量
 */
public class VerifyStatusConstant {

    /**
     * 未验证
     */
    public static final Integer UNVERIFIED = 0;

    /**
     * 已验证可利用
     */
    public static final Integer VERIFIED_EXPLOITABLE = 1;

    /**
     * 验证失败
     */
    public static final Integer VERIFICATION_FAILED = 2;

    /**
     * 误报
     */
    public static final Integer FALSE_POSITIVE = 3;
}
