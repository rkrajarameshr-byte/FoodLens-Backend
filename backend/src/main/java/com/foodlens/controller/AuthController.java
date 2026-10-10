package com.foodlens.controller;

import com.foodlens.config.JwtService;
import com.foodlens.dto.AuthDtos.*;
import com.foodlens.entity.User;
import com.foodlens.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserRepository users; private final PasswordEncoder encoder; private final JwtService jwt;
    @PostMapping("/register") public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest r){
        if(users.existsByEmailIgnoreCase(r.email())) return ResponseEntity.badRequest().body("Email already registered");
        User u=users.save(User.builder().name(r.name()).email(r.email().toLowerCase()).password(encoder.encode(r.password())).age(r.age()).heightCm(r.heightCm()).weightKg(r.weightKg()).goal(r.goal()).dietPreference(r.dietPreference()).build());
        return ResponseEntity.ok(new AuthResponse(jwt.generate(u.getEmail()),u.getId(),u.getName(),u.getEmail()));
    }
    @PostMapping("/login") public ResponseEntity<?> login(@Valid @RequestBody LoginRequest r){
        User u=users.findByEmailIgnoreCase(r.email()).orElse(null);
        if(u==null || !encoder.matches(r.password(),u.getPassword())) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        return ResponseEntity.ok(new AuthResponse(jwt.generate(u.getEmail()),u.getId(),u.getName(),u.getEmail()));
    }
}
