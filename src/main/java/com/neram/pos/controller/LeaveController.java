package com.neram.pos.controller;

import com.neram.pos.dto.LeaveApplicationRequestDTO;
import com.neram.pos.dto.LeaveResponseDTO;
import com.neram.pos.dto.LeaveReviewRequestDTO;
import com.neram.pos.service.LeaveService;
import lombok.RequiredArgsConstructor;
import com.neram.pos.util.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
@com.neram.pos.plan.RequiresFeature(com.neram.pos.plan.Feature.STAFF_PAYROLL)
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LeaveResponseDTO> applyForLeave(@RequestBody LeaveApplicationRequestDTO request) {
        return ResponseEntity.ok(leaveService.applyForLeave(request));
    }

    @GetMapping({"/employee/{employeeId}", "/employee"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<LeaveResponseDTO>> getLeavesByEmployee(@PathVariable(required = false) Long employeeId, Pageable pageable) {
        return PagedResponse.of(leaveService.getLeavesByEmployee(employeeId, pageable));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<LeaveResponseDTO>> getLeaveAudit(@RequestParam(required = false) String status, Pageable pageable) {
        return PagedResponse.of(leaveService.getLeaveRequests(status, pageable));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<LeaveResponseDTO> reviewLeave(@PathVariable Long id, @RequestBody LeaveReviewRequestDTO request) {
        return ResponseEntity.ok(leaveService.reviewLeave(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteLeave(@PathVariable Long id) {
        leaveService.deleteLeave(id);
        return ResponseEntity.noContent().build();
    }
}
