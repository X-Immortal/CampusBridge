package com.campusbridge.common;

import com.campusbridge.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    private final AuthContextUtil authContextUtil;

    public CurrentUserResolver(AuthContextUtil authContextUtil) {
        this.authContextUtil = authContextUtil;
    }

    public Long requireCurrentUserId(HttpServletRequest request) {
        Long userId = resolveOptionalUserId(request);
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED);
        }
        return userId;
    }

    public Long resolveOptionalUserId(HttpServletRequest request) {
        return authContextUtil.getCurrentUserId();
    }
}
