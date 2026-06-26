package com.example.gateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class StaticResourceConfig implements WebFluxConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vue SPA 静态资源（位于 classpath:/static/）
        // 注意：使用 hash 路由模式，不需要 server fallback
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}