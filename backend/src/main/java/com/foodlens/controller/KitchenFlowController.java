package com.foodlens.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlens.entity.*;
import com.foodlens.repository.*;
import com.foodlens.service.CurrentUserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Shared, authenticated workflow: Recipes/Planner -> Shopping -> Pantry, and Dashboard status. */
@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
public class KitchenFlowController {
    private final RecipeRepository recipes;
    private final SavedChefRecipeRepository chefRecipes;
    private final PantryItemRepository pantry;
    private final ShoppingItemRepository shopping;
    private final PlannedMealRepository plans;
    private final MealRepository meals;
    private final CurrentUserService current;
    private final ObjectMapper mapper;

    private static final Pattern QUANTITY = Pattern.compile(
        "^(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|l|pcs|pieces|item|items)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Map<String,String> ALIASES = Map.ofEntries(
        Map.entry("eggs","egg"),Map.entry("tomatoes","tomato"),Map.entry("potatoes","potato"),
        Map.entry("onions","onion"),Map.entry("bread slices","bread"),Map.entry("sliced bread","bread"),
        Map.entry("whole milk","milk"),Map.entry("cooking oil","oil"),Map.entry("vegetables","vegetable"));

    private static String norm(String s) {
        String x=(s==null?"":s).trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]+"," ").replaceAll("\\s+"," ").trim();
        x=ALIASES.getOrDefault(x,x);
        if(x.endsWith("s") && !x.endsWith("ss") && !x.endsWith("us")) x=x.substring(0,x.length()-1);
        return x;
    }
    private static String unit(String raw) {
        if(raw==null)return "";
        return switch(raw.trim().toLowerCase(Locale.ROOT)) {
            case "pieces", "piece", "pcs", "pc" -> "pcs";
            case "item", "items" -> "item";
            case "g", "kg", "ml", "l" -> raw.trim().toLowerCase(Locale.ROOT);
            default -> "";
        };
    }
    private static String baseUnit(String u){return u.equals("kg")?"g":u.equals("l")?"ml":u;}
    private static double baseQty(double n,String u){return (u.equals("kg")||u.equals("l"))?n*1000:n;}
    private static double tidy(double n){return Math.round(n*100.0)/100.0;}
    private record Required(String name, double quantity, String unit) {}
    private record Stock(String name,double quantity,String unit) {}

    private static Required parseCatalog(String s,double ratio){
        if(s==null || s.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Recipe contains an empty ingredient.");
        Matcher m=QUANTITY.matcher(s.strip());
        if(!m.matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Recipe ingredient needs a measurable amount: "+s);
        String u=unit(m.group(3));
        if(u.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported recipe unit.");
        double qty=Double.parseDouble(m.group(2))*ratio;
        if(!Double.isFinite(qty)||qty<=0||qty>100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid recipe quantity.");
        return new Required(m.group(1).strip(),qty,u);
    }
    private List<Required> catalogNeeds(Recipe r,int servings){
        if(servings<1||servings>12)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Servings must be between 1 and 12.");
        double ratio=(double)servings/Math.max(1,r.getServings());
        return r.getIngredients().stream().map(x->parseCatalog(x,ratio)).toList();
    }
    private List<Required> chefNeeds(SavedChefRecipe recipe){
        try {
            JsonNode root=mapper.readTree(recipe.getIngredientsJson());
            if(!root.isArray())throw new IllegalArgumentException("Ingredients must be a list.");
            List<Required> found=new ArrayList<>();
            for(JsonNode i:root){
                String name=i.path("name").asText("").strip();
                String rawUnit=unit(i.path("unit").asText(""));
                double qty=i.path("quantity").asDouble(-1);
                if(name.isBlank() || rawUnit.isEmpty() || !Double.isFinite(qty) || qty<=0 || qty>100000)
                    throw new IllegalArgumentException("Invalid saved recipe ingredient.");
                found.add(new Required(name,qty,rawUnit));
            }
            if(found.isEmpty())throw new IllegalArgumentException("No ingredients.");
            return found;
        } catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "This saved recipe has unrecognized quantities. Edit it before adding groceries.");
        }
    }
    private Recipe recipe(Long id){return recipes.findById(id).orElseThrow(()->
        new ResponseStatusException(HttpStatus.NOT_FOUND,"Recipe not found."));}
    private SavedChefRecipe chefRecipe(Long id){return chefRecipes.findByIdAndUserId(id,current.get().getId()).orElseThrow(()->
        new ResponseStatusException(HttpStatus.NOT_FOUND,"Saved recipe not found."));}

    private Map<String,Double> amounts(boolean purchased){
        Map<String,Double> out=new HashMap<>();
        Long userId=current.get().getId();
        if(purchased){
            for(PantryItem i:pantry.findByUserIdOrderByExpiryDateAsc(userId)){
                if(i.getQuantity()<=0 || (i.getExpiryDate()!=null && i.getExpiryDate().isBefore(LocalDate.now()))) continue;
                String u=unit(i.getUnit());if(u.isEmpty())continue;
                out.merge(norm(i.getName())+":"+baseUnit(u),baseQty(i.getQuantity(),u),Double::sum);
            }
        } else {
            for(ShoppingItem i:shopping.findByUserIdOrderByPurchasedAscIdDesc(userId)){
                if(i.isPurchased() || i.getQuantity()<=0)continue;
                String u=unit(i.getUnit());if(u.isEmpty())continue;
                out.merge(norm(i.getName())+":"+baseUnit(u),baseQty(i.getQuantity(),u),Double::sum);
            }
        }
        return out;
    }
    private List<Map<String,Object>> missing(List<Required> required,boolean subtractShopping){
        Map<String,Double> onHand=amounts(true),listed=subtractShopping?amounts(false):Map.of();
        Map<String,Stock> merged=new LinkedHashMap<>();
        for(Required r:required){
            String base=baseUnit(r.unit()),k=norm(r.name())+":"+base;
            double q=baseQty(r.quantity(),r.unit());
            if(merged.containsKey(k)) {
                Stock prev=merged.get(k);merged.put(k,new Stock(prev.name(),prev.quantity()+q,base));
            } else merged.put(k,new Stock(r.name(),q,base));
        }
        List<Map<String,Object>> needs=new ArrayList<>();
        for(var entry:merged.entrySet()){
            Stock r=entry.getValue();
            double have=onHand.getOrDefault(entry.getKey(),0.0);
            double listedQty=listed.getOrDefault(entry.getKey(),0.0);
            double need=Math.max(0,r.quantity()-have-listedQty);
            if(need>0.0001){
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("name",r.name()); item.put("quantity",tidy(need));item.put("unit",r.unit());
                item.put("inPantry",tidy(have));item.put("alreadyToBuy",tidy(listedQty));
                needs.add(item);
            }
        }
        return needs;
    }
    private Map<String,Object> status(String name,Long id,List<Required> needs){
        List<Map<String,Object>> missing=missing(needs,false);
        return Map.of("id",id,"name",name,"canCook",missing.isEmpty(),"missing",missing);
    }
    @GetMapping("/recipes/status") @Transactional
    public List<Map<String,Object>> catalogStatus(){
        return recipes.findAll().stream().map(r->status(r.getName(),r.getId(),catalogNeeds(r,Math.max(1,r.getServings())))).toList();
    }
    @GetMapping("/chef-recipes/status")
    public List<Map<String,Object>> chefStatus(){
        List<Map<String,Object>> output=new ArrayList<>();
        for(SavedChefRecipe r:chefRecipes.findByUserIdOrderByIdDesc(current.get().getId())){
            try{output.add(status(r.getName(),r.getId(),chefNeeds(r)));}
            catch(ResponseStatusException e){output.add(Map.of("id",r.getId(),"name",r.getName(),"canCook",false,"missing",List.of(),"warning","Unknown recipe quantities"));}
        }
        return output;
    }
    @PostMapping("/recipes/{id}/shopping") @Transactional
    public Map<String,Object> catalogToShopping(@PathVariable Long id,@RequestParam(required=false) Integer servings){
        Recipe r=recipe(id);
        return addToShopping(r.getName(),catalogNeeds(r,servings==null?r.getServings():servings));
    }
    @PostMapping("/chef-recipes/{id}/shopping") @Transactional
    public Map<String,Object> chefToShopping(@PathVariable Long id){
        SavedChefRecipe r=chefRecipe(id);
        return addToShopping(r.getName(),chefNeeds(r));
    }
    private Map<String,Object> addToShopping(String recipe,List<Required> required){
        List<Map<String,Object>> missing=missing(required,true);
        int added=0;
        for(Map<String,Object> item:missing){
            double quantity=((Number)item.get("quantity")).doubleValue();
            if(quantity<0.0001) continue;
            ShoppingItem groceries=new ShoppingItem();
            groceries.setUser(current.get());
            groceries.setName((String)item.get("name"));groceries.setQuantity(quantity);
            groceries.setUnit((String)item.get("unit"));groceries.setPurchased(false);
            // Additions are delta quantities; repeated clicks cannot duplicate previously-listed needs.
            ShoppingItem existing=shopping.findByUserIdOrderByPurchasedAscIdDesc(current.get().getId()).stream()
                .filter(i->!i.isPurchased() && norm(i.getName()).equals(norm(groceries.getName()))
                    && baseUnit(unit(i.getUnit())).equals(baseUnit(unit(groceries.getUnit()))))
                .findFirst().orElse(null);
            if(existing==null)shopping.save(groceries);
            else {
                String eu=unit(existing.getUnit());
                double delta=(eu.equals("kg") || eu.equals("l")) ? quantity/1000.0 : quantity;
                if(existing.getQuantity()+delta>100000)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Shopping quantity too large.");
                existing.setQuantity(existing.getQuantity()+delta);shopping.save(existing);
            }
            added++;
        }
        return Map.of("recipe",recipe,"added",added,"items",missing,
            "message",added==0?"Everything needed is in Pantry or already on Shopping.":"Missing ingredients added to Shopping.");
    }
    @GetMapping("/overview")
    public Map<String,Object> overview(){
        Long userId=current.get().getId();LocalDate today=LocalDate.now();
        var pantryItems=pantry.findByUserIdOrderByExpiryDateAsc(userId);
        var shoppingItems=shopping.findByUserIdOrderByPurchasedAscIdDesc(userId);
        long expiring=pantryItems.stream().filter(i->i.getQuantity()>0&&i.getExpiryDate()!=null&&
            !i.getExpiryDate().isBefore(today)&&!i.getExpiryDate().isAfter(today.plusDays(3))).count();
        long expired=pantryItems.stream().filter(i->i.getQuantity()>0&&i.getExpiryDate()!=null&&
            i.getExpiryDate().isBefore(today)).count();
        long toBuy=shoppingItems.stream().filter(i->!i.isPurchased()).count();
        long bought=shoppingItems.size()-toBuy;
        int upcoming=plans.findByUserIdAndDateBetweenOrderByDateAsc(userId,today,today.plusDays(6)).size();
        int todayMeals=meals.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId,
            today.atStartOfDay(),today.plusDays(1).atStartOfDay()).size();
        return Map.of("pantryItems",pantryItems.size(),"expiringSoon",expiring,"expired",expired,
            "toBuy",toBuy,"purchased",bought,"plannedNext7Days",upcoming,"savedChefRecipes",
            chefRecipes.findByUserIdOrderByIdDesc(userId).size(),"mealsToday",todayMeals);
    }
}
