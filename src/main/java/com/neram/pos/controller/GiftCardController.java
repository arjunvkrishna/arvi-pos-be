package com.neram.pos.controller;

import com.neram.pos.dto.GiftCardDTO;
import com.neram.pos.entity.User;
import com.neram.pos.repository.UserRepository;
import com.neram.pos.service.GiftCardService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.CRM)
public class GiftCardController {

    private final GiftCardService giftCardService;
    private final UserRepository userRepository;

    @GetMapping("/gift-cards")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PagedModel<GiftCardDTO>> listCards(Pageable pageable) {
        // PagedModel is Spring Data's stable page shape ({content, page:{size, number, totalElements, totalPages}}); a bare Page is not
        return ResponseEntity.ok(new PagedModel<>(giftCardService.listCards(pageable)));
    }

    @PostMapping("/gift-cards")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<GiftCardDTO> issueCard(@RequestBody IssueRequest req) {
        Long userId = getCurrentUserId();
        GiftCardDTO dto = giftCardService.issueGiftCard(
                req.getInitialBalance(),
                req.getPaymentMode(),
                req.getCustomerId(),
                req.getExpiresAt(),
                userId
        );
        return ResponseEntity.ok(dto);
    }

    /** The cafe is named in the URL so the balance is read from that cafe's own database; no login is needed. */
    @GetMapping("/public/{cafe}/gift-cards/{code}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String cafe, @PathVariable String code) {
        return giftCardService.getByCode(code)
                .map(card -> ResponseEntity.ok(Map.of(
                        "code", card.getCode(),
                        "currentBalance", card.getCurrentBalance(),
                        "status", card.getStatus(),
                        "expiresAt", card.getExpiresAt() != null ? card.getExpiresAt().toString() : "No Expiry"
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/gift-cards/{code}/redeem")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> redeem(@PathVariable String code, @RequestBody RedeemRequest req) {
        giftCardService.redeem(code, req.getAmount(), req.getBillId());
        return ResponseEntity.ok(Map.of("message", "Redemption successful"));
    }

    private Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                return userRepository.findByUsername(auth.getName()).map(User::getId).orElse(null);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Data
    public static class IssueRequest {
        private BigDecimal initialBalance;
        private String paymentMode; // CASH, UPI, CARD
        private Long customerId;
        private LocalDate expiresAt;
    }

    @Data
    public static class RedeemRequest {
        private BigDecimal amount;
        private Long billId;
    }
}
