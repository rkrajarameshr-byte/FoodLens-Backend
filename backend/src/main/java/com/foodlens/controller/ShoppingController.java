package com.foodlens.controller;

import com.foodlens.entity.PantryItem;
import com.foodlens.entity.ShoppingItem;
import com.foodlens.repository.PantryItemRepository;
import com.foodlens.repository.ShoppingItemRepository;
import com.foodlens.service.CurrentUserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Authenticated shopping list. Never return or modify another user's items. */
@RestController
@RequestMapping("/api/shopping")
@RequiredArgsConstructor
public class ShoppingController {
    private final ShoppingItemRepository repo;
    private final PantryItemRepository pantry;
    private final CurrentUserService current;

    public record Change(String name, Double quantity, String unit) {}
    private record Validated(String name, double quantity, String unit) {}

    private static String normalized(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private static Validated validate(Change request) {
        if (request == null || request.name() == null || request.name().isBlank()
                || request.name().strip().length() > 100
                || request.quantity() == null || !Double.isFinite(request.quantity())
                || request.quantity() <= 0 || request.quantity() > 100000
                || request.unit() == null || request.unit().isBlank()
                || request.unit().strip().length() > 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a food name, positive quantity and unit.");
        }
        return new Validated(request.name().strip(), request.quantity(), request.unit().strip());
    }
    private ShoppingItem owned(Long id) {
        ShoppingItem item = repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping item not found."));
        if (!item.getUser().getId().equals(current.get().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your shopping item.");
        }
        return item;
    }
    private List<ShoppingItem> mine() {
        return repo.findByUserIdOrderByPurchasedAscIdDesc(current.get().getId());
    }

    @GetMapping
    public List<ShoppingItem> list() { return mine(); }

    @PostMapping
    @Transactional
    public ShoppingItem add(@RequestBody Change request) {
        Validated v = validate(request);
        // Add repeat grocery entries together only when units are the same.
        for (ShoppingItem existing : mine()) {
            if (!existing.isPurchased()
                    && normalized(existing.getName()).equals(normalized(v.name()))
                    && normalized(existing.getUnit()).equals(normalized(v.unit()))) {
                double combined = existing.getQuantity() + v.quantity();
                if (combined > 100000)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity too large.");
                existing.setQuantity(combined);
                return repo.save(existing);
            }
        }
        ShoppingItem created = new ShoppingItem();
        created.setUser(current.get());
        created.setName(v.name());
        created.setQuantity(v.quantity());
        created.setUnit(v.unit());
        created.setPurchased(false);
        return repo.save(created);
    }

    @PutMapping("/{id}")
    @Transactional
    public ShoppingItem update(@PathVariable Long id, @RequestBody Change request) {
        Validated v = validate(request);
        ShoppingItem item = owned(id);
        item.setName(v.name());
        item.setQuantity(v.quantity());
        item.setUnit(v.unit());
        return repo.save(item);
    }

    @PutMapping("/{id}/toggle")
    @Transactional
    public ShoppingItem toggle(@PathVariable Long id) {
        ShoppingItem item = owned(id);
        item.setPurchased(!item.isPurchased());
        return repo.save(item);
    }

    @PostMapping("/{id}/pantry")
    @Transactional
    public PantryItem moveToPantry(@PathVariable Long id) {
        ShoppingItem item = owned(id);
        if (!item.isPurchased())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mark the item purchased before transferring it to the pantry.");
        // Only merge with stock that has no expiry date, so new groceries aren't
        // assigned an old expiry date and expired food isn't made current.
        PantryItem target = pantry.findByUserIdOrderByExpiryDateAsc(current.get().getId()).stream()
                .filter(p -> p.getExpiryDate() == null
                        && normalized(p.getName()).equals(normalized(item.getName()))
                        && p.getUnit() != null
                        && normalized(p.getUnit()).equals(normalized(item.getUnit())))
                .findFirst().orElse(null);
        PantryItem saved;
        if (target == null) {
            PantryItem p = new PantryItem();
            p.setUser(current.get());
            p.setName(item.getName());
            p.setQuantity(item.getQuantity());
            p.setUnit(item.getUnit());
            p.setCategory("Groceries");
            saved = pantry.save(p);
        } else {
            double combined = target.getQuantity() + item.getQuantity();
            if (!Double.isFinite(combined) || combined > 100000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pantry quantity too large.");
            target.setQuantity(combined);
            saved = pantry.save(target);
        }
        repo.delete(item);
        return saved;
    }

    @DeleteMapping("/purchased")
    @Transactional
    public Map<String,Integer> clearPurchased() {
        List<ShoppingItem> bought = mine().stream().filter(ShoppingItem::isPurchased).toList();
        repo.deleteAll(bought);
        return Map.of("deleted", bought.size());
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repo.delete(owned(id));
    }
}
