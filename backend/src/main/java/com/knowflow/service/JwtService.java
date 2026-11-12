package com.knowflow.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 负责签发和解析 JWT。
 * subject 存用户主键 id，这样过滤器里能直接拿到当前用户，不必把用户名放进 token。
 */
@Service
public class JwtService {

    public static final class ExpiredTokenException extends RuntimeException {
        public ExpiredTokenException() {
            super("登录已过期");
        }
    }

    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException() {
            super("无效的登录凭证");
        }
    }

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtService(
            @Value("${knowflow.jwt.secret}") String secret,
            @Value("${knowflow.jwt.expire-seconds}") long expireSeconds
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少 KNOWFLOW_JWT_SECRET");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    public String createToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException ex) {
            throw new ExpiredTokenException();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }
}
