package com.network.monitor.common.constant;

public class DefenseTypeConstant {

    public static final String BLACKLIST = "BLACKLIST";

    public static final String RATE_LIMIT = "RATE_LIMIT";

    public static final String BLOCK = "BLOCK";

    public static final String BLOCK_IP = BLACKLIST;

    public static final String BLOCK_REQUEST = BLOCK;

    public static final String IP_BLOCK = BLACKLIST;

    public static final String MALICIOUS_REQUEST = BLOCK;

    private DefenseTypeConstant() {
    }
}
