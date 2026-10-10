package com.foodlens.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false) private String name;
    @Column(nullable=false, unique=true) private String email;
    @Column(nullable=false) private String password;
    private Integer age;
    private Double heightCm;
    private Double weightKg;
    private String goal;
    private String dietPreference;
    @Column(nullable=false) @Builder.Default private String role = "USER";
}
