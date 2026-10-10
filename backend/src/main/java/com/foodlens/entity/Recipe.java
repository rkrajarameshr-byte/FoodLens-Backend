package com.foodlens.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Recipe {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false) private String name;
    private String cuisine;
    private String dietType;
    private int prepTimeMinutes;
    private int cookTimeMinutes;
    private int servings;
    private int caloriesPerServing;
    private double proteinPerServing;
    @Column(length=5000) private String instructions;
    @ElementCollection
    @CollectionTable(name="recipe_ingredients", joinColumns=@JoinColumn(name="recipe_id"))
    @Column(name="ingredient")
    @Builder.Default private List<String> ingredients = new ArrayList<>();
}
