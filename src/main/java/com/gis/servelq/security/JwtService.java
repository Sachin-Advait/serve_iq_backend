package com.gis.servelq.security;

import com.gis.servelq.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class JwtService {

    private final String secret;
    private final long expirationMinutes;
    private SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expirationMinutes:720}") long expirationMinutes) {
        this.secret = secret;
        this.expirationMinutes = expirationMinutes;
    }

    @PostConstruct
    void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 needs a 256 bit key. Fail at startup rather than let someone
            // deploy with "changeme" as the signing key.
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters (got " + bytes.length + ")");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("email", user.getEmail());
        if (user.getBranchId() != null) {
            claims.put("branchId", user.getBranchId());
        }
        if (user.getCounterId() != null) {
            claims.put("counterId", user.getCounterId());
        }

        return Jwts.builder()
                .claims(claims)
                .subject(user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /**
     * Claims if the token is valid, null if it is expired, tampered with or
     * otherwise unusable. Callers treat null as "not authenticated"; we do not
     * report the reason back to the client.
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getMessage());
            return null;
        }
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
