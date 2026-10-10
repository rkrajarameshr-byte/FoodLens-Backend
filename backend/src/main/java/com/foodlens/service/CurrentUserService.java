package com.foodlens.service;
import com.foodlens.entity.User;
import com.foodlens.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository users;
    public User get(){
        String email=SecurityContextHolder.getContext().getAuthentication().getName();
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }
}
