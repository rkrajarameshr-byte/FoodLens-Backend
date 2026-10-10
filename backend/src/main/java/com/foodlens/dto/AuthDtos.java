package com.foodlens.dto;
import jakarta.validation.constraints.*;
public final class AuthDtos {
    private AuthDtos(){}
    public record RegisterRequest(@NotBlank String name,@Email @NotBlank String email,@Size(min=6) String password,Integer age,Double heightCm,Double weightKg,String goal,String dietPreference){}
    public record LoginRequest(@Email @NotBlank String email,@NotBlank String password){}
    public record AuthResponse(String token, Long userId, String name, String email){}
}
