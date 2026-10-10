package com.foodlens.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlens.entity.Meal;
import com.foodlens.entity.MealItem;
import com.foodlens.entity.PantryItem;
import com.foodlens.entity.Recipe;
import com.foodlens.entity.User;
import com.foodlens.repository.MealRepository;
import com.foodlens.repository.PantryItemRepository;
import com.foodlens.repository.RecipeRepository;
import com.foodlens.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/** Grounded chat: build the context on the server for the currently authenticated user. */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final PantryItemRepository pantry;
    private final RecipeRepository recipes;
    private final MealRepository meals;
    private final CurrentUserService current;
    private final ObjectMapper mapper;

    @Value("${foodlens.ai-service-url}")
    private String aiServiceUrl;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(12))
        .build();

    public record ChatTurn(String role, String content) {}
    public record ChatRequest(String message, List<ChatTurn> history, List<String> suggestedRecipeTitles) {}
    public record ChatResponse(String answer, String provider, JsonNode recipe) {}

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String question = request == null || request.message() == null ? "" : request.message().trim();
        if (question.isEmpty() || question.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a question (up to 2000 characters).");
        }

        User user = current.get();
        Long userId = user.getId();
        LocalDate today = LocalDate.now();
        var availablePantry = pantry.findByUserIdOrderByExpiryDateAsc(userId);
        var availableRecipes = recipes.findAll();
        var todayMeals = meals.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        var recentMeals = meals.findByUserIdOrderByCreatedAtDesc(userId);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("date", today.toString());
        context.put("dietPreference", safe(user.getDietPreference()));
        context.put("goal", safe(user.getGoal()));
        if (request.suggestedRecipeTitles() != null) {
            context.put("previousRecipeTitles", request.suggestedRecipeTitles().stream()
                .filter(t -> t != null && !t.isBlank() && t.length() <= 120)
                .limit(32).toList());
        }

        // Never trust client-supplied food data; context comes from repositories scoped to userId.
        List<Map<String, Object>> pantryData = new ArrayList<>();
        for (PantryItem item : availablePantry.stream().limit(40).toList()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", safe(item.getName()));
            entry.put("quantity", item.getQuantity());
            entry.put("unit", safe(item.getUnit()));
            entry.put("expiryDate", item.getExpiryDate() == null ? null : item.getExpiryDate().toString());
            pantryData.add(entry);
        }
        context.put("pantry", pantryData);
        context.put("pantryTruncated", availablePantry.size() > pantryData.size());

        List<Map<String, Object>> recipeData = new ArrayList<>();
        for (Recipe recipe : availableRecipes.stream().limit(12).toList()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", safe(recipe.getName()));
            entry.put("dietType", safe(recipe.getDietType()));
            entry.put("ingredients", recipe.getIngredients() == null ? List.of() : recipe.getIngredients().stream().limit(18).toList());
            entry.put("instructions", truncate(recipe.getInstructions(), 420));
            entry.put("minutes", recipe.getPrepTimeMinutes() + recipe.getCookTimeMinutes());
            entry.put("caloriesPerServing", recipe.getCaloriesPerServing());
            entry.put("proteinPerServing", recipe.getProteinPerServing());
            recipeData.add(entry);
        }
        context.put("recipes", recipeData);
        context.put("recipesTruncated", availableRecipes.size() > recipeData.size());

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("savedMealCount", todayMeals.size());
        totals.put("calories", round(todayMeals.stream().mapToDouble(Meal::getTotalCalories).sum()));
        totals.put("proteinGrams", round(todayMeals.stream().mapToDouble(Meal::getTotalProtein).sum()));
        totals.put("carbGrams", round(todayMeals.stream().mapToDouble(Meal::getTotalCarbs).sum()));
        totals.put("fatGrams", round(todayMeals.stream().mapToDouble(Meal::getTotalFat).sum()));
        context.put("todaySavedMeals", totals);

        List<Map<String, Object>> recent = new ArrayList<>();
        for (Meal meal : recentMeals.stream().limit(6).toList()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dateTime", meal.getCreatedAt() == null ? null : meal.getCreatedAt().toString());
            entry.put("mealType", safe(meal.getMealType()));
            entry.put("calories", round(meal.getTotalCalories()));
            entry.put("proteinGrams", round(meal.getTotalProtein()));
            entry.put("foods", meal.getItems() == null ? List.of() :
                meal.getItems().stream().limit(12).map(MealItem::getFoodName).toList());
            recent.add(entry);
        }
        context.put("recentSavedMeals", recent);
        context.put("recentMealsTruncated", recentMeals.size() > recent.size());

        List<ChatTurn> history = new ArrayList<>();
        if (request.history() != null) {
            var incoming = request.history();
            for (ChatTurn turn : incoming.subList(Math.max(0, incoming.size() - 8), incoming.size())) {
                if (turn != null && ("user".equals(turn.role()) || "assistant".equals(turn.role()))
                    && turn.content() != null && !turn.content().isBlank()) {
                    history.add(new ChatTurn(turn.role(), truncate(turn.content(), 1200)));
                }
            }
        }

        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("message", question, "history", history, "context", context));
            HttpRequest aiRequest = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl.replaceAll("/$", "") + "/chat"))
                .timeout(Duration.ofSeconds(225))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
            HttpResponse<String> response = HTTP.send(aiRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                String detail = "Local AI assistant unavailable (HTTP " + response.statusCode() + ").";
                if (response.statusCode() == 404) detail = "AI chat endpoint not found. Rebuild the ai-service container.";
                if (response.statusCode() == 503) detail = "Local AI unavailable. Check that Ollama is running and gemma3:4b is installed.";
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail);
            }
            JsonNode result = mapper.readTree(response.body());
            String answer = result.path("answer").asText("").trim();
            if (answer.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Local AI returned an empty answer.");
            }
            return new ChatResponse(answer, result.path("provider").asText("local-ai"),
                result.path("recipe").isObject() ? result.path("recipe") : null);
        } catch (HttpTimeoutException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Local AI took too long. Try a shorter question.", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot reach local AI. Check Docker and Ollama.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI request interrupted.", e);
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String truncate(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }
    private static double round(double number) { return Math.round(number * 10.0) / 10.0; }
}
