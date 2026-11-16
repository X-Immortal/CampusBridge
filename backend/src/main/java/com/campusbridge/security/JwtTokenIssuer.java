package com.campusbridge.security;

import com.campusbridge.enums.TokenType;
import com.campusbridge.enums.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JwtTokenIssuer {
    private final JwtEncoder encoder;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtTokenIssuer(JwtEncoder encoder,
                          @Value("${jwt.expiration}") long accessExpiration,
                          @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.encoder = encoder;
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String issueAccessToken(Long accountId, UserRole role) {
        return encode(claims(accountId, TokenType.ACCESS, accessExpiration).claim("role", role.name()).build());
    }

    public String issueRefreshToken(Long accountId) {
        return encode(claims(accountId, TokenType.REFRESH, refreshExpiration).build());
    }

    private JwtClaimsSet.Builder claims(Long accountId, TokenType type, long expiration) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder().subject(accountId.toString())
                .claim("accountId", accountId).claim("tokenType", type.name())
                .issuedAt(now).expiresAt(now.plusMillis(expiration));
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
