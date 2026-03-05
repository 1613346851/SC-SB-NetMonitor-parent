package com.network.monitor.common.constant;

/**
 * 攻击类型常量
 */
public class AttackTypeConstant {

    /**
     * SQL 注入
     */
    public static final String SQL_INJECTION = "SQL_INJECTION";

    /**
     * XSS 跨站脚本
     */
    public static final String XSS = "XSS";

    /**
     * 命令注入
     */
    public static final String COMMAND_INJECTION = "COMMAND_INJECTION";

    /**
     * DDoS 攻击
     */
    public static final String DDOS = "DDOS";

    /**
     * 路径遍历
     */
    public static final String PATH_TRAVERSAL = "PATH_TRAVERSAL";

    /**
     * 文件包含
     */
    public static final String FILE_INCLUDE = "FILE_INCLUDE";

    /**
     * CSRF 跨站请求伪造
     */
    public static final String CSRF = "CSRF";

    /**
     * SSRF 服务端请求伪造
     */
    public static final String SSRF = "SSRF";

    /**
     * 其他攻击类型
     */
    public static final String OTHER = "OTHER";
}
