package com.network.gateway.config;

import com.network.gateway.constant.GatewayHttpConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate配置类
 * 配置用于跨服务调用的RestTemplate客户端
 *
 * @author network-monitor
 * @since 1.0.0
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 配置RestTemplate Bean
     *
     * @return RestTemplate实例
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 设置连接超时时间
        factory.setConnectTimeout(GatewayHttpConstant.MonitorService.CONNECT_TIMEOUT);
        
        // 设置读取超时时间
        factory.setReadTimeout(GatewayHttpConstant.MonitorService.READ_TIMEOUT);
        
        // 可选：配置代理（如果需要的话）
        // configureProxy(factory);
        
        return new RestTemplate(factory);
    }

    /**
     * 配置HTTP代理（可选）
     * 如果网关需要通过代理访问监控服务，可以启用此配置
     *
     * @param factory 请求工厂
     */
    private void configureProxy(SimpleClientHttpRequestFactory factory) {
        // 示例：配置代理服务器
        /*
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.host", 8080));
        factory.setProxy(proxy);
        */
    }

    /**
     * 配置带负载均衡的RestTemplate（如果使用服务发现）
     * 注意：当前项目使用固定地址，不需要负载均衡
     *
     * @return LoadBalancerRestTemplate实例
     */
    /*
    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }
    */

    /**
     * 配置自定义的RestTemplate（用于特定场景）
     *
     * @return 自定义RestTemplate实例
     */
    @Bean("customRestTemplate")
    public RestTemplate customRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 自定义超时设置
        factory.setConnectTimeout(3000);  // 3秒连接超时
        factory.setReadTimeout(5000);     // 5秒读取超时
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // 可以添加拦截器、消息转换器等
        // restTemplate.getInterceptors().add(new CustomClientHttpRequestInterceptor());
        
        return restTemplate;
    }

    /**
     * 配置高超时的RestTemplate（用于大数据传输）
     *
     * @return 高超时RestTemplate实例
     */
    @Bean("highTimeoutRestTemplate")
    public RestTemplate highTimeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 较长的超时时间
        factory.setConnectTimeout(10000);  // 10秒连接超时
        factory.setReadTimeout(30000);     // 30秒读取超时
        
        return new RestTemplate(factory);
    }
}