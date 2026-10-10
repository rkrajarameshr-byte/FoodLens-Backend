package com.foodlens.repository;
import com.foodlens.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.time.LocalDateTime;
import java.util.List;
public interface MealRepository extends JpaRepository<Meal,Long>{
    @EntityGraph(attributePaths={"items"})
    List<Meal> findByUserIdOrderByCreatedAtDesc(Long userId);
    @EntityGraph(attributePaths={"items"})
    List<Meal> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, LocalDateTime start, LocalDateTime end);
}
