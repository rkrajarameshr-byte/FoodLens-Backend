package com.foodlens.controller;
import com.foodlens.repository.MealRepository;
import com.foodlens.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api/dashboard") @RequiredArgsConstructor
public class DashboardController {
    private final MealRepository meals; private final CurrentUserService current;
    @GetMapping("/today") public Map<String,Object> today(){
        LocalDate d=LocalDate.now();var list=meals.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(current.get().getId(),d.atStartOfDay(),d.plusDays(1).atStartOfDay());
        double cal=list.stream().mapToDouble(m->m.getTotalCalories()).sum(),p=list.stream().mapToDouble(m->m.getTotalProtein()).sum(),carbs=list.stream().mapToDouble(m->m.getTotalCarbs()).sum(),fat=list.stream().mapToDouble(m->m.getTotalFat()).sum(),fiber=list.stream().mapToDouble(m->m.getTotalFiber()).sum();
        return Map.of("calories",round(cal),"protein",round(p),"carbs",round(carbs),"fat",round(fat),"fiber",round(fiber),"meals",list);
    }
    @GetMapping("/week") public List<Map<String,Object>> week(){
        LocalDate today=LocalDate.now();var list=meals.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(current.get().getId(),today.minusDays(6).atStartOfDay(),today.plusDays(1).atStartOfDay());
        List<Map<String,Object>> result=new ArrayList<>();
        for(int i=6;i>=0;i--){
            LocalDate date=today.minusDays(i);
            double calories=list.stream().filter(m->m.getCreatedAt().toLocalDate().equals(date)).mapToDouble(m->m.getTotalCalories()).sum();
            double protein=list.stream().filter(m->m.getCreatedAt().toLocalDate().equals(date)).mapToDouble(m->m.getTotalProtein()).sum();
            result.add(Map.of("date",date.toString(),"calories",round(calories),"protein",round(protein)));
        }
        return result;
    }
    private double round(double v){return Math.round(v*10.0)/10.0;}
}
