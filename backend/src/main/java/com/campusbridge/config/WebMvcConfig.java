package com.campusbridge.config;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA fallback: when a 404 occurs on a non-API path, serve index.html
 * so Vue Router (history mode) can handle client-side routing.
 */
@Configuration
public class WebMvcConfig {

    @Bean
    public ErrorPageRegistrar spaErrorPageRegistrar() {
        return registry -> registry.addErrorPages(
                new ErrorPage(HttpStatus.NOT_FOUND, "/index.html"));
    }

    /** /ai-demo 目录本身没有 index 映射（会被 SPA fallback 吃掉），直接跳到页面。 */
    @Bean
    public WebMvcConfigurer aiDemoRedirect() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addRedirectViewController("/ai-demo", "/ai-demo/index.html");
                registry.addRedirectViewController("/ai-demo/", "/ai-demo/index.html");
            }
        };
    }
}
