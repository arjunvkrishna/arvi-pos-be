package com.neram.pos.service;

import com.neram.pos.dto.PromotionRequestDTO;
import com.neram.pos.dto.PromotionResponseDTO;
import com.neram.pos.entity.Promotion;
import com.neram.pos.entity.User;
import com.neram.pos.repository.PromotionRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    public static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final PromotionRepository promotionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public List<PromotionResponseDTO> getAllPromotions() {
        return promotionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /** One page of promotions, newest first. */
    public org.springframework.data.domain.Page<PromotionResponseDTO> getAllPromotions(org.springframework.data.domain.Pageable pageable) {
        return promotionRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponseDTO);
    }

    public PromotionResponseDTO getPromotionById(Long id) {
        Promotion promo = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promotion not found with id: " + id));
        return toResponseDTO(promo);
    }

    public Optional<Promotion> getActivePromotion() {
        LocalDateTime nowKolkata = LocalDateTime.now(ZONE_KOLKATA);
        List<Promotion> activePromos = promotionRepository.findActivePromotionsAt(nowKolkata).stream()
                .filter(p -> PromotionSchedule.isActiveAt(p, nowKolkata)).collect(Collectors.toList());
        if (activePromos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(activePromos.get(0));
    }

    @Transactional
    public PromotionResponseDTO createPromotion(PromotionRequestDTO dto, Long userId, String username) {
        validatePromotionRequest(dto);

        boolean willBeActive = dto.getIsActive() == null || Boolean.TRUE.equals(dto.getIsActive());
        if (willBeActive) {
            checkNoActiveOverlap(null, candidate(dto));
        }

        Promotion promo = Promotion.builder()
                .name(dto.getName().trim())
                .discountPercentage(dto.getDiscountPercentage())
                .startDateTime(dto.getStartDateTime())
                .endDateTime(dto.getEndDateTime())
                .isActive(willBeActive)
                .recurring(Boolean.TRUE.equals(dto.getRecurring()))
                .daysOfWeek(Boolean.TRUE.equals(dto.getRecurring()) ? PromotionSchedule.joinDays(dto.getDaysOfWeek()) : null)
                .dailyStart(Boolean.TRUE.equals(dto.getRecurring()) ? dto.getDailyStart() : null)
                .dailyEnd(Boolean.TRUE.equals(dto.getRecurring()) ? dto.getDailyEnd() : null)
                .description(dto.getDescription() != null ? dto.getDescription().trim() : null)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Promotion saved = promotionRepository.save(promo);
        auditLogService.log("CREATE_PROMOTION", "PROMOTION", saved.getId(), null,
                String.format("Created promotion '%s' (%.1f%%) valid %s to %s | User: %s",
                        saved.getName(), saved.getDiscountPercentage(),
                        saved.getStartDateTime().format(FMT), saved.getEndDateTime().format(FMT), username));

        return toResponseDTO(saved);
    }

    @Transactional
    public PromotionResponseDTO updatePromotion(Long id, PromotionRequestDTO dto, String username) {
        Promotion promo = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promotion not found with id: " + id));

        validatePromotionRequest(dto);

        boolean willBeActive = dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE.equals(promo.getIsActive());
        if (willBeActive) {
            checkNoActiveOverlap(id, candidate(dto));
        }

        String oldDesc = String.format("'%s' (%.1f%%, Active: %s)", promo.getName(), promo.getDiscountPercentage(), promo.getIsActive());

        promo.setName(dto.getName().trim());
        promo.setDiscountPercentage(dto.getDiscountPercentage());
        promo.setStartDateTime(dto.getStartDateTime());
        promo.setEndDateTime(dto.getEndDateTime());
        promo.setIsActive(willBeActive);
        promo.setRecurring(Boolean.TRUE.equals(dto.getRecurring()));
        promo.setDaysOfWeek(Boolean.TRUE.equals(dto.getRecurring()) ? PromotionSchedule.joinDays(dto.getDaysOfWeek()) : null);
        promo.setDailyStart(Boolean.TRUE.equals(dto.getRecurring()) ? dto.getDailyStart() : null);
        promo.setDailyEnd(Boolean.TRUE.equals(dto.getRecurring()) ? dto.getDailyEnd() : null);
        if (dto.getDescription() != null) {
            promo.setDescription(dto.getDescription().trim());
        }
        promo.setUpdatedAt(LocalDateTime.now());

        Promotion saved = promotionRepository.save(promo);
        auditLogService.log("UPDATE_PROMOTION", "PROMOTION", saved.getId(), oldDesc,
                String.format("Updated promotion '%s' (%.1f%%, Active: %s) valid %s to %s | User: %s",
                        saved.getName(), saved.getDiscountPercentage(), saved.getIsActive(),
                        saved.getStartDateTime().format(FMT), saved.getEndDateTime().format(FMT), username));

        return toResponseDTO(saved);
    }

    @Transactional
    public PromotionResponseDTO toggleActive(Long id, String username) {
        Promotion promo = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promotion not found with id: " + id));

        boolean newActive = !Boolean.TRUE.equals(promo.getIsActive());
        if (newActive) {
            checkNoActiveOverlap(id, promo);
        }

        promo.setIsActive(newActive);
        promo.setUpdatedAt(LocalDateTime.now());
        Promotion saved = promotionRepository.save(promo);

        String action = newActive ? "ACTIVATE_PROMOTION" : "DEACTIVATE_PROMOTION";
        auditLogService.log(action, "PROMOTION", saved.getId(), null,
                String.format("%s promotion '%s' | User: %s", newActive ? "Activated" : "Deactivated", saved.getName(), username));

        return toResponseDTO(saved);
    }

    @Transactional
    public void deletePromotion(Long id, String username) {
        Promotion promo = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promotion not found with id: " + id));

        String info = String.format("Deleted promotion '%s' (%.1f%%)", promo.getName(), promo.getDiscountPercentage());
        promotionRepository.delete(promo);
        auditLogService.log("DELETE_PROMOTION", "PROMOTION", id, info, "User: " + username);
    }

    private void validatePromotionRequest(PromotionRequestDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Promotion name is required and cannot be blank.");
        }
        if (dto.getDiscountPercentage() == null) {
            throw new IllegalArgumentException("Discount percentage is required.");
        }
        if (dto.getDiscountPercentage() <= 0 || dto.getDiscountPercentage() > 100) {
            throw new IllegalArgumentException(String.format("Discount percentage must be greater than 0 and at most 100. Entered: %.2f%%", dto.getDiscountPercentage()));
        }
        if (dto.getStartDateTime() == null) {
            throw new IllegalArgumentException("Start date/time is required.");
        }
        if (dto.getEndDateTime() == null) {
            throw new IllegalArgumentException("End date/time is required.");
        }
        if (!dto.getEndDateTime().isAfter(dto.getStartDateTime())) {
            throw new IllegalArgumentException("Promotion end date/time must be strictly after the start date/time.");
        }
        if (Boolean.TRUE.equals(dto.getRecurring())) {
            PromotionSchedule.validateRecurring(dto.getDaysOfWeek(), dto.getDailyStart(), dto.getDailyEnd());
        }
    }

    /** The request as a promotion, so the overlap rules can compare it with the saved ones. */
    private static Promotion candidate(PromotionRequestDTO dto) {
        boolean rec = Boolean.TRUE.equals(dto.getRecurring());
        return Promotion.builder().startDateTime(dto.getStartDateTime()).endDateTime(dto.getEndDateTime()).isActive(true).recurring(rec)
                .daysOfWeek(rec ? PromotionSchedule.joinDays(dto.getDaysOfWeek()) : null).dailyStart(rec ? dto.getDailyStart() : null).dailyEnd(rec ? dto.getDailyEnd() : null).build();
    }

    private void checkNoActiveOverlap(Long excludeId, Promotion candidate) {
        for (Promotion p : promotionRepository.findByIsActiveTrue()) {
            if (excludeId != null && p.getId().equals(excludeId)) {
                continue;
            }
            if (PromotionSchedule.overlaps(candidate, p)) {
                throw new IllegalArgumentException(String.format(
                        "This promotion (%s to %s%s) could apply at the same time as the active promotion '%s' (%s to %s%s). Only one active promotion may apply at a time.",
                        candidate.getStartDateTime().format(FMT), candidate.getEndDateTime().format(FMT), suffix(candidate),
                        p.getName(), p.getStartDateTime().format(FMT), p.getEndDateTime().format(FMT), suffix(p)));
            }
        }
    }

    private static String suffix(Promotion p) {
        String t = PromotionSchedule.scheduleText(p);
        return t == null ? "" : ", " + t;
    }

    public PromotionResponseDTO toResponseDTO(Promotion p) {
        LocalDateTime nowKolkata = LocalDateTime.now(ZONE_KOLKATA);
        String status;
        if (!Boolean.TRUE.equals(p.getIsActive())) {
            status = "INACTIVE";
        } else if (nowKolkata.isBefore(p.getStartDateTime())) {
            status = "SCHEDULED";
        } else if (nowKolkata.isAfter(p.getEndDateTime())) {
            status = "EXPIRED";
        } else if (Boolean.TRUE.equals(p.getRecurring()) && !PromotionSchedule.isActiveAt(p, nowKolkata)) {
            status = "SCHEDULED";   // inside its date range but outside the daily window
        } else {
            status = "ACTIVE";
        }

        String createdByName = null;
        if (p.getCreatedBy() != null) {
            createdByName = userRepository.findById(p.getCreatedBy())
                    .map(User::getUsername)
                    .orElse(null);
        }

        return PromotionResponseDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .discountPercentage(p.getDiscountPercentage())
                .startDateTime(p.getStartDateTime())
                .endDateTime(p.getEndDateTime())
                .isActive(p.getIsActive())
                .status(status)
                .description(p.getDescription())
                .createdBy(p.getCreatedBy())
                .createdByName(createdByName)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .recurring(Boolean.TRUE.equals(p.getRecurring()))
                .daysOfWeek(Boolean.TRUE.equals(p.getRecurring()) ? PromotionSchedule.parseDays(p.getDaysOfWeek()) : null)
                .dailyStart(p.getDailyStart())
                .dailyEnd(p.getDailyEnd())
                .scheduleText(PromotionSchedule.scheduleText(p))
                .build();
    }
}
