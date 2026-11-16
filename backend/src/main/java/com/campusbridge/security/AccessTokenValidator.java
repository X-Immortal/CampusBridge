package com.campusbridge.security;

import com.campusbridge.enums.TokenType;
import com.campusbridge.enums.UserRole;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object type = jwt.getClaims().get("tokenType");
        Object role = jwt.getClaims().get("role");
        // Older access tokens identify their purpose by role; refresh tokens do not carry one.
        boolean legacyAccess = type == null
                && (UserRole.USER.name().equals(role) || UserRole.ADMIN.name().equals(role));
        Object accountId = jwt.getClaims().get("accountId");
        if ((TokenType.ACCESS.name().equals(type) || legacyAccess)
                && accountId instanceof Number id
                && Long.toString(id.longValue()).equals(jwt.getSubject())
                && jwt.getExpiresAt() != null) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Access token with matching account identity required", null));
    }
}
