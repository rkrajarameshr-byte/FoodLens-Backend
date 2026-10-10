package com.foodlens.dto;
import java.util.List;
public final class MealDtos {
    private MealDtos(){}
    public record ItemRequest(String foodName,double quantityGrams,double calories,double protein,double carbs,double fat,double fiber,double confidence){}
    public record SaveMealRequest(String mealType,String imageUrl,List<ItemRequest> items){}
}
