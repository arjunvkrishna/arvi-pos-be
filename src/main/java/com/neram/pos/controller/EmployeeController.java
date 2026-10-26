package com.neram.pos.controller;

import com.neram.pos.dto.EmployeeDTO;
import com.neram.pos.dto.EmployeeRequestDTO;
import com.neram.pos.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<EmployeeDTO>> getAllEmployees(@RequestParam(required = false) String role, Authentication auth, Pageable pageable) {
        String callerRole = getRoleFromAuth(auth);
        if (role != null && !role.isBlank()) {
            return PagedResponse.of(employeeService.getEmployeesByRole(role, callerRole, pageable));
        }
        return PagedResponse.of(employeeService.getAllEmployees(callerRole, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EmployeeDTO> getEmployeeById(@PathVariable Long id, Authentication auth) {
        String callerRole = getRoleFromAuth(auth);
        return ResponseEntity.ok(employeeService.getEmployeeById(id, callerRole));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EmployeeDTO> createEmployee(@RequestBody EmployeeRequestDTO request, Authentication auth) {
        String callerRole = getRoleFromAuth(auth);
        return ResponseEntity.ok(employeeService.createEmployee(request, callerRole));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EmployeeDTO> updateEmployee(@PathVariable Long id, @RequestBody EmployeeRequestDTO request, Authentication auth) {
        String callerRole = getRoleFromAuth(auth);
        return ResponseEntity.ok(employeeService.updateEmployee(id, request, callerRole));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id, Authentication auth) {
        String callerRole = getRoleFromAuth(auth);
        employeeService.deleteEmployee(id, callerRole);
        return ResponseEntity.noContent().build();
    }

    private String getRoleFromAuth(Authentication auth) {
        if (auth == null) return "ADMIN";
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse("ADMIN");
    }
}
