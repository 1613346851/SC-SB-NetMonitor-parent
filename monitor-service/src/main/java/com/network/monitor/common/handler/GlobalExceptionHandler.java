package com.network.monitor.common.handler;

import com.network.monitor.common.ApiResponse;
import com.network.monitor.common.exception.AuthRequiredException;
import com.network.monitor.common.exception.BizException;
import com.network.monitor.common.exception.ParamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    private static final String LOGIN_PAGE = "redirect:/login";

    @ExceptionHandler(AuthRequiredException.class)
    public Object handleAuthRequiredException(AuthRequiredException e, HttpServletRequest request) {
        logger.info("需要登录：path={}, message={}", request.getRequestURI(), e.getMessage());
        if (isPageRequest(request)) {
            String currentPath = request.getRequestURI();
            String queryString = request.getQueryString();
            String returnUrl = queryString != null ? currentPath + "?" + queryString : currentPath;
            return new ModelAndView(LOGIN_PAGE + "?returnUrl=" + java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8));
        }
        return ApiResponse.unauthorized(e.getMessage());
    }

    @ExceptionHandler(BizException.class)
    public Object handleBizException(BizException e, HttpServletRequest request) {
        logger.error("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        if (isPageRequest(request)) {
            return createErrorView(e.getMessage());
        }
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ParamException.class)
    public Object handleParamException(ParamException e, HttpServletRequest request) {
        logger.error("参数校验异常：message={}", e.getMessage());
        if (isPageRequest(request)) {
            return createErrorView(e.getMessage());
        }
        return ApiResponse.badRequest(e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public Object handleSecurityException(SecurityException e, HttpServletRequest request) {
        logger.warn("权限不足：path={}, message={}", request.getRequestURI(), e.getMessage());
        if (isPageRequest(request)) {
            ModelAndView mav = new ModelAndView("forbidden");
            mav.addObject("message", e.getMessage());
            mav.addObject("path", request.getRequestURI());
            return mav;
        }
        return ApiResponse.forbidden(e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logger.error("请求方法不支持：path={}, method={}, supportedMethods={}", 
            request.getRequestURI(), e.getMethod(), e.getSupportedHttpMethods());
        if (isPageRequest(request)) {
            return createErrorView("请求方法不支持: " + e.getMethod());
        }
        return ApiResponse.error(405, "请求方法不支持: " + e.getMethod() + " " + request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        String path = request.getRequestURI();
        logger.error("系统异常：path={}, method={}", path, request.getMethod(), e);
        
        if (isPageRequest(request)) {
            ModelAndView mav = new ModelAndView("error");
            mav.addObject("message", "系统繁忙，请稍后再试");
            mav.addObject("path", path);
            return mav;
        }
        return ApiResponse.error("系统繁忙，请稍后再试");
    }
    
    private boolean isPageRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }
    
    private ModelAndView createErrorView(String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("message", message);
        return mav;
    }
}
