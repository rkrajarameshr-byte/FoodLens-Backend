package com.foodlens.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;
    public JwtService(@Value("${foodlens.jwt.secret}") String secret,@Value("${foodlens.jwt.expiration-ms}") long expirationMs){
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if(bytes.length < 32) throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }
    public String generate(String email){
        Date now=new Date();
        return Jwts.builder().subject(email).issuedAt(now).expiration(new Date(now.getTime()+expirationMs)).signWith(key).compact();
    }
    public String extractEmail(String token){ return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
    public boolean valid(String token){ try{ extractEmail(token); return true; }catch(JwtException|IllegalArgumentException e){ return false; } }
}
