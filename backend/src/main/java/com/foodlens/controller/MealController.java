package com.foodlens.controller;
import com.foodlens.dto.MealDtos.*;
import com.foodlens.entity.*;
import com.foodlens.repository.MealRepository;
import com.foodlens.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/api/meals") @RequiredArgsConstructor
public class MealController {
    private final MealRepository meals;private final CurrentUserService current;
    @GetMapping public List<Meal> list(){return meals.findByUserIdOrderByCreatedAtDesc(current.get().getId());}
    @PostMapping public Meal save(@RequestBody SaveMealRequest r){
        if(r==null || r.items()==null || r.items().isEmpty() || r.items().size()>12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Save at least one valid food item.");
        Meal meal=Meal.builder().user(current.get()).mealType(r.mealType()==null?"Meal":r.mealType()).imageUrl(null).build();
        for(ItemRequest i:r.items()){
            if(i.foodName()==null || i.foodName().isBlank() || i.quantityGrams()<=0 || !Double.isFinite(i.quantityGrams()) ||
               !Double.isFinite(i.calories()) || i.calories()<0 || !Double.isFinite(i.protein()) || i.protein()<0 ||
               !Double.isFinite(i.carbs()) || i.carbs()<0 || !Double.isFinite(i.fat()) || i.fat()<0 ||
               !Double.isFinite(i.fiber()) || i.fiber()<0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Meal contains invalid nutrition values.");
            MealItem mi=MealItem.builder().meal(meal).foodName(i.foodName()).quantityGrams(i.quantityGrams()).calories(i.calories()).protein(i.protein()).carbs(i.carbs()).fat(i.fat()).fiber(i.fiber()).confidence(i.confidence()).build();
            meal.getItems().add(mi);meal.setTotalCalories(meal.getTotalCalories()+i.calories());meal.setTotalProtein(meal.getTotalProtein()+i.protein());meal.setTotalCarbs(meal.getTotalCarbs()+i.carbs());meal.setTotalFat(meal.getTotalFat()+i.fat());meal.setTotalFiber(meal.getTotalFiber()+i.fiber());
        }
        return meals.save(meal);
    }
}
