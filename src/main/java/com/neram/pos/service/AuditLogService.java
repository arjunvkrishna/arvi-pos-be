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
