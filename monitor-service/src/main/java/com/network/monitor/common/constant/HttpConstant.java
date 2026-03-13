package com.network.monitor.common.constant;

/**
 * HTTP 相关常量
 */
public class HttpConstant {

    /**
     * 成功状态码
     */
    public static final int HTTP_OK = 200;

    /**
     * 创建成功状态码
     */
    public static final int HTTP_CREATED = 201;

    /**
     * 无授权状态码
     */
    public static final int HTTP_UNAUTHORIZED = 401;

    /**
     * 禁止访问状态码
     */
    public static final int HTTP_FORBIDDEN = 403;

    /**
     * 未找到状态码
     */
    public static final int HTTP_NOT_FOUND = 404;

    /**
     * 服务器错误状态码
     */
    public static final int HTTP_INTERNAL_ERROR = 500;

    /**
     * GET 请求方法
     */
    public static final String HTTP_METHOD_GET = "GET";

    /**
     * POST 请求方法
     */
    public static final String HTTP_METHOD_POST = "POST";

    /**
     * PUT 请求方法
     */
    public static final String HTTP_METHOD_PUT = "PUT";

    /**
     * DELETE 请求方法
     */
    public static final String HTTP_METHOD_DELETE = "DELETE";

    /**
     * Content-Type: application/json
     */
    public static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * Content-Type: application/x-www-form-urlencoded
     */
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

    /**
     * Content-Type: multipart/form-data
     */
    public static final String CONTENT_TYPE_MULTIPART = "multipart/form-data";

    /**
     * 网关服务 API 基础路径
     */
    public static final String GATEWAY_API_BASE_URL = "http://localhost:8080";

    /**
     * 网关防御指令接收接口
     */
    public static final String GATEWAY_DEFENSE_ENDPOINT = "/api/gateway/defense/command";

    /**
     * 网关日志同步接口
     */
    public static final String GATEWAY_LOG_ENDPOINT = "/api/gateway/defense/log/sync";
}
