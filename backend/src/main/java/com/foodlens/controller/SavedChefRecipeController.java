package com.foodlens.controller;

import com.foodlens.entity.PantryItem;
import com.foodlens.entity.SavedChefRecipe;
import com.foodlens.entity.User;
import com.foodlens.repository.PantryItemRepository;
import com.foodlens.repository.SavedChefRecipeRepository;
import com.foodlens.service.CurrentUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Save curated pantry recipe ideas per user, never globally or as a fabricated nutrition entry. */
@RestController
@RequestMapping("/api/assistant/saved-recipes")
@RequiredArgsConstructor
public class SavedChefRecipeController {
    private final SavedChefRecipeRepository repository;
    private final PantryItemRepository pantry;
    private final CurrentUserService current;
    private final ObjectMapper mapper;

    public record Ingredient(@NotBlank String name, @NotNull @DecimalMin("0.001") Double quantity,
                             @NotBlank String unit) {}
    public record SaveRequest(@NotBlank String name,
                              @NotEmpty List<@Valid Ingredient> ingredients,
                              @NotBlank String instructions,
                              Integer servings,Integer prepTimeMinutes,Integer cookTimeMinutes,
                              String cuisine,String dietType,Boolean quantityVerified,String source) {}
    public record ChefRecipeView(Long id,String name,List<Ingredient> ingredients,String instructions,
                                 int servings,int prepTimeMinutes,int cookTimeMinutes,
                                 String cuisine,String dietType,boolean quantityVerified,
                                 String createdAt) {}

    private static final Set<String> UNITS=Set.of("pcs","g","kg","ml","l");
    private static final Map<String,String> ALIASES=Map.ofEntries(
        Map.entry("eggs","egg"),Map.entry("tomatoes","tomato"),
        Map.entry("potatoes","potato"),Map.entry("onions","onion"),
        Map.entry("bread slices","bread"),Map.entry("sliced bread","bread"),
        Map.entry("cooking oil","oil"),Map.entry("vegetable oil","oil"),
        Map.entry("whole milk","milk"),Map.entry("white sugar","sugar"));

    private static String norm(String s){
        String x=(s==null?"":s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]+"," ").replaceAll("\\s+"," ").trim();
        return ALIASES.getOrDefault(x,x);
    }
    private static String trimmed(String v,int max,String name){
        if(v==null || v.isBlank() || v.strip().length()>max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,name+" is required (max "+max+" characters).");
        return v.strip();
    }
    private static String optional(String value,int max){
        return value==null?"":value.strip().substring(0,Math.min(value.strip().length(),max));
    }
    private static String baseUnit(String u){
        return switch(u){case "kg"->"g";case "l"->"ml";default->u;};
    }
    private static double baseAmount(double x,String u){
        return (u.equals("kg") || u.equals("l"))?x*1000:x;
    }
    private boolean hasAmounts(List<Ingredient> ingredients,Long userId){
        var stored=new HashMap<String,Double>();
        for(PantryItem i:pantry.findByUserIdOrderByExpiryDateAsc(userId)){
            if(i.getExpiryDate()!=null && i.getExpiryDate().isBefore(LocalDate.now()))continue;
            String unit=(i.getUnit()==null?"":i.getUnit().toLowerCase(Locale.ROOT).strip());
            if(!UNITS.contains(unit) || i.getQuantity()<=0)continue;
            String key=norm(i.getName())+":"+baseUnit(unit);
            stored.merge(key,baseAmount(i.getQuantity(),unit),Double::sum);
        }
        var used=new HashMap<String,Double>();
        for(Ingredient ingredient:ingredients){
            String unit=ingredient.unit().toLowerCase(Locale.ROOT).strip();
            String key=norm(ingredient.name())+":"+baseUnit(unit);
            used.merge(key,baseAmount(ingredient.quantity(),unit),Double::sum);
            if(used.get(key)>stored.getOrDefault(key,0.0)+0.0001)return false;
        }
        return true;
    }
    private List<Ingredient> ingredients(SavedChefRecipe r){
        try{return mapper.readValue(r.getIngredientsJson(),new TypeReference<List<Ingredient>>(){});}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Saved recipe data could not be read.");}
    }
    private ChefRecipeView view(SavedChefRecipe r){
        return new ChefRecipeView(r.getId(),r.getName(),ingredients(r),r.getInstructions(),
            r.getServings(),r.getPrepTimeMinutes(),r.getCookTimeMinutes(),
            r.getCuisine(),r.getDietType(),r.isQuantityVerified(),r.getCreatedAt().toString());
    }
    @GetMapping
    public List<ChefRecipeView> all(){
        return repository.findByUserIdOrderByIdDesc(current.get().getId())
            .stream().map(this::view).toList();
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChefRecipeView save(@Valid @RequestBody SaveRequest request){
        // The assistant may only save ideas explicitly sourced from the trusted
        // curated pantry templates; arbitrary free-text Ollama recipes are excluded.
        if(!"foodlens:curated-pantry".equals(request.source()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only verified-source pantry suggestions can be saved.");
        String title=trimmed(request.name(),120,"Recipe name");
        String steps=trimmed(request.instructions(),4000,"Instructions");
        if(request.ingredients().size()>16)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Maximum 16 ingredients per recipe.");
        List<Ingredient> cleaned=new ArrayList<>();
        for(Ingredient i:request.ingredients()){
            if(i==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ingredient is missing.");
            String ingredient=trimmed(i.name(),80,"Ingredient name");
            String unit=i.unit()==null?"":i.unit().strip().toLowerCase(Locale.ROOT);
            if(!UNITS.contains(unit) || i.quantity()==null || !Double.isFinite(i.quantity()) || i.quantity()<=0 || i.quantity()>10000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid ingredient amount/unit.");
            cleaned.add(new Ingredient(ingredient,i.quantity(),unit));
        }
        int servings=request.servings()==null?1:request.servings();
        int prep=request.prepTimeMinutes()==null?0:request.prepTimeMinutes();
        int cook=request.cookTimeMinutes()==null?0:request.cookTimeMinutes();
        if(servings<1||servings>20||prep<0||prep>240||cook<0||cook>480)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid servings or preparation time.");
        User user=current.get();
        String key=norm(title);
        if(repository.existsByUserIdAndNormalizedName(user.getId(),key))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This recipe is already saved.");
        boolean verified=hasAmounts(cleaned,user.getId());
        try{
            SavedChefRecipe created=repository.save(SavedChefRecipe.builder()
                .user(user).name(title).normalizedName(key)
                .ingredientsJson(mapper.writeValueAsString(cleaned)).instructions(steps)
                .servings(servings).prepTimeMinutes(prep).cookTimeMinutes(cook)
                .cuisine(optional(request.cuisine(),80)).dietType(optional(request.dietType(),50))
                .quantityVerified(verified).build());
            return view(created);
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid recipe ingredients.",e);
        }
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id){
        SavedChefRecipe item=repository.findByIdAndUserId(id,current.get().getId())
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Saved recipe not found."));
        repository.delete(item);
    }
}
