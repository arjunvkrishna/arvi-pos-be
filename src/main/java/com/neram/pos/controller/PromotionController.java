package com.neram.pos.controller;

import com.neram.pos.dto.PromotionRequestDTO;
import com.neram.pos.dto.PromotionResponseDTO;
import com.neram.pos.repository.UserRepository;
import com.neram.pos.service.PromotionService;
import lombok.RequiredArgsConstructor;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<PromotionResponseDTO>> getAllPromotions(Pageable pageable) {
        return PagedResponse.of(promotionService.getAllPromotions(pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<PromotionResponseDTO> getActivePromotion() {
        return promotionService.getActivePromotion()
                .map(p -> ResponseEntity.ok(promotionService.toResponseDTO(p)))
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PromotionResponseDTO> getPromotionById(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getPromotionById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<PromotionResponseDTO> createPromotion(
            @RequestBody PromotionRequestDTO request, Authentication auth) {
        Long userId = getUserId(auth);
        String username = auth != null ? auth.getName() : "SYSTEM";
        return ResponseEntity.ok(promotionService.createPromotion(request, userId, username));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<PromotionResponseDTO> updatePromotion(
            @PathVariable Long id, @RequestBody PromotionRequestDTO request, Authentication auth) {
        String username = auth != null ? auth.getName() : "SYSTEM";
        return ResponseEntity.ok(promotionService.updatePromotion(id, request, username));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<PromotionResponseDTO> toggleActive(
            @PathVariable Long id, Authentication auth) {
        String username = auth != null ? auth.getName() : "SYSTEM";
        return ResponseEntity.ok(promotionService.toggleActive(id, username));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_MENU')")
    public ResponseEntity<Map<String, String>> deletePromotion(
            @PathVariable Long id, Authentication auth) {
        String username = auth != null ? auth.getName() : "SYSTEM";
        promotionService.deletePromotion(id, username);
        return ResponseEntity.ok(Map.of("message", "Promotion deleted successfully"));
    }

    private Long getUserId(Authentication auth) {
        if (auth == null) return null;
        return userRepository.findByUsername(auth.getName())
                .map(u -> u.getId()).orElse(null);
    }
}
