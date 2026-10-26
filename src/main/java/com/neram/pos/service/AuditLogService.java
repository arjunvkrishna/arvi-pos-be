package com.neram.pos.service;

import com.neram.pos.dto.AuditLogDTO;
import com.neram.pos.entity.AuditLog;
import com.neram.pos.entity.User;
import com.neram.pos.repository.AuditLogRepository;
import com.neram.pos.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private HttpServletRequest httpServletRequest;

    public void log(String action, String entityType, Long entityId, String oldValue, String newValue) {
        log(action, entityType, entityId, oldValue, newValue, null, null);
    }

    public void log(String action, String entityType, Long entityId, String oldValue, String newValue, Long explicitUserId, String explicitUserName) {
        Long userId = explicitUserId;
        String userName = explicitUserName;

        if (userName == null || userId == null) {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                    if (userName == null) userName = auth.getName();
                    if (userId == null) {
                        User u = userRepository.findByUsername(userName).orElse(null);
                        if (u != null) {
                            userId = u.getId();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (userName == null) userName = "SYSTEM";

        String clientIp = null;
        if (httpServletRequest != null) {
            try {
                clientIp = httpServletRequest.getHeader("X-Forwarded-For");
                if (clientIp == null || clientIp.isBlank() || "unknown".equalsIgnoreCase(clientIp)) {
                    clientIp = httpServletRequest.getRemoteAddr();
                } else if (clientIp.contains(",")) {
                    clientIp = clientIp.split(",")[0].trim();
                }
            } catch (Exception ignored) {}
        }

        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setPerformedBy(userId);
        log.setPerformedByName(userName);
        log.setIpAddress(clientIp);
        log.setPerformedAt(LocalDateTime.now());
        auditLogRepository.save(log);
    }

    private static final int MAX_LOG_ROWS = 2000;

    public List<AuditLogDTO> getAllLogs() {
        return getLogs(null, null, null, null, null, null);
    }

    public List<AuditLogDTO> getLogs(String username, String entityType, String action) {
        return getLogs(username, entityType, action, null, null, null);
    }

    public List<AuditLogDTO> getLogs(String username, String entityType, String action, LocalDate date, LocalDate startDate, LocalDate endDate) {
        String u = (username != null && !username.isBlank() && !username.equalsIgnoreCase("ALL")) ? username.trim() : null;
        String e = (entityType != null && !entityType.isBlank() && !entityType.equalsIgnoreCase("ALL")) ? entityType.trim() : null;
        String a = (action != null && !action.isBlank() && !action.equalsIgnoreCase("ALL")) ? action.trim() : null;

        LocalDate start = date != null ? date : startDate;
        LocalDate end = date != null ? date : endDate;

        if (u == null && e == null && a == null && start == null && end == null) {
            return auditLogRepository.findTop200ByOrderByPerformedAtDesc().stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }

        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (u != null) {
                predicates.add(cb.equal(cb.lower(root.get("performedByName")), u.toLowerCase()));
            }
            if (e != null) {
                predicates.add(cb.equal(root.get("entityType"), e));
            }
            if (a != null) {
                predicates.add(cb.equal(root.get("action"), a));
            }
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("performedAt"), start.atStartOfDay()));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("performedAt"), end.atTime(LocalTime.MAX)));
            }
            query.orderBy(cb.desc(root.get("performedAt")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        // Bounded: newest MAX_LOG_ROWS matches only, so a wide filter can never load the whole audit table into memory.
        return auditLogRepository.findBy(spec, q -> q.sortBy(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "performedAt")).limit(MAX_LOG_ROWS).all()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<AuditLogDTO> getMyLogs(String action, String entityType, LocalDate date, LocalDate startDate, LocalDate endDate) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required to access personal audit logs");
        }
        String currentUsername = auth.getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        String e = (entityType != null && !entityType.isBlank() && !entityType.equalsIgnoreCase("ALL")) ? entityType.trim() : null;
        String a = (action != null && !action.isBlank() && !action.equalsIgnoreCase("ALL")) ? action.trim() : null;

        LocalDate start = date != null ? date : startDate;
        LocalDate end = date != null ? date : endDate;

        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            // Strictly filter for authenticated user only: either by username or userId
            if (currentUserId != null) {
                predicates.add(cb.or(
                        cb.equal(cb.lower(root.get("performedByName")), currentUsername.toLowerCase()),
                        cb.equal(root.get("performedBy"), currentUserId)
                ));
            } else {
                predicates.add(cb.equal(cb.lower(root.get("performedByName")), currentUsername.toLowerCase()));
            }

            if (e != null) {
                predicates.add(cb.equal(root.get("entityType"), e));
            }
            if (a != null) {
                predicates.add(cb.equal(root.get("action"), a));
            }
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("performedAt"), start.atStartOfDay()));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("performedAt"), end.atTime(LocalTime.MAX)));
            }
            query.orderBy(cb.desc(root.get("performedAt")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        // Bounded: newest MAX_LOG_ROWS matches only, so a wide filter can never load the whole audit table into memory.
        return auditLogRepository.findBy(spec, q -> q.sortBy(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "performedAt")).limit(MAX_LOG_ROWS).all()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * One page of the audit log (admin view), newest first. Every filter is optional and applied in the database:
     * user, entity type (category), action, one day or a date range, free text over action / user / entity / old and new value /
     * entity id, and billsOnly (bill entity or an action with BILL in its name). Without page/size it falls back to the
     * bounded list from {@link #getLogs}.
     */
    public org.springframework.data.domain.Page<AuditLogDTO> getLogsPage(String username, String entityType, String action,
                                                                         LocalDate date, LocalDate startDate, LocalDate endDate,
                                                                         String q, boolean billsOnly, org.springframework.data.domain.Pageable pageable) {
        if ((pageable == null || pageable.isUnpaged()) && (q == null || q.isBlank()) && !billsOnly) {
            List<AuditLogDTO> all = getLogs(username, entityType, action, date, startDate, endDate);
            return new org.springframework.data.domain.PageImpl<>(all, org.springframework.data.domain.Pageable.unpaged(), all.size());
        }
        String u = (username != null && !username.isBlank() && !username.equalsIgnoreCase("ALL")) ? username.trim() : null;
        return findAuditPage(auditSpec(u, null, null, entityType, action, date, startDate, endDate, q, billsOnly), pageable);
    }

    /** One page of the signed-in user's own audit trail, newest first (same optional filters as {@link #getLogsPage}). */
    public org.springframework.data.domain.Page<AuditLogDTO> getMyLogsPage(String entityType, String action, LocalDate date,
                                                                           LocalDate startDate, LocalDate endDate, String q,
                                                                           org.springframework.data.domain.Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required to access personal audit logs");
        }
        String currentUsername = auth.getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        return findAuditPage(auditSpec(null, currentUsername, currentUserId, entityType, action, date, startDate, endDate, q, false), pageable);
    }

    private org.springframework.data.domain.Page<AuditLogDTO> findAuditPage(org.springframework.data.jpa.domain.Specification<AuditLog> spec,
                                                                           org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Sort newestFirst = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("performedAt"), org.springframework.data.domain.Sort.Order.desc("id"));
        // No page requested: stay bounded like getLogs (newest MAX_LOG_ROWS) so a wide filter never loads the whole table.
        org.springframework.data.domain.Pageable bounded = (pageable == null || pageable.isUnpaged())
                ? org.springframework.data.domain.PageRequest.of(0, MAX_LOG_ROWS, newestFirst)
                : com.neram.pos.util.PagedResponse.withSort(pageable, newestFirst);
        return auditLogRepository.findAll(spec, bounded).map(this::toDTO);
    }

    private org.springframework.data.jpa.domain.Specification<AuditLog> auditSpec(String userFilter, String ownerName, Long ownerId,
                                                                                 String entityType, String action, LocalDate date,
                                                                                 LocalDate startDate, LocalDate endDate, String q, boolean billsOnly) {
        String e = (entityType != null && !entityType.isBlank() && !entityType.equalsIgnoreCase("ALL")) ? entityType.trim() : null;
        String a = (action != null && !action.isBlank() && !action.equalsIgnoreCase("ALL")) ? action.trim() : null;
        LocalDate start = date != null ? date : startDate;
        LocalDate end = date != null ? date : endDate;
        String pattern = com.neram.pos.util.LikeSearch.pattern(q);

        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            if (ownerName != null) {
                // The signed-in user's own rows: by username or user id
                p.add(ownerId != null
                        ? cb.or(cb.equal(cb.lower(root.get("performedByName")), ownerName.toLowerCase()), cb.equal(root.get("performedBy"), ownerId))
                        : cb.equal(cb.lower(root.get("performedByName")), ownerName.toLowerCase()));
            }
            if (userFilter != null) p.add(cb.equal(cb.lower(root.get("performedByName")), userFilter.toLowerCase()));
            if (e != null) p.add(cb.equal(cb.upper(root.get("entityType")), e.toUpperCase()));
            if (a != null) p.add(cb.equal(cb.upper(root.get("action")), a.toUpperCase()));
            if (billsOnly) {
                p.add(cb.or(cb.equal(cb.upper(root.get("entityType")), "BILL"), cb.like(cb.upper(root.<String>get("action")), "%BILL%")));
            }
            if (start != null) p.add(cb.greaterThanOrEqualTo(root.get("performedAt"), start.atStartOfDay()));
            if (end != null) p.add(cb.lessThanOrEqualTo(root.get("performedAt"), end.atTime(LocalTime.MAX)));
            if (pattern != null) {
                p.add(cb.or(
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("action"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("performedByName"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("entityType"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("oldValue"), pattern),
                        com.neram.pos.util.LikeSearch.contains(cb, root.get("newValue"), pattern),
                        cb.like(cb.function("str", String.class, root.get("entityId")), pattern, com.neram.pos.util.LikeSearch.ESCAPE)));
            }
            return cb.and(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private AuditLogDTO toDTO(AuditLog a) {
        return AuditLogDTO.builder()
                .id(a.getId())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .performedBy(a.getPerformedBy())
                .performedByName(a.getPerformedByName())
                .ipAddress(a.getIpAddress())
                .performedAt(a.getPerformedAt())
                .build();
    }
}
