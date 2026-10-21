package com.neram.pos.service;

import com.neram.pos.entity.User;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /** Marker authority present while the account still has a temporary password that must be changed. */
    public static final String PASSWORD_CHANGE_REQUIRED = "PWD_CHANGE_REQUIRED";

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        boolean isEnabled = user.getActive() == null || Boolean.TRUE.equals(user.getActive());
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        // Role SUPER_ADMIN, ADMIN, CAFE_ADMIN and MANAGER have all permissions by default
        boolean isElevated = user.getRole() == User.Role.SUPER_ADMIN ||
                             user.getRole() == User.Role.ADMIN ||
                             user.getRole() == User.Role.CAFE_ADMIN ||
                             user.getRole() == User.Role.MANAGER;

        boolean canTakeOrders = isElevated || Boolean.TRUE.equals(user.getCanTakeOrders());
        boolean canManageInventory = isElevated || Boolean.TRUE.equals(user.getCanManageInventory());

        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            authorities.add(new SimpleGrantedAuthority(PASSWORD_CHANGE_REQUIRED));
        }
        if (canTakeOrders) {
            authorities.add(new SimpleGrantedAuthority("PERM_TAKE_ORDERS"));
        }
        if (canManageInventory) {
            authorities.add(new SimpleGrantedAuthority("PERM_MANAGE_INVENTORY"));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                isEnabled,
                true,
                true,
                true,
                authorities
        );
    }
}
