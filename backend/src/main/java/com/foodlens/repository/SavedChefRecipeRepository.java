package com.foodlens.repository;

import com.foodlens.entity.SavedChefRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SavedChefRecipeRepository extends JpaRepository<SavedChefRecipe,Long> {
    List<SavedChefRecipe> findByUserIdOrderByIdDesc(Long userId);
    Optional<SavedChefRecipe> findByIdAndUserId(Long id,Long userId);
    boolean existsByUserIdAndNormalizedName(Long userId,String normalizedName);
}
