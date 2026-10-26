package com.neram.pos.controller;

import com.neram.pos.dto.RecipeDTO;
import com.neram.pos.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping("/menu-item/{menuItemId}")
    public ResponseEntity<List<RecipeDTO.RecipeResponse>> getRecipesForMenuItem(@PathVariable Long menuItemId) {
        return ResponseEntity.ok(recipeService.getRecipesForMenuItem(menuItemId));
    }

    @GetMapping
    public ResponseEntity<List<RecipeDTO.RecipeResponse>> getAllRecipes() {
        return ResponseEntity.ok(recipeService.getAllRecipes());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<RecipeDTO.RecipeResponse> createRecipe(@RequestBody RecipeDTO.RecipeRequest req) {
        return ResponseEntity.ok(recipeService.createRecipe(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<RecipeDTO.RecipeResponse> updateRecipe(
            @PathVariable Long id,
            @RequestBody RecipeDTO.RecipeRequest req) {
        return ResponseEntity.ok(recipeService.updateRecipe(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<Void> deleteRecipe(@PathVariable Long id) {
        recipeService.deleteRecipe(id);
        return ResponseEntity.noContent().build();
    }
}
