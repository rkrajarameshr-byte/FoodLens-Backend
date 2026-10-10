package com.foodlens.repository;
import com.foodlens.entity.PantryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PantryItemRepository extends JpaRepository<PantryItem,Long>{ List<PantryItem> findByUserIdOrderByExpiryDateAsc(Long userId); }
