package com.network.gateway.constant;

/**
 * 网关HTTP常量类
 * 定义HTTP相关的状态码、请求头、响应格式等
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class GatewayHttpConstant {

    /**
     * HTTP状态码
     */
    public static class HttpStatus {
        /** 禁止访问 */
        public static final int FORBIDDEN = 403;
        /** 请求过多 */
        public static final int TOO_MANY_REQUESTS = 429;
        /** 服务不可用 */
        public static final int SERVICE_UNAVAILABLE = 503;
        /** 网关超时 */
        public static final int GATEWAY_TIMEOUT = 504;
    }

    /**
     * HTTP请求头
     */
    public static class Header {
        /** 真实IP地址头 */
        public static final String X_FORWARDED_FOR = "X-Forwarded-For";
        /** 真实主机头 */
        public static final String X_FORWARDED_HOST = "X-Forwarded-Host";
        /** 真实端口头 */
        public static final String X_FORWARDED_PORT = "X-Forwarded-Port";
        /** 真实协议头 */
        public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
        /** 客户端真实IP */
        public static final String X_REAL_IP = "X-Real-IP";
        /** 用户代理 */
        public static final String USER_AGENT = "User-Agent";
        /** 内容类型 */
        public static final String CONTENT_TYPE = "Content-Type";
        /** 内容长度 */
        public static final String CONTENT_LENGTH = "Content-Length";
    }

    /**
     * 内容类型
     */
    public static class ContentType {
        /** JSON格式 */
        public static final String APPLICATION_JSON = "application/json";
        /** HTML格式 */
        public static final String TEXT_HTML = "text/html";
        /** 纯文本 */
        public static final String TEXT_PLAIN = "text/plain";
    }

    /**
     * 字符编码
     */
    public static class Charset {
        /** UTF-8编码 */
        public static final String UTF_8 = "UTF-8";
    }

    /**
     * 监控服务相关配置
     */
    public static class MonitorService {
        /** 流量监控接口路径 */
        public static final String TRAFFIC_MONITOR_ENDPOINT = "http://localhost:9002/api/monitor/traffic";
        /** 防御日志接口路径 */
        public static final String DEFENSE_LOG_ENDPOINT = "http://localhost:9002/api/monitor/defense/log";
        /** 防御指令接口路径 */
        public static final String DEFENSE_COMMAND_ENDPOINT = "http://localhost:9002/api/monitor/defense/command";
        
        /** 连接超时时间（毫秒） */
        public static final int CONNECT_TIMEOUT = 5000;
        /** 读取超时时间（毫秒） */
        public static final int READ_TIMEOUT = 10000;
    }

    private GatewayHttpConstant() {
        // 私有构造函数，防止实例化
    }
}