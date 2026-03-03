package com.network.gateway.handler;

import com.network.gateway.exception.GatewayBizException;
import com.network.gateway.exception.GatewayRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 网关全局异常处理器
 * 处理网关过滤器、接口等所有组件的异常，返回标准化错误响应
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Component
@Order(-2) // 确保在默认异常处理器之前执行
public class GatewayGlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GatewayGlobalExceptionHandler.class);
    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    /**
     * 异常处理方法
     *
     * @param exchange ServerWebExchange对象
     * @param ex 异常对象
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        
        try {
            // 构建错误响应
            Map<String, Object> errorResponse = buildErrorResponse(ex);
            
            // 设置响应状态码
            HttpStatus status = determineHttpStatus(ex);
            response.setStatusCode(status);
            
            // 设置响应头
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            
            // 构建响应体
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = BUFFER_FACTORY.wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
            
            logger.error("网关异常处理: 状态码[{}] 错误类型[{}] 错误信息[{}]", 
                        status.value(), ex.getClass().getSimpleName(), ex.getMessage());
            
            return response.writeWith(Mono.just(buffer))
                    .then(response.setComplete());
                    
        } catch (Exception e) {
            logger.error("构建错误响应时发生异常", e);
            
            // 如果构建响应失败，返回最基本的错误信息
            String basicError = "{\"error\":\"Internal Server Error\",\"message\":\"网关处理异常\"}";
            DataBuffer buffer = BUFFER_FACTORY.wrap(basicError.getBytes(StandardCharsets.UTF_8));
            
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            
            return response.writeWith(Mono.just(buffer))
                    .then(response.setComplete());
        }
    }

    /**
     * 构建错误响应对象
     *
     * @param ex 异常对象
     * @return 错误响应Map
     */
    private Map<String, Object> buildErrorResponse(Throwable ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        if (ex instanceof GatewayRequestException requestEx) {
            // 处理网关请求异常
            errorResponse.put("error", "Gateway Request Error");
            errorResponse.put("message", requestEx.getMessage());
            errorResponse.put("errorCode", requestEx.getErrorCode());
            errorResponse.put("httpStatus", requestEx.getHttpStatus());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
        } else if (ex instanceof GatewayBizException bizEx) {
            // 处理网关业务异常
            errorResponse.put("error", "Gateway Business Error");
            errorResponse.put("message", bizEx.getMessage());
            errorResponse.put("errorCode", bizEx.getErrorCode());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
        } else if (ex instanceof ResponseStatusException statusEx) {
            // 处理Spring WebFlux的标准异常
            errorResponse.put("error", "Response Status Error");
            errorResponse.put("message", statusEx.getMessage());
            errorResponse.put("httpStatus", statusEx.getRawStatusCode());
            errorResponse.put("reason", statusEx.getReason());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
        } else {
            // 处理其他未预期的异常
            errorResponse.put("error", "Internal Server Error");
            errorResponse.put("message", "网关内部处理异常");
            errorResponse.put("errorCode", "GATEWAY_INTERNAL_ERROR");
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            // 在开发环境中可以显示详细错误信息
            if (isDevelopmentEnvironment()) {
                errorResponse.put("details", ex.getMessage());
                errorResponse.put("exceptionType", ex.getClass().getName());
            }
        }
        
        return errorResponse;
    }

    /**
     * 确定HTTP状态码
     *
     * @param ex 异常对象
     * @return HttpStatus
     */
    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof GatewayRequestException requestEx) {
            return HttpStatus.valueOf(requestEx.getHttpStatus());
        } else if (ex instanceof GatewayBizException) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        } else if (ex instanceof ResponseStatusException statusEx) {
            return HttpStatus.valueOf(statusEx.getRawStatusCode());
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * 判断是否为开发环境
     *
     * @return true表示开发环境
     */
    private boolean isDevelopmentEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "dev");
        return "dev".equals(profile) || "development".equals(profile);
    }

    /**
     * 记录异常详细信息
     *
     * @param ex 异常对象
     * @param exchange ServerWebExchange对象
     */
    private void logExceptionDetails(Throwable ex, ServerWebExchange exchange) {
        String requestInfo = String.format("方法=%s, URI=%s, IP=%s",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI(),
                getClientIpAddress(exchange));
        
        if (ex instanceof GatewayRequestException || ex instanceof GatewayBizException) {
            logger.warn("网关业务异常 {}: {}", requestInfo, ex.getMessage());
        } else {
            logger.error("网关系统异常 {}: {}", requestInfo, ex.getMessage(), ex);
        }
    }

    /**
     * 获取客户端IP地址
     *
     * @param exchange ServerWebExchange对象
     * @return 客户端IP
     */
    private String getClientIpAddress(ServerWebExchange exchange) {
        // 从请求头中获取真实IP
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // 从远程地址获取
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    /**
     * 构建详细的错误信息（用于开发调试）
     *
     * @param ex 异常对象
     * @return 详细错误信息
     */
    private String buildDetailedErrorMessage(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("异常类型: ").append(ex.getClass().getName()).append("\n");
        sb.append("异常信息: ").append(ex.getMessage()).append("\n");
        
        // 添加堆栈跟踪（仅开发环境）
        if (isDevelopmentEnvironment()) {
            sb.append("堆栈跟踪:\n");
            for (StackTraceElement element : ex.getStackTrace()) {
                sb.append("  at ").append(element.toString()).append("\n");
                if (sb.length() > 2000) { // 限制长度
                    sb.append("  ...更多堆栈信息已截断\n");
                    break;
                }
            }
        }
        
        return sb.toString();
    }
}