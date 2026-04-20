package com.network.monitor.common.handler;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.common.exception.BizException;
import com.network.monitor.common.exception.ParamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBizException(BizException e) {
        logger.error("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ParamException.class)
    public ApiResponse<?> handleParamException(ParamException e) {
        logger.error("参数校验异常：message={}", e.getMessage());
        return ApiResponse.badRequest(e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<?> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logger.error("请求方法不支持：path={}, method={}, supportedMethods={}", 
            request.getRequestURI(), e.getMethod(), e.getSupportedHttpMethods());
        return ApiResponse.error(405, "请求方法不支持: " + e.getMethod() + " " + request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e, HttpServletRequest request) {
        logger.error("系统异常：path={}, method={}", request.getRequestURI(), request.getMethod(), e);
        return ApiResponse.error("系统繁忙，请稍后再试");
    }
}
