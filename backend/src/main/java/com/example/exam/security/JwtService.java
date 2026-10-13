package com.example.exam.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes:120}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                // A token id makes one single token addressable, which is what logout
                // revocation keys on; without a jti there is nothing to revoke.
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("uid", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object value = parse(token).get("uid");
        return value instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(value));
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }

    /** JWT id (jti) of the token, or null for tokens issued before ids were added. */
    public String extractTokenId(String token) {
        return parse(token).getId();
    }

    /** Seconds left before the token expires; 0 once it has expired. */
    public long getRemainingSeconds(String token) {
        Date expiration = parse(token).getExpiration();
        long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000L;
        return Math.max(remaining, 0L);
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
