package com.foodlens.controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/stores")
public class StoreController {
    @GetMapping("/nearby") public List<Map<String,Object>> nearby(@RequestParam(required=false) Double lat,@RequestParam(required=false) Double lng){
        return List.of(
            Map.of("name","FreshMart","distanceKm",1.2,"open",true,"availability","Likely","note","Demo data - connect a maps/store provider for live results"),
            Map.of("name","Neighborhood Supermarket","distanceKm",1.8,"open",true,"availability","Likely","note","Demo data - connect a maps/store provider for live results"),
            Map.of("name","Organic Basket","distanceKm",2.6,"open",false,"availability","Unknown","note","Demo data - connect a maps/store provider for live results")
        );
    }
}
