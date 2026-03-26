package com.erise.ai.backend.common.security;

import com.erise.ai.backend.common.config.EriseProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final EriseProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(EriseProperties properties) {
        this.properties = properties;
        if (properties.getJwt().getSecret() == null || properties.getJwt().getSecret().isBlank()) {
            throw new IllegalStateException("JWT secret must not be blank");
        }
        byte[] keyBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(CurrentUser user) {
        Instant expiresAt = Instant.now().plus(properties.getJwt().getAccessTokenExpireMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claims(Map.of(
                        "userId", user.userId(),
                        "username", user.username(),
                        "roleCode", user.roleCode()
                ))
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String createRefreshToken(CurrentUser user) {
        Instant expiresAt = Instant.now().plus(properties.getJwt().getRefreshTokenExpireDays(), ChronoUnit.DAYS);
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claims(Map.of(
                        "userId", user.userId(),
                        "username", user.username(),
                        "roleCode", user.roleCode(),
                        "type", "refresh"
                ))
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token)
                .getPayload();
        return new CurrentUser(
                Long.parseLong(String.valueOf(claims.get("userId"))),
                String.valueOf(claims.get("username")),
                String.valueOf(claims.get("roleCode"))
        );
    }

    public boolean isRefreshToken(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token)
                .getPayload();
        return "refresh".equals(claims.get("type"));
    }
}
