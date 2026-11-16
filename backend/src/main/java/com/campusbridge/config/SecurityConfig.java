package com.campusbridge.config;

import com.campusbridge.enums.UserRole;

import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.ErrorResponse;
import com.campusbridge.security.JwtUserAuthenticationConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUserAuthenticationConverter jwtAuthenticationConverter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/api/v1/auth/**",
                            "/api/v1/home/**",
                            "/api/v1/content/**",
                            "/error"
                    ).permitAll();
                    auth.requestMatchers(HttpMethod.GET,
                            "/api/v1/posts/replied",
                            "/api/v1/posts/marked",
                            "/api/v1/users/me/profile"
                    ).authenticated();
                    auth.requestMatchers(HttpMethod.GET,
                            "/api/v1/tasks",
                            "/api/v1/tasks/*",
                            "/api/v1/posts",
                            "/api/v1/posts/*",
                            "/api/v1/users/*/profile",
                            "/api/v1/users/*/reviews"
                    ).permitAll();
                    auth.requestMatchers("/api/v1/admin/**").hasRole(UserRole.ADMIN.name());
                    auth.anyRequest().authenticated();
                })
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(ErrorCode.AUTH_001_UNAUTHORIZED.getHttpStatus().value());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(
                    ErrorCode.AUTH_001_UNAUTHORIZED, "Missing or invalid JWT token", null));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> {
            response.setStatus(ErrorCode.MOD_001_PERMISSION_DENIED.getHttpStatus().value());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(
                    ErrorCode.MOD_001_PERMISSION_DENIED,
                    ErrorCode.MOD_001_PERMISSION_DENIED.getDefaultMessage(), null));
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
