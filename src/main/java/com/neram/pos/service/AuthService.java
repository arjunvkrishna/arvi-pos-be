package com.neram.pos.service;

import com.neram.pos.access.AccessPolicy;
import com.neram.pos.config.JwtTokenProvider;
import com.neram.pos.dto.LoginRequest;
import com.neram.pos.dto.LoginResponse;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import com.neram.pos.tenancy.PlatformAdminService;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;
    private final com.neram.pos.repository.TenantRepository tenantRepository;
    private final com.neram.pos.repository.OutletRepository outletRepository;
    private final LoginAttemptService loginAttemptService;

    /** Separates the cafe code from the random part in the refresh token handed to clients. */
    public static final char REFRESH_SEPARATOR = '~';

    private final TenantRegistry tenantRegistry;
    private final PlatformAdminService platformAdminService;
    private final com.neram.pos.plan.PlanService planService;
    private final PlatformLoginThrottle platformThrottle = new PlatformLoginThrottle();

    /**
     * Logs in a cafe user (cafe code + username + password) or, when no cafe code is given, a SaaS owner account.
     * The cafe code picks which cafe database the credentials are checked against.
     */
    public LoginResponse login(LoginRequest request) {
        return login(request, null);
    }

    /** @param clientIp the caller's address, used for lockout and audit; may be null in tests / internal calls */
    public LoginResponse login(LoginRequest request, String clientIp) {
        String code = request.getTenantCode() == null ? "" : request.getTenantCode().trim().toLowerCase();
        if (code.isEmpty()) {
            return platformLogin(request, clientIp);
        }
        TenantRecord tenant = tenantRegistry.findByCode(code)
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        if (!tenant.isActive()) {
            throw new BadCredentialsException("This cafe is not active. Please contact support.");
        }
        return TenantContext.callAs(tenant.info(), () -> cafeLogin(request, tenant, clientIp));
    }

    private LoginResponse cafeLogin(LoginRequest request, TenantRecord tenant, String clientIp) {
        // Lockout state lives in the cafe's own database, so one cafe's failed logins never lock another cafe's users.
        loginAttemptService.assertNotLocked(request.getUsername(), clientIp);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            long lockedSeconds = loginAttemptService.recordFailure(request.getUsername(), clientIp);
            if (lockedSeconds > 0) {
                throw new LoginAttemptService.AccountLockedException(lockedSeconds);   // this attempt just locked it
            }
            throw e;
        }
        loginAttemptService.recordSuccess(request.getUsername(), clientIp);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication, tenant.id(), tenant.code());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        auditLogService.log("LOGIN", "USER", user.getId(), null, "User logged in: " + user.getUsername(), user.getId(), user.getUsername());
        return buildLoginResponse(user, jwt, wrapRefresh(tenant.code(), refreshTokenService.issue(user)), "Login successful");
    }

    private LoginResponse platformLogin(LoginRequest request, String clientIp) {
        String throttleKey = (request.getUsername() == null ? "" : request.getUsername().toLowerCase()) + "|" + clientIp;
        platformThrottle.assertNotLocked(throttleKey);
        PlatformAdminService.PlatformAdmin admin = platformAdminService
                .authenticate(request.getUsername(), request.getPassword())
                .orElseThrow(() -> {
                    platformThrottle.recordFailure(throttleKey);
                    return new BadCredentialsException("Bad credentials");
                });
        platformThrottle.recordSuccess(throttleKey);
        return LoginResponse.builder()
                .token(tokenProvider.generatePlatformToken(admin.username()))
                .username(admin.username())
                .role(User.Role.SUPER_ADMIN.name())
                .employeeName(admin.fullName() != null ? admin.fullName() : admin.username())
                .canTakeOrders(true)
                .canManageInventory(true)
                .canCancelOrder(true)
                .mustChangePassword(admin.mustChangePassword())
                .message("Login successful")
                .build();
    }

    /** Exchanges a valid refresh token for a new access token and a rotated refresh token. */
    public LoginResponse refresh(String refreshToken) {
        String[] parts = splitRefresh(refreshToken);
        TenantRecord tenant = tenantRegistry.findByCode(parts[0])
                .filter(TenantRecord::isActive)
                .orElseThrow(() -> new RefreshTokenService.InvalidRefreshTokenException("Invalid refresh token"));
        return TenantContext.callAs(tenant.info(), () -> {
            RefreshTokenService.Rotation rotation = refreshTokenService.rotate(parts[1]);
            User user = rotation.user();
            String jwt = tokenProvider.generateTokenFromUsername(user.getUsername(), tenant.id(), tenant.code());
            return buildLoginResponse(user, jwt, wrapRefresh(tenant.code(), rotation.refreshToken()), "Token refreshed");
        });
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.indexOf(REFRESH_SEPARATOR) < 0) {
            return;
        }
        String[] parts = splitRefresh(refreshToken);
        tenantRegistry.findByCode(parts[0]).ifPresent(tenant ->
                TenantContext.runAs(tenant.info(), () -> refreshTokenService.revoke(parts[1])));
    }

    static String wrapRefresh(String tenantCode, String rawToken) {
        return tenantCode + REFRESH_SEPARATOR + rawToken;
    }

    static String[] splitRefresh(String refreshToken) {
        int i = refreshToken == null ? -1 : refreshToken.indexOf(REFRESH_SEPARATOR);
        if (i <= 0 || i == refreshToken.length() - 1) {
            throw new RefreshTokenService.InvalidRefreshTokenException("Invalid refresh token");
        }
        return new String[]{refreshToken.substring(0, i), refreshToken.substring(i + 1)};
    }

    private LoginResponse buildLoginResponse(User user, String jwt, String refreshToken, String message) {
        String employeeName = user.getUsername();
        if (user.getEmployeeId() != null) {
            Employee emp = employeeRepository.findById(user.getEmployeeId()).orElse(null);
            if (emp != null) {
                employeeName = emp.getName();
            }
        }

        boolean isElevated = user.getRole() == User.Role.SUPER_ADMIN ||
                             user.getRole() == User.Role.ADMIN ||
                             user.getRole() == User.Role.CAFE_ADMIN ||
                             user.getRole() == User.Role.GENERAL_MANAGER ||
                             user.getRole() == User.Role.MANAGER;
        boolean canTakeOrders = isElevated || Boolean.TRUE.equals(user.getCanTakeOrders());
        boolean canManageInventory = isElevated || Boolean.TRUE.equals(user.getCanManageInventory());
        boolean canCancelOrder = isElevated || Boolean.TRUE.equals(user.getCanCancelOrder());

        String tenantName = null;
        String tenantCode = null;
        String logoUrl = null;
        if (user.getTenantId() != null) {
            var tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
            if (tenant != null) {
                tenantName = tenant.getName();
                tenantCode = tenant.getCode();
                logoUrl = tenant.getLogoUrl();
            }
        }

        String outletName = null;
        String outletCode = null;
        if (user.getOutletId() != null) {
            var outlet = outletRepository.findById(user.getOutletId()).orElse(null);
            if (outlet != null) {
                outletName = outlet.getName();
                outletCode = outlet.getCode();
            }
        }

        return LoginResponse.builder()
                .token(jwt)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .role(user.getRole().name())
                .employeeId(user.getEmployeeId())
                .employeeName(employeeName)
                .canTakeOrders(canTakeOrders)
                .canManageInventory(canManageInventory)
                .canCancelOrder(canCancelOrder)
                .canPrintBill(isElevated || Boolean.TRUE.equals(user.getCanPrintBill()))
                .canManageMenu(AccessPolicy.managementAuthorities(user).contains(AccessPolicy.PERM_MANAGE_MENU))
                .canManageSettings(AccessPolicy.managementAuthorities(user).contains(AccessPolicy.PERM_MANAGE_OUTLET_SETTINGS))
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .tenantId(user.getTenantId())
                .tenantName(tenantName)
                .tenantCode(tenantCode)
                .logoUrl(logoUrl)
                .outletId(user.getOutletId())
                .outletName(outletName)
                .outletCode(outletCode)
                .plan(planService.summary())
                .message(message)
                .build();
    }
}
