package com.foodlens.service;

import com.foodlens.dto.AnalysisDtos.*;
import com.foodlens.entity.Food;
import com.foodlens.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.*;

@Service @RequiredArgsConstructor
public class FoodAnalysisService {
    private final FoodRepository foods;
    @Value("${foodlens.ai-service-url}") private String aiUrl;
    private static final long MAX_IMAGE_BYTES=10L*1024*1024;
    private static final Map<String,String> ALIASES=Map.ofEntries(
        Map.entry("steamed rice","Rice"),Map.entry("white rice","Rice"),Map.entry("basmati rice","Rice"),
        Map.entry("rice pilaf","Rice"),Map.entry("grilled chicken","Chicken"),
        Map.entry("roasted chicken","Chicken"),Map.entry("chicken tikka","Chicken"),
        Map.entry("chicken masala","Chicken Curry"),Map.entry("lentil soup","Dal"),
        Map.entry("lentil curry","Dal"),Map.entry("yellow dal","Dal"),
        Map.entry("chapati","Roti"),Map.entry("flatbread","Roti"),Map.entry("roti","Roti"),
        Map.entry("naan bread","Naan"),Map.entry("scrambled eggs","Egg"),Map.entry("boiled egg","Egg"),
        Map.entry("fried egg","Egg"),Map.entry("eggs","Egg"),Map.entry("omelette","Omelette"),
        Map.entry("mixed salad","Salad"),Map.entry("vegetable salad","Salad"),
        Map.entry("yogurt","Curd"),Map.entry("plain yogurt","Curd"),Map.entry("dahi","Curd"),
        Map.entry("molten chocolate cake","Chocolate Lava Cake"),
        Map.entry("molten lava cake","Chocolate Lava Cake"),
        Map.entry("chocolate fondant","Chocolate Lava Cake"),
        Map.entry("lava cake","Chocolate Lava Cake"),
        Map.entry("chocolate lava cake","Chocolate Lava Cake"),
        Map.entry("vanilla ice cream scoop","Vanilla Ice Cream"),
        Map.entry("ice cream scoop","Ice Cream"),
        Map.entry("chocolate syrup","Chocolate Sauce"),
        Map.entry("cottage cheese","Paneer"),Map.entry("chickpea curry","Chana Masala")
    );

