package com.foodlens.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Meal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) @JsonIgnore private User user;
    private String mealType;
    private String imageUrl;
    private double totalCalories;
    private double totalProtein;
    private double totalCarbs;
    private double totalFat;
    private double totalFiber;
    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
    @OneToMany(mappedBy="meal", cascade=CascadeType.ALL, orphanRemoval=true)
    @Builder.Default private List<MealItem> items = new ArrayList<>();
}
