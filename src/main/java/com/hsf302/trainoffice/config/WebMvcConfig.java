package com.hsf302.trainoffice.config;

import com.hsf302.trainoffice.ratelimit.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final boolean rateLimitEnabled;

    public WebMvcConfig(AdminAuthInterceptor adminAuthInterceptor,
                        RateLimitInterceptor rateLimitInterceptor,
                        @Value("${app.rate-limit.enabled:true}") boolean rateLimitEnabled) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.rateLimitEnabled = rateLimitEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Đếm request trước mọi xử lý khác (bảng giới hạn: ratelimit/RateLimitRules).
        if (rateLimitEnabled) {
            registry.addInterceptor(rateLimitInterceptor).order(0);
        }

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**", "/admin")
                .order(1);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/forgot-password")
                .setViewName("auth/forgot-password");
        registry.addViewController("/profile/change-password")
                .setViewName("customer/change-password");
    }
}
