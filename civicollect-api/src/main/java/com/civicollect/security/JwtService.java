package com.civicollect.security;

import com.civicollect.config.AppProperties;
import com.civicollect.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = props.jwt().expirationMinutes();
    }

    public String generate(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    /** Valide la signature et l'expiration puis retourne les claims du JWT. */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** Valide la signature et l'expiration puis retourne l'identifiant utilisateur. */
    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }
}
