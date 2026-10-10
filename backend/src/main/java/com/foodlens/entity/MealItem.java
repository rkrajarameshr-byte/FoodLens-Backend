package com.foodlens.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MealItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false) @JsonIgnore private Meal meal;
    private String foodName;
    private double quantityGrams;
    private double calories;
    private double protein;
    private double carbs;
    private double fat;
    private double fiber;
    private double confidence;
}
