package com.foodlens.controller;
import com.foodlens.entity.PantryItem;
import com.foodlens.repository.PantryItemRepository;
import com.foodlens.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
@RestController @RequestMapping("/api/pantry") @RequiredArgsConstructor
public class PantryController {
    private final PantryItemRepository repo; private final CurrentUserService current;
    @GetMapping public List<PantryItem> list(){ return repo.findByUserIdOrderByExpiryDateAsc(current.get().getId()); }
    private void validate(PantryItem p){
        if(p.getName()==null || p.getName().isBlank() || p.getName().length()>100 ||
           !Double.isFinite(p.getQuantity()) || p.getQuantity()<0 || p.getQuantity()>100000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid ingredient and quantity.");
    }
    private PantryItem owned(Long id){
        PantryItem p=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Pantry item not found."));
        if(!p.getUser().getId().equals(current.get().getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not your pantry item.");
        return p;
    }
    @PostMapping public PantryItem add(@RequestBody PantryItem p){validate(p);p.setId(null);p.setUser(current.get());return repo.save(p);}
    @PutMapping("/{id}") public PantryItem update(@PathVariable Long id,@RequestBody PantryItem change){
        validate(change);PantryItem p=owned(id);
        p.setName(change.getName().trim());p.setQuantity(change.getQuantity());p.setUnit(change.getUnit());
        p.setCategory(change.getCategory());p.setExpiryDate(change.getExpiryDate());return repo.save(p);
    }
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id){repo.delete(owned(id));}
}
