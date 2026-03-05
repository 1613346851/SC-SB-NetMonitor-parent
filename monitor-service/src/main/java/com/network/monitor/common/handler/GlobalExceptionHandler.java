package com.network.monitor.common.handler;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.common.exception.BizException;
import com.network.monitor.common.exception.ParamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBizException(BizException e) {
        logger.error("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(ParamException.class)
    public ApiResponse<?> handleParamException(ParamException e) {
        logger.error("参数校验异常：message={}", e.getMessage());
        return ApiResponse.badRequest(e.getMessage());
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        logger.error("系统异常：", e);
        return ApiResponse.error("系统繁忙，请稍后再试");
    }
}
