package com.example.gateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class StaticResourceConfig implements WebFluxConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/customer/**", "/agent/**", "/admin/**", "/**")
                .addResourceLocations("classpath:/static/customer/", "classpath:/static/agent/", "classpath:/static/admin/", "classpath:/static/");
    }
}