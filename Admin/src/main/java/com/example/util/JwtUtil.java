package com.example.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {

    // Mock secret key (in production, store this in application.properties or a secure vault)
    private static final String SECRET_KEY = "your-very-long-and-secure-secret-key-here-32bytes!";
    private static final long EXPIRATION_TIME = 86400000; // 24 hours in milliseconds

    // Use SecretKeySpec for consistent key handling
    private static final SecretKeySpec SIGNING_KEY = new SecretKeySpec(
        SECRET_KEY.getBytes(), SignatureAlgorithm.HS256.getJcaName()
    );

    // Generate a mock JWT for testing
    public static String generateToken(Long userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SignatureAlgorithm.HS256, SIGNING_KEY)
                .compact();
    }

    // Decode and validate JWT
    public static Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(SIGNING_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            throw new RuntimeException("Invalid or expired JWT: " + e.getMessage());
        }
    }

    // Extract userId from JWT
    public static Long getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        return Long.valueOf(claims.get("userId").toString());
    }

    // Extract role from JWT
    public static String getRoleFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("role").toString();
    }
}