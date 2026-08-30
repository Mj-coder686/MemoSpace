package com.memospace.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    public static final String ADMIN_AUTHORITY = "ADMIN_SESSION";
    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-hours}") long expirationHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofHours(expirationHours);
    }

    public String issue(Long userId, String username) {
        return issue(userId, username, "USER");
    }

    public String issueAdmin(Long userId, String username) {
        return issue(userId, username, "ADMIN");
    }

    private String issue(Long userId, String username, String sessionType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("session_type", sessionType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public Long parseUserId(String token) {
        Session session = parseSession(token);
        if (session.admin()) throw new IllegalArgumentException("Administrator sessions cannot access user features");
        return session.userId();
    }

    public Session parseSession(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String type = claims.get("session_type", String.class);
        return new Session(Long.parseLong(claims.getSubject()), "ADMIN".equals(type));
    }

    public record Session(long userId, boolean admin) {}
}
