package com.foodlens.dto;
import java.util.List;
public final class AnalysisDtos {
    private AnalysisDtos(){}
    public record AiDetectedItem(String name,double confidence,double estimatedGrams){}
    public record AiAnalysisResponse(List<AiDetectedItem> foods,String provider,String note){}
    public record RecalculateRequest(List<AiDetectedItem> foods,String provider,String note){}
    public record EnrichedItem(String name,double confidence,double estimatedGrams,double calories,double protein,double carbs,double fat,double fiber,boolean nutritionKnown){}
    public record FoodAnalysisResponse(List<EnrichedItem> foods,double totalCalories,double totalProtein,double totalCarbs,double totalFat,double totalFiber,int qualityScore,List<String> benefits,List<String> concerns,String provider,String note){}
}
