package com.network.target.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/lib/**")
                .addResourceLocations("classpath:/static/lib/")
                .setCachePeriod(3600);
        
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCachePeriod(3600);
        
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCachePeriod(3600);
        
        registry.addResourceHandler("/webfonts/**")
                .addResourceLocations("classpath:/static/webfonts/")
                .setCachePeriod(3600);
        
        registry.addResourceHandler("/test-files/**")
                .addResourceLocations("classpath:/static/test-files/")
                .setCachePeriod(0);
        
        registry.addResourceHandler("/target/lib/**")
                .addResourceLocations("classpath:/static/lib/")
                .setCachePeriod(3600);
        
        registry.addResourceHandler("/target/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCachePeriod(3600);
        
        registry.addResourceHandler("/target/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCachePeriod(3600);
        
        registry.addResourceHandler("/target/webfonts/**")
                .addResourceLocations("classpath:/static/webfonts/")
                .setCachePeriod(3600);
        
        registry.addResourceHandler("/target/test-files/**")
                .addResourceLocations("classpath:/static/test-files/")
                .setCachePeriod(0);
    }
}
