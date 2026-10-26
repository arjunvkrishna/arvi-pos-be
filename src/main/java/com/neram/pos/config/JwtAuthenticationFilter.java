package com.neram.pos.config;

import com.neram.pos.service.CustomUserDetailsService;
import com.neram.pos.tenancy.PlatformAdminService;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates the request and selects the cafe it works on.
 * <ul>
 *   <li>Cafe token: the cafe comes from the signed token (never from a header), must still be ACTIVE, and the user
 *       is loaded from that cafe's own database.</li>
 *   <li>Platform token: a SaaS owner; no cafe is selected, so cafe data stays unreachable.</li>
 *   <li>No token: only the public menu and print bridge endpoints are open, and they name the cafe in the URL
 *       ({@code /api/public/{cafe}/...}) or the {@code X-Tenant-Code} header.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Code";
    public static final String OUTLET_HEADER = "X-Outlet-Id";
    /** Print bridges of multi-branch cafes name their branch so they only receive that branch's tickets. */
    public static final String OUTLET_CODE_HEADER = "X-Outlet-Code";

    private static final Pattern PUBLIC_CAFE_PATH = Pattern.compile("^/api/public/([a-z0-9][a-z0-9-]{1,28}[a-z0-9])/.*");

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final TenantRegistry tenantRegistry;
    private final PlatformAdminService platformAdminService;
    private final com.neram.pos.access.OutletScopeResolver outletScopeResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = getTokenFromRequest(request);
            if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
                if (!authenticate(token, request, response)) {
                    return;
                }
            } else if (isCafeScopedAnonymousEndpoint(request.getRequestURI())) {
                if (!selectTenantFromRequest(request, response)) {
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            com.neram.pos.access.OutletContext.clear();
            TenantContext.clear();
            MDC.remove("tenant");
        }
    }

    /** @return false when a response has already been written and the chain must stop. */
    private boolean authenticate(String token, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            JwtTokenProvider.TokenIdentity identity = tokenProvider.getIdentity(token);
            UserDetails userDetails;
            if (identity.platform()) {
                Optional<PlatformAdminService.PlatformAdmin> admin = platformAdminService.findByUsername(identity.username());
                if (admin.isEmpty() || !admin.get().active()) {
                    return true; // unauthenticated; the entry point answers 401
                }
                userDetails = platformAdminService.toUserDetails(admin.get());
            } else {
                if (identity.tenantId() == null) {
                    return true; // legacy token without a cafe: not accepted
                }
                Optional<TenantRecord> tenant = tenantRegistry.findById(identity.tenantId());
                if (tenant.isEmpty() || !tenant.get().code().equals(identity.tenantCode())) {
                    return true;
                }
                if (!tenant.get().isActive()) {
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                            "{\"error\":\"This cafe is not active. Please contact support.\"}");
                    return false;
                }
                TenantContext.set(tenant.get().info());
                MDC.put("tenant", tenant.get().code());
                userDetails = userDetailsService.loadUserByUsername(identity.username());
                if (isPasswordChangePending(userDetails) && !isAllowedWhilePasswordChangePending(request)) {
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                            "{\"error\":\"Password change required. Please set a new password before continuing.\"}");
                    return false;
                }
                if (userDetails instanceof com.neram.pos.access.CafeUserDetails cafeUser) {
                    try {
                        com.neram.pos.access.OutletContext.set(
                                outletScopeResolver.resolve(cafeUser, request.getHeader(OUTLET_HEADER)));
                    } catch (com.neram.pos.access.OutletAccessDeniedException e) {
                        writeJson(response, HttpServletResponse.SC_FORBIDDEN, "{\"error\":\"Unknown branch\"}");
                        return false;
                    }
                }
            }
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
            MDC.put("user", identity.username());
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }
        return true;
    }

    private boolean selectTenantFromRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String code = null;
        Matcher m = PUBLIC_CAFE_PATH.matcher(request.getRequestURI());
        if (m.matches()) {
            code = m.group(1);
        } else if (StringUtils.hasText(request.getHeader(TENANT_HEADER))) {
            code = request.getHeader(TENANT_HEADER).trim().toLowerCase();
        }
        if (code == null) {
            return true; // controller fails closed with "no cafe selected"
        }
        Optional<TenantRecord> tenant = tenantRegistry.findByCode(code);
        if (tenant.isEmpty()) {
            writeJson(response, HttpServletResponse.SC_NOT_FOUND, "{\"error\":\"Unknown cafe\"}");
            return false;
        }
        if (!tenant.get().isActive()) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "{\"error\":\"This cafe is not active\"}");
            return false;
        }
        TenantContext.set(tenant.get().info());
        MDC.put("tenant", tenant.get().code());
        String outletCode = request.getHeader(OUTLET_CODE_HEADER);
        if (outletCode != null && !outletCode.isBlank() && request.getRequestURI().startsWith("/api/printer/bridge/")) {
            try {
                com.neram.pos.access.OutletContext.set(outletScopeResolver.resolveByCode(outletCode));
            } catch (com.neram.pos.access.OutletAccessDeniedException e) {
                writeJson(response, HttpServletResponse.SC_NOT_FOUND, "{\"error\":\"Unknown branch\"}");
                return false;
            }
        }
        return true;
    }

    private static boolean isCafeScopedAnonymousEndpoint(String path) {
        return path != null && (path.startsWith("/api/public/") || path.startsWith("/api/printer/bridge/"));
    }

    private static void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(body);
    }

    private static boolean isPasswordChangePending(UserDetails user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> CustomUserDetailsService.PASSWORD_CHANGE_REQUIRED.equals(a.getAuthority()));
    }

    /** While a temporary password is active only the change-password flow (and reading own profile) is usable. */
    static boolean isAllowedWhilePasswordChangePending(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path == null) return false;
        if (path.startsWith("/api/auth/") || path.equals("/api/health")) return true;
        if (path.equals("/api/users/me/change-password") && ("PATCH".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))) return true;
        return path.equals("/api/users/me") && "GET".equalsIgnoreCase(method);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
