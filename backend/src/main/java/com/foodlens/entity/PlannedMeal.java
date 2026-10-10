package com.foodlens.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlannedMeal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JsonIgnore private User user;
    private LocalDate date;
    private String slot;
    private Long recipeId;
    private String recipeName;
    private int caloriesPerServing;
    private double proteinPerServing;
}