    public FoodAnalysisResponse analyze(MultipartFile file) throws IOException {
        if(file==null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose an image first.");
        if(file.getSize()>MAX_IMAGE_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Image must be 10 MB or smaller.");
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("meal.jpg");
        MediaType contentType;
        try {contentType=MediaType.parseMediaType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"));}
        catch(Exception e){contentType=MediaType.APPLICATION_OCTET_STREAM;}

        // Send a real multipart/form-data request to FastAPI.
        String boundary = "FoodLens" + java.util.UUID.randomUUID()
                .toString().replace("-", "");

        String safeFilename = filename.replaceAll("[\\r\\n\\\"]", "_");

        String header = new StringBuilder()
                .append("--").append(boundary)
                .append((char) 13).append((char) 10)
                .append("Content-Disposition: form-data; name=")
                .append((char) 34).append("file").append((char) 34)
                .append("; filename=")
                .append((char) 34).append(safeFilename).append((char) 34)
                .append((char) 13).append((char) 10)
                .append("Content-Type: ").append(contentType)
                .append((char) 13).append((char) 10)
                .append((char) 13).append((char) 10)
                .toString();

        var output = new java.io.ByteArrayOutputStream();
        output.writeBytes(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.writeBytes(file.getBytes());
        output.writeBytes(("\r\n--" + boundary + "--\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        AiAnalysisResponse ai;

        try {
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(aiUrl + "/analyze"))
                    .timeout(java.time.Duration.ofSeconds(290))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(java.net.http.HttpRequest.BodyPublishers
                            .ofByteArray(output.toByteArray()))
                    .build();

            var client = java.net.http.HttpClient.newBuilder()
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();

            var response = client.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI service HTTP " + response.statusCode()
                                + ": " + response.body()
                );
            }

            ai = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(response.body(), AiAnalysisResponse.class);

        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT, "Local AI analysis timed out.", ex
            );
        } catch (java.io.IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "AI connection or response failed.", ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "AI request interrupted.", ex
            );
        }

        if(ai==null || ai.foods()==null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI service returned no result.");
        return enrich(ai.foods(),ai.provider(),ai.note());
    }

    public List<String> catalog(){return foods.findAll().stream().map(Food::getName).sorted().toList();}

    public FoodAnalysisResponse recalculate(RecalculateRequest request){
        if(request==null || request.foods()==null || request.foods().isEmpty() || request.foods().size()>12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Supply between 1 and 12 foods.");
        return enrich(request.foods(),request.provider(),request.note());
    }

    private FoodAnalysisResponse enrich(List<AiDetectedItem> detected,String provider,String note){
        if(detected.size()>12) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Too many food items.");
        List<EnrichedItem> result=new ArrayList<>();
        List<String> unknown=new ArrayList<>();
        double tc=0,tp=0,tcarb=0,tf=0,tfib=0;
        for(var d:detected){
            if(d.name()==null || d.name().isBlank() || d.name().length()>100 || !Double.isFinite(d.estimatedGrams()) || d.estimatedGrams()<=0 || d.estimatedGrams()>5000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter valid food names and portions between 1 and 5000 grams.");
            String key=d.name().trim().toLowerCase(Locale.ROOT);
            String normalized=ALIASES.getOrDefault(key,d.name().trim());
            Food f=foods.findByNameIgnoreCase(normalized).orElse(null);
            boolean known=f!=null;
            if(!known) unknown.add(d.name());
            double factor=d.estimatedGrams()/100.0;
            double cal=known?f.getCaloriesPer100g()*factor:0;
            double p=known?f.getProteinPer100g()*factor:0;
            double carbs=known?f.getCarbsPer100g()*factor:0;
            double fat=known?f.getFatPer100g()*factor:0;
            double fiber=known?f.getFiberPer100g()*factor:0;
            result.add(new EnrichedItem(d.name().trim(),Math.max(0,Math.min(1,d.confidence())),round(d.estimatedGrams()),round(cal),round(p),round(carbs),round(fat),round(fiber),known));
            tc+=cal;tp+=p;tcarb+=carbs;tf+=fat;tfib+=fiber;
        }
        List<String> benefits=new ArrayList<>(),concerns=new ArrayList<>();
        if(tp>=25) benefits.add("Protein contribution from recognized foods");
        if(tfib>=7) benefits.add("Fiber contribution from recognized foods");
        if(result.size()>=3) benefits.add("Several food components detected");
        if(tcarb>90) concerns.add("Carbohydrate estimate is relatively high; verify portions");
        if(tf>35) concerns.add("Oil and fat may be underestimated from photos");
        if(!unknown.isEmpty()) concerns.add("Missing nutrition data for: "+String.join(", ",unknown));
        if(benefits.isEmpty()) benefits.add("Portions are estimates; confirm ingredients and serving sizes");
        if(concerns.isEmpty()) concerns.add("Hidden oil, sugar, salt and ingredients cannot be determined reliably");
        int score=unknown.isEmpty() && !result.isEmpty()?Math.max(1,Math.min(10,6+(tp>=25?1:0)+(tfib>=7?1:0)+(result.size()>=3?1:0)-(tcarb>100?1:0)-(tf>40?1:0))):0;
        String safeNote=Optional.ofNullable(note).orElse("Visual estimates are approximate.");
        if(!unknown.isEmpty()) safeNote+=" Nutrition is INCOMPLETE for "+String.join(", ",unknown)+". Edit the food names and recalculate before saving.";
        return new FoodAnalysisResponse(result,round(tc),round(tp),round(tcarb),round(tf),round(tfib),score,benefits,concerns,
            Optional.ofNullable(provider).orElse("unknown"),safeNote);
    }
    private double round(double v){return Math.round(v*10.0)/10.0;}
}
