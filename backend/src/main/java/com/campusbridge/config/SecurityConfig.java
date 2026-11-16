package com.campusbridge.config;

import com.campusbridge.filter.JwtAuthFilter;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.beans.factory.annotation.Autowired;

@Configuration
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/api/v1/auth/**",
                            "/api/v1/home/**",
                            "/api/v1/content/**",
                            "/api/v1/home/recommendations",
                            "/api/v1/content",
                            "/error",
                            "/",
                            "/index.html",
                            "/favicon.ico",
                            "/assets/**",
                            "/src/**",
                            "/*.js",
                            "/*.css",
                            "/*.png",
                            "/*.svg",
                            "/*.jpg"
                    ).permitAll();
                    auth.requestMatchers(HttpMethod.GET,
                            "/api/v1/tasks",
                            "/api/v1/tasks/*",
                            "/api/v1/posts",
                            "/api/v1/posts/*",
                            "/api/v1/users/*/profile",
                            "/api/v1/users/*/reviews"
                    ).permitAll();
                    auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                })
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(
                    ErrorCode.AUTH_001_UNAUTHORIZED, "Missing or invalid JWT token", null));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(
                    ErrorCode.MOD_001_PERMISSION_DENIED,
                    ErrorCode.MOD_001_PERMISSION_DENIED.getDefaultMessage(), null));
        };
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("No in-memory user: " + username);
        };
    }
}
