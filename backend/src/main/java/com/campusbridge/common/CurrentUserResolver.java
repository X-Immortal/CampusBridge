package com.campusbridge.common;

import com.campusbridge.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {
    private final AuthContextUtil authContextUtil;

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
