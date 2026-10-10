package com.foodlens.repository;
import com.foodlens.entity.ShoppingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem,Long>{ List<ShoppingItem> findByUserIdOrderByPurchasedAscIdDesc(Long userId); }
