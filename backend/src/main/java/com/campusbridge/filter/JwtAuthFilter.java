package com.campusbridge.filter;

import com.campusbridge.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = getTokenFromRequest(request);
            if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                String subject = jwtUtil.extractUsername(token);
                if (StringUtils.hasText(subject) && jwtUtil.validateToken(token, subject)) {
                    String role = jwtUtil.extractRole(token);
                    Long userId = jwtUtil.extractAccountId(token);
                    if (userId == null) {
                        userId = Long.valueOf(subject);
                    }

                    String normalizedRole = StringUtils.hasText(role) ? role.toUpperCase() : "USER";
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + normalizedRole))
                            );
                    authToken.setDetails(Map.of("userId", userId, "role", normalizedRole));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    request.setAttribute("currentUserId", userId);
                    request.setAttribute("currentUserRole", normalizedRole);
                }
            }
        } catch (Exception e) {
            log.debug("JWT auth skipped: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
