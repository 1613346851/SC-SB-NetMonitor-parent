package com.network.target.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.io.EOFException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({EOFException.class, ClientAbortException.class})
    public void handleClientAbort(Exception e, HttpServletRequest request) {
        log.debug("客户端断开连接: {} {}", request.getMethod(), request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public void handleMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        if (e.getCause() instanceof ClientAbortException || 
            (e.getMessage() != null && e.getMessage().contains("EOFException"))) {
            log.debug("客户端断开连接，请求体读取中断: {} {}", request.getMethod(), request.getRequestURI());
        } else {
            log.warn("请求体解析失败: {} {} - {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        }
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.debug("缺少必需参数: {} - {}", e.getParameterName(), request.getRequestURI());
        Map<String, Object> result = new HashMap<>();
        result.put("code", HttpStatus.BAD_REQUEST.value());
        result.put("message", "缺少必需参数: " + e.getParameterName());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e, HttpServletRequest request) {
        String message = e.getMessage();
        if (message != null && (message.contains("Broken pipe") || 
            message.contains("EOFException") ||
            message.contains("ClientAbortException"))) {
            log.debug("客户端断开连接: {} {}", request.getMethod(), request.getRequestURI());
            return null;
        }
        
        if (e.getClass().getName().contains("TimeoutException") || 
            (message != null && message.contains("timeout"))) {
            log.warn("请求处理超时: {} {}", request.getMethod(), request.getRequestURI());
            Map<String, Object> result = new HashMap<>();
            result.put("code", HttpStatus.REQUEST_TIMEOUT.value());
            result.put("message", "请求处理超时");
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(result);
        }
        
        log.error("未处理的异常: {} {}", request.getMethod(), request.getRequestURI(), e);
        Map<String, Object> result = new HashMap<>();
        result.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
        result.put("message", "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
