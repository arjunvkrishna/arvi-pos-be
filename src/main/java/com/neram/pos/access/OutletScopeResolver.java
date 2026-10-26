package com.neram.pos.access;

import com.neram.pos.entity.User;
import com.neram.pos.repository.OutletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides which branch a request is limited to, from who is calling.
 * <ul>
 *   <li>Owner, cafe admin, main (general) manager: whole cafe, or one branch when they pick it (X-Outlet-Id).</li>
 *   <li>Branch manager and all staff: their own branch only; the header is ignored.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OutletScopeResolver {

    private final OutletRepository outletRepository;

    /** @return the outlet to limit the request to, or null for "whole cafe". */
    public Long resolve(CafeUserDetails principal, String requestedOutletHeader) {
        if (principal.getRole() == User.Role.SUPER_ADMIN) {
            return null;
        }
        if (AccessPolicy.hasCafeWideAccess(principal.getRole())) {
            Long requested = parse(requestedOutletHeader);
            if (requested == null) {
                return null;
            }
            if (!outletRepository.existsById(requested)) {
                throw new OutletAccessDeniedException();
            }
            return requested;
        }
        return principal.getOutletId() != null ? principal.getOutletId() : OutletContext.NO_OUTLET;
    }

    /** Branch named by a print bridge (its outlet code); the bridge then only sees that branch's tickets. */
    public Long resolveByCode(String outletCode) {
        if (outletCode == null || outletCode.isBlank()) {
            return null;
        }
        return outletRepository.findAll().stream()
                .filter(o -> outletCode.trim().equalsIgnoreCase(o.getCode()))
                .map(com.neram.pos.entity.Outlet::getId)
                .findFirst()
                .orElseThrow(OutletAccessDeniedException::new);
    }

    private static Long parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
