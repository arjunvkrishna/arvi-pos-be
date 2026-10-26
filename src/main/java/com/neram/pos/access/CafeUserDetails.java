package com.neram.pos.access;

import com.neram.pos.entity.User;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/** The logged-in cafe user, with the facts access decisions need (role, home branch). */
public class CafeUserDetails extends org.springframework.security.core.userdetails.User {

    private final Long userId;
    private final User.Role role;
    private final Long outletId;

    public CafeUserDetails(String username, String password, boolean enabled,
                           Collection<? extends GrantedAuthority> authorities,
                           Long userId, User.Role role, Long outletId) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
        this.role = role;
        this.outletId = outletId;
    }

    public Long getUserId() {
        return userId;
    }

    public User.Role getRole() {
        return role;
    }

    public Long getOutletId() {
        return outletId;
    }
}
