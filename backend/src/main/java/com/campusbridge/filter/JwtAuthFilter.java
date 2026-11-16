package com.campusbridge.filter;

import com.campusbridge.entity.User;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long accountId = resolveAccessAccountId(token);
            if (accountId != null) {
                User user = userMapper.selectById(accountId);
                if (user != null && Integer.valueOf(1).equals(user.getEnabled())
                        && ("USER".equals(user.getRole()) || "ADMIN".equals(user.getRole()))) {
                    String role = user.getRole();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    accountId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    authentication.setDetails(Map.of("userId", accountId, "role", role));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    request.setAttribute("currentUserId", accountId);
                    request.setAttribute("currentUserRole", role);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private Long resolveAccessAccountId(String token) {
        try {
            return jwtUtil.extractAccessAccountId(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT authentication rejected: {}", ex.getMessage());
            return null;
        }
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
