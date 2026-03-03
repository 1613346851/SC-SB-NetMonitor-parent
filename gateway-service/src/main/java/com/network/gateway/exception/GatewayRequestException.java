package com.network.gateway.exception;

/**
 * 网关请求异常
 * 用于处理网关请求处理过程中的异常
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class GatewayRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * HTTP状态码
     */
    private final int httpStatus;

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 构造函数
     *
     * @param httpStatus HTTP状态码
     * @param message 错误信息
     */
    public GatewayRequestException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = "GATEWAY_REQUEST_ERROR";
    }

    /**
     * 构造函数
     *
     * @param httpStatus HTTP状态码
     * @param message 错误信息
     * @param cause 原始异常
     */
    public GatewayRequestException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = "GATEWAY_REQUEST_ERROR";
    }

    /**
     * 构造函数
     *
     * @param httpStatus HTTP状态码
     * @param errorCode 错误码
     * @param message 错误信息
     */
    public GatewayRequestException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    /**
     * 构造函数
     *
     * @param httpStatus HTTP状态码
     * @param errorCode 错误码
     * @param message 错误信息
     * @param cause 原始异常
     */
    public GatewayRequestException(int httpStatus, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    /**
     * 获取HTTP状态码
     *
     * @return HTTP状态码
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 创建400 Bad Request异常
     *
     * @param message 错误信息
     * @return GatewayRequestException
     */
    public static GatewayRequestException badRequest(String message) {
        return new GatewayRequestException(400, "BAD_REQUEST", message);
    }

    /**
     * 创建401 Unauthorized异常
     *
     * @param message 错误信息
     * @return GatewayRequestException
     */
    public static GatewayRequestException unauthorized(String message) {
        return new GatewayRequestException(401, "UNAUTHORIZED", message);
    }

    /**
     * 创建403 Forbidden异常
     *
     * @param message 错误信息
     * @return GatewayRequestException
     */
    public static GatewayRequestException forbidden(String message) {
        return new GatewayRequestException(403, "FORBIDDEN", message);
    }

    /**
     * 创建404 Not Found异常
     *
     * @param message 错误信息
     * @return GatewayRequestException
     */
    public static GatewayRequestException notFound(String message) {
        return new GatewayRequestException(404, "NOT_FOUND", message);
    }

    /**
     * 创建429 Too Many Requests异常
     *
     * @param message 错误信息
     * @return GatewayRequestException
     */
    public static GatewayRequestException tooManyRequests(String message) {
        return new GatewayRequestException(429, "TOO_MANY_REQUESTS", message);
    }

    /**
     * 创建500 Internal Server Error异常
     *
     * @param message 错误信息
     * @return GatewayRequestException
     */
    public static GatewayRequestException internalServerError(String message) {
        return new GatewayRequestException(500, "INTERNAL_SERVER_ERROR", message);
    }

    /**
     * 创建502 Bad Gateway异常
     *
     * @param message 错误信息
     * @return GatewayRequestException
     */
    public static GatewayRequestException badGateway(String message) {
        return new GatewayRequestException(502, "BAD_GATEWAY", message);
    }

    /**
     * 创建503 Service Unavailable异常
     *
     * @param message 错误信息
     * @return GatewayRequestException
     */
    public static GatewayRequestException serviceUnavailable(String message) {
        return new GatewayRequestException(503, "SERVICE_UNAVAILABLE", message);
    }

    /**
     * 创建504 Gateway Timeout异常
     *
     * @param message 错误信息
     * @return GatewayRequestException
     */
    public static GatewayRequestException gatewayTimeout(String message) {
        return new GatewayRequestException(504, "GATEWAY_TIMEOUT", message);
    }
}