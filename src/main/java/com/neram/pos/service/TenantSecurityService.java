package com.neram.pos.service;

import com.neram.pos.entity.User;
import com.neram.pos.repository.UserRepository;
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
        return userRepository.findByUsername(auth.getName());
    }

    public boolean isSuperAdmin() {
        return getCurrentUser()
                .map(u -> u.getRole() == User.Role.SUPER_ADMIN)
                .orElse(false);
    }

    public boolean isCafeAdmin() {
        return getCurrentUser()
                .map(u -> u.getRole() == User.Role.SUPER_ADMIN || u.getRole() == User.Role.ADMIN || u.getRole() == User.Role.CAFE_ADMIN)
                .orElse(false);
    }

    public Long resolveTenantId(Long requestedTenantId) {
        Optional<User> userOpt = getCurrentUser();
        if (userOpt.isEmpty()) {
            return requestedTenantId != null ? requestedTenantId : 1L;
        }
        User user = userOpt.get();
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            return requestedTenantId != null ? requestedTenantId : 1L;
        }
        return user.getTenantId() != null ? user.getTenantId() : 1L;
    }

    public Long resolveOutletId(Long requestedOutletId) {
        Optional<User> userOpt = getCurrentUser();
        if (userOpt.isEmpty()) {
            return requestedOutletId != null ? requestedOutletId : 1L;
        }
        User user = userOpt.get();
        if (user.getRole() == User.Role.SUPER_ADMIN || user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.CAFE_ADMIN) {
            return requestedOutletId != null ? requestedOutletId : (user.getOutletId() != null ? user.getOutletId() : 1L);
        }
        return user.getOutletId() != null ? user.getOutletId() : 1L;
    }
}
