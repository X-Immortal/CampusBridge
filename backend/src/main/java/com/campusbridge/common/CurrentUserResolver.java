package com.campusbridge.common;
import com.campusbridge.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    private final JwtUtil jwtUtil;
    private final String authHeader;
    private final String tokenPrefix;

    public CurrentUserResolver(
            JwtUtil jwtUtil,
            @Value("${jwt.header:Authorization}") String authHeader,
            @Value("${jwt.prefix:Bearer }") String tokenPrefix) {
        this.jwtUtil = jwtUtil;
        this.authHeader = authHeader;
        this.tokenPrefix = tokenPrefix;
    }

    private String resolveToken(HttpServletRequest request) {
        String headerValue = request.getHeader(authHeader);
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        if (headerValue.startsWith(tokenPrefix)) {
            return headerValue.substring(tokenPrefix.length()).trim();
        }
        return headerValue.trim();
    }

    public Long requireCurrentUserId(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        try {
            Long userId = jwtUtil.extractAccountId(token);
            if (userId == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return userId;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
