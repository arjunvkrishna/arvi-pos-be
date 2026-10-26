package com.neram.pos.service;

import com.neram.pos.entity.User;
import com.neram.pos.repository.UserRepository;
import com.neram.pos.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantSecurityService {

    private final UserRepository userRepository;

    public Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        // SaaS owner accounts live in the control database and have no cafe user row.
        if (TenantContext.current().isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
    }

    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    public boolean isCafeAdmin() {
        return getCurrentUser()
                .map(u -> u.getRole() == User.Role.SUPER_ADMIN || u.getRole() == User.Role.ADMIN || u.getRole() == User.Role.CAFE_ADMIN)
                .orElse(false);
    }

    public boolean isElevatedManager() {
        return getCurrentUser()
                .map(u -> u.getRole() == User.Role.SUPER_ADMIN ||
                          u.getRole() == User.Role.ADMIN ||
                          u.getRole() == User.Role.CAFE_ADMIN ||
                          u.getRole() == User.Role.GENERAL_MANAGER)
                .orElse(false);
    }

    /**
     * The cafe the caller works for. Always the one in the request context (taken from the signed token), never a
     * client-supplied value, so a request cannot reach another cafe's rows by passing a different id.
     */
    public Long resolveTenantId(Long requestedTenantId) {
        return TenantContext.requireId();
    }

    public Long resolveOutletId(Long requestedOutletId) {
        Optional<User> userOpt = getCurrentUser();
        if (userOpt.isEmpty()) {
            return requestedOutletId != null ? requestedOutletId : 1L;
        }
        User user = userOpt.get();
        if (user.getRole() == User.Role.SUPER_ADMIN ||
            user.getRole() == User.Role.ADMIN ||
            user.getRole() == User.Role.CAFE_ADMIN ||
            user.getRole() == User.Role.GENERAL_MANAGER) {
            return requestedOutletId != null ? requestedOutletId : (user.getOutletId() != null ? user.getOutletId() : 1L);
        }
        return user.getOutletId() != null ? user.getOutletId() : 1L;
    }
}
