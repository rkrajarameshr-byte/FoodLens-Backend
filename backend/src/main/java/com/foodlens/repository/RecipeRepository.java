package com.foodlens.repository;
import com.foodlens.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;
public interface RecipeRepository extends JpaRepository<Recipe,Long>{
    @Override @EntityGraph(attributePaths={"ingredients"}) List<Recipe> findAll();
    @Override @EntityGraph(attributePaths={"ingredients"}) Optional<Recipe> findById(Long id);
    @EntityGraph(attributePaths={"ingredients"}) List<Recipe> findByDietTypeIgnoreCase(String dietType);
    boolean existsByNameIgnoreCase(String name);
}
