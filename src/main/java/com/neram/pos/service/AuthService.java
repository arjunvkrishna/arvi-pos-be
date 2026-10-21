package com.neram.pos.service;

import com.neram.pos.config.JwtTokenProvider;
import com.neram.pos.dto.LoginRequest;
import com.neram.pos.dto.LoginResponse;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
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

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        auditLogService.log("LOGIN", "USER", user.getId(), null, "User logged in: " + user.getUsername(), user.getId(), user.getUsername());
        return buildLoginResponse(user, jwt, refreshTokenService.issue(user), "Login successful");
    }

    /** Exchanges a valid refresh token for a new access token and a rotated refresh token. */
    public LoginResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        User user = rotation.user();
        String jwt = tokenProvider.generateTokenFromUsername(user.getUsername());
        return buildLoginResponse(user, jwt, rotation.refreshToken(), "Token refreshed");
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
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
                             user.getRole() == User.Role.MANAGER;
        boolean canTakeOrders = isElevated || Boolean.TRUE.equals(user.getCanTakeOrders());
        boolean canManageInventory = isElevated || Boolean.TRUE.equals(user.getCanManageInventory());

        String tenantName = null;
        String tenantCode = null;
        if (user.getTenantId() != null) {
            var tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
            if (tenant != null) {
                tenantName = tenant.getName();
                tenantCode = tenant.getCode();
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
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .tenantId(user.getTenantId())
                .tenantName(tenantName)
                .tenantCode(tenantCode)
                .outletId(user.getOutletId())
                .outletName(outletName)
                .outletCode(outletCode)
                .message(message)
                .build();
    }
}
