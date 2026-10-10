package com.foodlens.service;

import com.foodlens.entity.*;
import com.foodlens.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;

@Component @RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final UserRepository users; private final FoodRepository foods; private final RecipeRepository recipes; private final PantryItemRepository pantry; private final PasswordEncoder encoder;
    @Override public void run(String... args){
        User demo=users.findByEmailIgnoreCase("demo@foodlens.ai").orElseGet(()->users.save(User.builder().name("Demo User").email("demo@foodlens.ai").password(encoder.encode("Demo@123")).age(22).heightCm(175.0).weightKg(72.0).goal("Balanced").dietPreference("Omnivore").build()));
        seedFood("Rice", "Grain",130,2.7,28,0.3,0.4,0.1,1);
        seedFood("Chicken Curry","Main",180,18,6,9,1,2,420);
        seedFood("Dal","Legume",116,9,20,0.4,8,1.8,5);
        seedFood("Salad","Vegetable",40,2,8,0.5,3,3,35);
        seedFood("Idli","Breakfast",146,4.5,30,0.7,1.3,0.5,290);
        seedFood("Sambar","Curry",80,4,12,2.5,4,3,330);
        seedFood("Paneer","Dairy",265,18,3.5,20,0,1.2,22);
        seedFood("Egg","Protein",155,13,1.1,11,0,1.1,124);
        seedFood("Banana","Fruit",89,1.1,23,0.3,2.6,12,1);
        seedFood("Chicken","Protein",165,31,0,3.6,0,0,74);
        seedFood("Roti","Grain",297,9.6,46,7.5,7.0,2.0,330);
        seedFood("Naan","Grain",310,9,52,7,2,3,500);
        seedFood("Curd","Dairy",61,3.5,4.7,3.3,0,4.7,46);
        seedFood("Milk","Dairy",61,3.2,4.8,3.3,0,5,43);
        seedFood("Omelette","Protein",154,11,2,11,0,1,170);
        seedFood("Apple","Fruit",52,0.3,14,0.2,2.4,10,1);
        seedFood("Orange","Fruit",47,0.9,12,0.1,2.4,9,0);
        seedFood("Bread","Grain",265,9,49,3.2,2.7,5,490);
        seedFood("Potato","Vegetable",87,1.9,20,0.1,1.8,1,4);
        seedFood("Tomato","Vegetable",18,0.9,3.9,0.2,1.2,2.6,5);
        seedFood("Carrot","Vegetable",41,0.9,10,0.2,2.8,4.8,69);
        seedFood("Broccoli","Vegetable",35,2.4,7.2,0.4,3.3,1.4,33);
        seedFood("Pasta","Grain",158,5.8,31,0.9,1.8,0.6,1);
        seedFood("Pizza","Mixed",266,11,33,10,2.3,3.6,598);
        seedFood("Burger","Mixed",295,17,24,15,1.5,4,450);
        seedFood("French Fries","Snack",312,3.4,41,15,3.8,0.3,210);
        seedFood("Chana Masala","Legume",164,7.6,22,5.4,6.3,3.8,350);
        seedFood("Dosa","Breakfast",168,4,29,4,1.5,1,300);
        seedFood("Biryani","Mixed",190,7,25,7,1.4,1,320);
        seedFood("Fish","Protein",128,26,0,2.7,0,0,60);
        seedFood("Salmon","Protein",208,20,0,13,0,0,59);
        seedFood("Oats","Grain",71,2.5,12,1.5,1.7,0.3,1);
        seedFood("Peanut Butter","Spread",588,25,20,50,6,9,17);
        seedFood("Mixed Vegetables","Vegetable",55,2.5,10,0.5,3.5,4,35);
        // Approximate per-100g reference values for plated desserts.
        // Ingredient lists and exact portions cannot be established from a photo.
        seedFood("Chocolate Lava Cake","Dessert",400,6,48,21,2,33,200);
        seedFood("Chocolate Cake","Dessert",371,5,53,16,2,39,300);
        seedFood("Vanilla Ice Cream","Dessert",207,3.5,24,11,0,21,80);
        seedFood("Ice Cream","Dessert",207,3.5,24,11,0,21,80);
        seedFood("Chocolate Sauce","Dessert",280,2,65,2,2,48,75);
        seedFood("Watermelon","Fruit",30,0.6,8,0.2,0.4,6,1);
        if(recipes.count()==0){
            recipes.save(Recipe.builder().name("Chicken Rice Bowl").cuisine("Indian-inspired").dietType("Omnivore").prepTimeMinutes(15).cookTimeMinutes(25).servings(2).caloriesPerServing(560).proteinPerServing(38).ingredients(List.of("Chicken Curry 300 g","Rice 250 g","Salad 150 g")).instructions("Cook rice. Prepare chicken curry. Assemble with fresh salad. Adjust portions to your nutrition target.").build());
            recipes.save(Recipe.builder().name("Paneer Veg Bowl").cuisine("Indian").dietType("Vegetarian").prepTimeMinutes(12).cookTimeMinutes(18).servings(2).caloriesPerServing(480).proteinPerServing(27).ingredients(List.of("Paneer 250 g","Rice 180 g","Mixed vegetables 250 g")).instructions("Pan-sear paneer and vegetables. Serve over rice. Add herbs and lemon.").build());
            recipes.save(Recipe.builder().name("Idli Sambar Breakfast").cuisine("South Indian").dietType("Vegetarian").prepTimeMinutes(10).cookTimeMinutes(20).servings(2).caloriesPerServing(390).proteinPerServing(13).ingredients(List.of("Idli 6 pieces","Sambar 400 ml")).instructions("Steam idlis and serve hot with sambar. Add vegetables to the sambar for extra fiber.").build());
        }
        if(!recipes.existsByNameIgnoreCase("Egg and Tomato Scramble"))
            recipes.save(Recipe.builder().name("Egg and Tomato Scramble").cuisine("Quick breakfast").dietType("Vegetarian").prepTimeMinutes(5).cookTimeMinutes(10).servings(1).caloriesPerServing(240).proteinPerServing(15).ingredients(List.of("Eggs 2 pieces","Tomatoes 2 pieces","Milk 30 ml")).instructions("Beat eggs with milk. Dice tomatoes. Cook tomatoes briefly in a pan, add eggs and stir gently until set.").build());
        if(!recipes.existsByNameIgnoreCase("Chana Masala with Roti"))
            recipes.save(Recipe.builder().name("Chana Masala with Roti").cuisine("North Indian").dietType("Vegetarian").prepTimeMinutes(15).cookTimeMinutes(25).servings(2).caloriesPerServing(460).proteinPerServing(17).ingredients(List.of("Chana Masala 300 g","Roti 4 pieces","Tomatoes 2 pieces")).instructions("Simmer chickpeas with spices and tomato until thick. Heat rotis. Serve together with chopped tomato.").build());
        if(!recipes.existsByNameIgnoreCase("Salmon and Vegetable Bowl"))
            recipes.save(Recipe.builder().name("Salmon and Vegetable Bowl").cuisine("Contemporary").dietType("Omnivore").prepTimeMinutes(10).cookTimeMinutes(20).servings(2).caloriesPerServing(520).proteinPerServing(35).ingredients(List.of("Salmon 300 g","Rice 180 g","Mixed Vegetables 200 g")).instructions("Cook rice. Pan-sear salmon until cooked through. Steam vegetables and serve together.").build());
        if(pantry.findByUserIdOrderByExpiryDateAsc(demo.getId()).isEmpty()){
            pantry.save(PantryItem.builder().user(demo).name("Milk").quantity(1).unit("L").category("Dairy").expiryDate(LocalDate.now().plusDays(1)).build());
            pantry.save(PantryItem.builder().user(demo).name("Tomatoes").quantity(6).unit("pcs").category("Vegetable").expiryDate(LocalDate.now().plusDays(3)).build());
            pantry.save(PantryItem.builder().user(demo).name("Eggs").quantity(8).unit("pcs").category("Protein").expiryDate(LocalDate.now().plusDays(6)).build());
        }
    }
    private void seedFood(String n,String cat,double cal,double p,double c,double fat,double fiber,double sugar,double sodium){ if(foods.findByNameIgnoreCase(n).isEmpty()) foods.save(Food.builder().name(n).category(cat).caloriesPer100g(cal).proteinPer100g(p).carbsPer100g(c).fatPer100g(fat).fiberPer100g(fiber).sugarPer100g(sugar).sodiumMgPer100g(sodium).build()); }
}
