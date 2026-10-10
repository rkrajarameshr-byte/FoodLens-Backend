package com.foodlens.controller;

import com.foodlens.entity.*;
import com.foodlens.repository.*;
import com.foodlens.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/recipes") @RequiredArgsConstructor
public class RecipeController {
    private final RecipeRepository recipes;
    private final PantryItemRepository pantry;
    private final ShoppingItemRepository shopping;
    private final CurrentUserService current;

    @GetMapping public List<Recipe> all(){ return recipes.findAll(); }

    private Recipe getRecipe(Long id){return recipes.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Recipe not found."));}
    private static String ingredientName(String raw){
        return raw.trim().replaceFirst("(?i)\\s+\\d+(?:\\.\\d+)?\\s*(?:g|kg|ml|l|pcs|pieces|cups?|tbsp|tsp)?$","").trim();
    }
    private static String norm(String raw){
        String v=raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]","").replaceAll("\\s+"," ");
        return v.endsWith("s") && !v.endsWith("ss") ? v.substring(0,v.length()-1) : v;
    }
    private List<String> missing(Recipe recipe){
        Set<String> available=pantry.findByUserIdOrderByExpiryDateAsc(current.get().getId()).stream()
            .filter(x->x.getQuantity()>0).map(x->norm(x.getName())).collect(Collectors.toSet());
        return recipe.getIngredients().stream().map(RecipeController::ingredientName)
            .filter(x->!available.contains(norm(x))).distinct().toList();
    }
    @GetMapping("/{id}/match") public Map<String,Object> match(@PathVariable Long id){
        Recipe recipe=getRecipe(id);List<String> missing=missing(recipe);
        return Map.of("recipeId",id,"missingIngredients",missing,
            "availableIngredients",recipe.getIngredients().stream().map(RecipeController::ingredientName)
                .filter(x->!missing.contains(x)).toList());
    }
    @PostMapping("/{id}/shopping") public Map<String,Object> addMissing(@PathVariable Long id){
        List<String> missing=missing(getRecipe(id));
        Set<String> existing=shopping.findByUserIdOrderByPurchasedAscIdDesc(current.get().getId()).stream()
            .filter(x->!x.isPurchased()).map(x->norm(x.getName())).collect(Collectors.toSet());
        int added=0;
        for(String name:missing){
            if(existing.add(norm(name))){
                shopping.save(ShoppingItem.builder().user(current.get()).name(name).quantity(1).unit("item").purchased(false).build());
                added++;
            }
        }
        return Map.of("added",added,"missingIngredients",missing);
    }
}
