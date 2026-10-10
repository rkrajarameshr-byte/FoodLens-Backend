package com.foodlens.controller;
import com.foodlens.entity.*;
import com.foodlens.repository.*;
import com.foodlens.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
@RestController @RequestMapping("/api/planner") @RequiredArgsConstructor
public class PlannerController {
    private final RecipeRepository recipes;
    private final PlannedMealRepository plans;
    private final CurrentUserService current;
    public record PlanRequest(LocalDate date,String slot,Long recipeId){}
    @GetMapping("/recommend") public Map<String,Object> recommend(){
        String pref=current.get().getDietPreference();
        List<Recipe> r=(pref!=null && pref.equalsIgnoreCase("Vegetarian"))?recipes.findByDietTypeIgnoreCase("Vegetarian"):recipes.findAll();
        return Map.of("goal",Optional.ofNullable(current.get().getGoal()).orElse("Balanced"),"recommendations",r);
    }
    @GetMapping("/week") public List<PlannedMeal> week(@RequestParam(required=false) LocalDate start){
        LocalDate monday=(start==null?LocalDate.now():start).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return plans.findByUserIdAndDateBetweenOrderByDateAsc(current.get().getId(),monday,monday.plusDays(6));
    }
    @PostMapping public PlannedMeal add(@RequestBody PlanRequest r){
        if(r.date()==null || r.recipeId()==null || r.slot()==null || !List.of("Breakfast","Lunch","Dinner","Snack").contains(r.slot()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a date, meal and recipe.");
        Recipe recipe=recipes.findById(r.recipeId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Recipe not found."));
        return plans.save(PlannedMeal.builder().user(current.get()).date(r.date()).slot(r.slot()).recipeId(recipe.getId())
            .recipeName(recipe.getName()).caloriesPerServing(recipe.getCaloriesPerServing()).proteinPerServing(recipe.getProteinPerServing()).build());
    }
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id){
        PlannedMeal entry=plans.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Planned meal not found."));
        if(!entry.getUser().getId().equals(current.get().getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not your plan.");
        plans.delete(entry);
    }
}
