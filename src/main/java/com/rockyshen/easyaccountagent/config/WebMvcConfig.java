package com.rockyshen.easyaccountagent.config;

import com.rockyshen.easyaccountagent.auth.AuthHttpInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthHttpInterceptor authHttpInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authHttpInterceptor)
                .addPathPatterns(
                        "/api/accounts",
                        "/api/accounts/**",
                        "/api/actions",
                        "/api/actions/**",
                        "/api/types",
                        "/api/types/**",
                        "/api/dashboard",
                        "/api/dashboard/**");
    }
}
