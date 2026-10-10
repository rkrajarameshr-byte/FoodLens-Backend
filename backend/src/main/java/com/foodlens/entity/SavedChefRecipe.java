package com.foodlens.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** A personal AI Chef idea; kept separate from the shared curated recipe catalog. */
@Entity
@Table(name="saved_chef_recipes", uniqueConstraints={
    @UniqueConstraint(name="uq_chef_user_title", columnNames={"user_id", "normalized_name"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavedChefRecipe {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id", nullable=false)
    private User user;
    @Column(nullable=false, length=130)
    private String name;
    @Column(name="normalized_name", nullable=false, length=130)
    private String normalizedName;
    @Column(nullable=false, columnDefinition="TEXT")
    private String ingredientsJson;
    @Column(nullable=false, columnDefinition="TEXT")
    private String instructions;
    private String cuisine;
    private String dietType;
    private int servings;
    private int prepTimeMinutes;
    private int cookTimeMinutes;
    private boolean quantityVerified;
    @Column(nullable=false, updatable=false)
    private Instant createdAt;
    @PrePersist void onCreate(){ if(createdAt==null) createdAt=Instant.now(); }
}
