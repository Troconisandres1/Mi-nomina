package com.minomina.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET no está configurado. La aplicación NO puede arrancar así: sin un secreto " +
                "propio, cualquiera podría fabricar tokens válidos. Genera uno y expórtalo antes de " +
                "arrancar, por ejemplo en PowerShell:\n" +
                "  $env:JWT_SECRET = (-join ((48..57)+(65..90)+(97..122)|Get-Random -Count 48|%{[char]$_}))"
            );
        }
        // La clave debe tener al menos 32 bytes para HS256.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres (bytes UTF-8). El actual tiene " + keyBytes.length + ".");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generarToken(String userId, String email, String rol) {
        Date ahora = new Date();
        Date expira = new Date(ahora.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(expira)
                .signWith(key)
                .compact();
    }

    public String extraerUserId(String token) {
        return extraerClaim(token, Claims::getSubject);
    }

    public String extraerEmail(String token) {
        return extraerClaim(token, c -> c.get("email", String.class));
    }

    public String extraerRol(String token) {
        return extraerClaim(token, c -> c.get("rol", String.class));
    }

    public boolean esValido(String token) {
        try {
            Claims claims = todasLasClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T extraerClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(todasLasClaims(token));
    }

    private Claims todasLasClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
