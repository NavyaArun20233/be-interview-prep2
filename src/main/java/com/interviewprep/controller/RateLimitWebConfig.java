package com.interviewprep.controller;

import com.interviewprep.service.RateLimiterService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Applies {@link RateLimitInterceptor} to the quotes API only.
 *
 * <p>Lives in {@code controller} rather than {@code config} because it wires web-layer and service classes, which
 * {@code config} must not depend on. The {@link WebMvcConfigurer} is a {@code @Bean} instead of this class
 * implementing it, so {@code @WebMvcTest} slices of other controllers do not pick it up (and need no
 * {@link RateLimiterService}); slices that want it {@code @Import} this class.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitWebConfig {

    static final String RATE_LIMITED_PATHS = "/api/v1/quotes/**";

    @Bean
    WebMvcConfigurer rateLimitWebMvcConfigurer(RateLimiterService rateLimiterService) {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(rateLimiterService);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns(RATE_LIMITED_PATHS);
            }
        };
    }
}
