package com.network.gateway.exception;

/**
 * 网关自定义业务异常
 * 用于处理网关业务逻辑中的异常情况
 *
 * @author network-monitor
 * @since 1.0.0
 */
public class GatewayBizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 构造函数
     *
     * @param message 错误信息
     */
    public GatewayBizException(String message) {
        super(message);
        this.errorCode = "GATEWAY_BIZ_ERROR";
    }

    /**
     * 构造函数
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public GatewayBizException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "GATEWAY_BIZ_ERROR";
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message 错误信息
     */
    public GatewayBizException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message 错误信息
     * @param cause 原始异常
     */
    public GatewayBizException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
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
     * 创建流量采集异常
     *
     * @param message 错误信息
     * @return GatewayBizException
     */
    public static GatewayBizException trafficCollectionError(String message) {
        return new GatewayBizException("TRAFFIC_COLLECTION_ERROR", message);
    }

    /**
     * 创建防御执行异常
     *
     * @param message 错误信息
     * @return GatewayBizException
     */
    public static GatewayBizException defenseExecutionError(String message) {
        return new GatewayBizException("DEFENSE_EXECUTION_ERROR", message);
    }

    /**
     * 创建缓存操作异常
     *
     * @param message 错误信息
     * @return GatewayBizException
     */
    public static GatewayBizException cacheOperationError(String message) {
        return new GatewayBizException("CACHE_OPERATION_ERROR", message);
    }

    /**
     * 创建配置异常
     *
     * @param message 错误信息
     * @return GatewayBizException
     */
    public static GatewayBizException configurationError(String message) {
        return new GatewayBizException("CONFIGURATION_ERROR", message);
    }

    /**
     * 创建服务间通信异常
     *
     * @param message 错误信息
     * @return GatewayBizException
     */
    public static GatewayBizException serviceCommunicationError(String message) {
        return new GatewayBizException("SERVICE_COMMUNICATION_ERROR", message);
    }
}