package com.campusbridge.security;

import com.campusbridge.entity.User;
import com.campusbridge.enums.UserRole;
import com.campusbridge.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserMapper users;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = users.selectById(Long.valueOf(jwt.getSubject()));
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())
                || (user.getRole() != UserRole.USER && user.getRole() != UserRole.ADMIN)) {
            throw new InvalidBearerTokenException("Account is unavailable");
        }
        UserPrincipal principal = new UserPrincipal(user);
        principal.eraseCredentials();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
