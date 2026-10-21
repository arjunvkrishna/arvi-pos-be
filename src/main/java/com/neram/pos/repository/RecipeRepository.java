package com.neram.pos.repository;

import com.neram.pos.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findByMenuItemId(Long menuItemId);
    List<Recipe> findByMenuItemIdAndActiveTrue(Long menuItemId);
    List<Recipe> findByInventoryItemId(Long inventoryItemId);
    Optional<Recipe> findByMenuItemIdAndInventoryItemId(Long menuItemId, Long inventoryItemId);
}
