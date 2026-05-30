package com.campusbridge.config;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

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
}
