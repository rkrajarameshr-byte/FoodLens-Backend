package com.foodlens.repository;
import com.foodlens.entity.PlannedMeal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface PlannedMealRepository extends JpaRepository<PlannedMeal,Long>{
    List<PlannedMeal> findByUserIdAndDateBetweenOrderByDateAsc(Long userId,LocalDate start,LocalDate end);
}
