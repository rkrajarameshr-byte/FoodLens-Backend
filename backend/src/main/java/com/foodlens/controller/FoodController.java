package com.foodlens.controller;
import com.foodlens.service.FoodAnalysisService;
import com.foodlens.dto.AnalysisDtos.RecalculateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/food") @RequiredArgsConstructor
public class FoodController {
    private final FoodAnalysisService service;
    @PostMapping(value="/analyze", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object analyze(@RequestPart("image") MultipartFile image) throws Exception { return service.analyze(image); }
    @GetMapping("/catalog")
    public Object catalog(){return service.catalog();}
    @PostMapping("/recalculate")
    public Object recalculate(@RequestBody RecalculateRequest r){return service.recalculate(r);}
}
