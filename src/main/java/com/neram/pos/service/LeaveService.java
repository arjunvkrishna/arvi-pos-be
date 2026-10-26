package com.neram.pos.service;

import com.neram.pos.dto.LeaveApplicationRequestDTO;
import com.neram.pos.dto.LeaveResponseDTO;
import com.neram.pos.dto.LeaveReviewRequestDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.LeaveRequest;
import com.neram.pos.entity.SystemNotification;
import com.neram.pos.entity.User;
import com.neram.pos.repository.EmployeeRepository;
import com.neram.pos.repository.LeaveRequestRepository;
import com.neram.pos.repository.SystemNotificationRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final SystemNotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final NotificationStreamService notificationStreamService;

    public User getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                return userRepository.findByUsername(auth.getName()).orElse(null);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Long getAuthenticatedEmployeeId(User user) {
        if (user == null) {
            throw new AccessDeniedException("Authentication required to determine employee identity.");
        }
        if (user.getEmployeeId() != null && user.getEmployeeId() > 0) {
            return user.getEmployeeId();
        }
        Employee emp = employeeRepository.findByNameIgnoreCase(user.getUsername())
                .orElseGet(() -> {
                    Employee newEmp = new Employee();
                    newEmp.setName(user.getUsername());
                    newEmp.setRole(user.getRole() == User.Role.ADMIN ? Employee.Role.ADMIN
                            : (user.getRole() == User.Role.MANAGER ? Employee.Role.MANAGER : Employee.Role.EMPLOYEE));
                    newEmp.setDepartment("Staff");
                    newEmp.setDesignation(user.getRole() == User.Role.MANAGER ? "Shift Manager" : user.getRole().name());
                    newEmp.setJoinedDate(LocalDate.now());
                    newEmp.setActive(true);
                    return employeeRepository.save(newEmp);
                });
        user.setEmployeeId(emp.getId());
        userRepository.save(user);
        return emp.getId();
    }

    public List<LeaveResponseDTO> getAllLeaveRequests() {
        return leaveRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<LeaveResponseDTO> getLeavesByEmployee(Long employeeId) {
        User currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getRole() == User.Role.EMPLOYEE) {
            Long myEmployeeId = getAuthenticatedEmployeeId(currentUser);
            if (employeeId != null && !employeeId.equals(myEmployeeId)) {
                throw new AccessDeniedException("Employees may only view their own leave requests.");
            }
            employeeId = myEmployeeId;
        } else if (employeeId == null && currentUser != null) {
            employeeId = getAuthenticatedEmployeeId(currentUser);
        }
        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID is required.");
        }
        return leaveRepository.findByEmployeeId(employeeId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private static final org.springframework.data.domain.Sort NEWEST_LEAVE_FIRST = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.desc("id"));

    /** One page of the leave requests of every employee (manager / admin view), newest first, optionally only one status. */
    public org.springframework.data.domain.Page<LeaveResponseDTO> getLeaveRequests(String status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Pageable sorted = com.neram.pos.util.PagedResponse.withSort(pageable, NEWEST_LEAVE_FIRST);
        if (status != null && !status.isBlank()) {
            return leaveRepository.findByStatus(LeaveRequest.Status.valueOf(status.toUpperCase()), sorted).map(this::toDTO);
        }
        return leaveRepository.findAll(sorted).map(this::toDTO);
    }

    /** One page of one employee's leave requests, newest first (same access rules as {@link #getLeavesByEmployee(Long)}). */
    public org.springframework.data.domain.Page<LeaveResponseDTO> getLeavesByEmployee(Long employeeId, org.springframework.data.domain.Pageable pageable) {
        Long target = resolveLeaveEmployeeId(employeeId);
        return leaveRepository.findByEmployeeId(target, com.neram.pos.util.PagedResponse.withSort(pageable, NEWEST_LEAVE_FIRST)).map(this::toDTO);
    }

    private Long resolveLeaveEmployeeId(Long employeeId) {
        User currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getRole() == User.Role.EMPLOYEE) {
            Long myEmployeeId = getAuthenticatedEmployeeId(currentUser);
            if (employeeId != null && !employeeId.equals(myEmployeeId)) {
                throw new AccessDeniedException("Employees may only view their own leave requests.");
            }
            employeeId = myEmployeeId;
        } else if (employeeId == null && currentUser != null) {
            employeeId = getAuthenticatedEmployeeId(currentUser);
        }
        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID is required.");
        }
        return employeeId;
    }

    public List<LeaveResponseDTO> getLeavesByStatus(String status) {
        LeaveRequest.Status st = LeaveRequest.Status.valueOf(status.toUpperCase());
        return leaveRepository.findByStatus(st).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeaveResponseDTO applyForLeave(LeaveApplicationRequestDTO dto) {
        User currentUser = getCurrentUser();
        Long targetEmployeeId;
        if (currentUser != null && currentUser.getRole() == User.Role.EMPLOYEE) {
            Long myEmployeeId = getAuthenticatedEmployeeId(currentUser);
            if (dto.getEmployeeId() != null && !dto.getEmployeeId().equals(myEmployeeId)) {
                throw new AccessDeniedException("Employees cannot apply for leave on behalf of other employees.");
            }
            targetEmployeeId = myEmployeeId;
        } else {
            if (dto.getEmployeeId() != null && dto.getEmployeeId() > 0) {
                targetEmployeeId = dto.getEmployeeId();
            } else if (currentUser != null) {
                targetEmployeeId = getAuthenticatedEmployeeId(currentUser);
            } else {
                throw new IllegalArgumentException("Employee ID is required.");
            }
        }

        Employee emp = employeeRepository.findById(targetEmployeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + targetEmployeeId));

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployeeId(emp.getId());
        leave.setFromDate(dto.getFromDate());
        leave.setToDate(dto.getToDate());
        if (dto.getLeaveType() != null) {
            String norm = dto.getLeaveType().toUpperCase().trim().replace(" ", "_").replace("_LEAVE", "");
            try {
                leave.setLeaveType(LeaveRequest.LeaveType.valueOf(norm));
            } catch (Exception e) {
                leave.setLeaveType(LeaveRequest.LeaveType.CASUAL);
            }
        }
        leave.setReason(dto.getReason());
        leave.setStatus(LeaveRequest.Status.PENDING);
        leave.setCreatedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRepository.save(leave);

        // Create detailed leave request notification for Manager / Admin
        String designation = (emp.getDesignation() != null && !emp.getDesignation().isBlank()) ? emp.getDesignation() : (emp.getRole() != null ? emp.getRole().name() : "Staff");
        String dept = (emp.getDepartment() != null && !emp.getDepartment().isBlank()) ? " (" + emp.getDepartment() + ")" : "";
        String leaveTypeName = saved.getLeaveType() != null ? saved.getLeaveType().name().replace("_", " ") + " Leave" : "Leave";
        String dateRange = (saved.getFromDate() != null && saved.getFromDate().equals(saved.getToDate()))
                ? String.valueOf(saved.getFromDate())
                : String.format("%s to %s", saved.getFromDate(), saved.getToDate());

        String title = String.format("📅 Leave Application: %s (%s)", emp.getName(), dateRange);
        String reasonStr = (saved.getReason() != null && !saved.getReason().isBlank()) ? " | Reason: " + saved.getReason().trim() : "";
        String msg = String.format("Staff: %s%s | Role: %s | Dates: %s | Type: %s%s",
                emp.getName(), dept, designation, dateRange, leaveTypeName, reasonStr);

        SystemNotification notification = SystemNotification.builder()
                .type(SystemNotification.NotificationType.LEAVE_REQUEST)
                .title(title)
                .message(msg)
                .referenceId(saved.getId())
                .createdBy(emp.getName())
                .status(SystemNotification.Status.UNREAD)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
        notificationStreamService.publishChangedAfterCommit();

        auditLogService.log("APPLY_LEAVE", "LEAVE", saved.getId(), null, emp.getName() + " applied for leave " + dateRange);
        return toDTO(saved);
    }

    @Transactional
    public LeaveResponseDTO reviewLeave(Long id, LeaveReviewRequestDTO dto) {
        LeaveRequest leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave request not found with id: " + id));

        LeaveRequest.Status newStatus = LeaveRequest.Status.valueOf(dto.getStatus().toUpperCase());
        leave.setStatus(newStatus);
        leave.setReviewNotes(dto.getReviewNotes());
        leave.setReviewedAt(LocalDateTime.now());

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                userRepository.findByUsername(auth.getName()).ifPresent(u -> leave.setReviewedBy(u.getId()));
            }
        } catch (Exception ignored) {}

        LeaveRequest saved = leaveRepository.save(leave);

        // Auto-mark related leave notification as READ when reviewed
        try {
            List<SystemNotification> relatedNotifs = notificationRepository.findByTypeAndReferenceId(SystemNotification.NotificationType.LEAVE_REQUEST, id);
            for (SystemNotification n : relatedNotifs) {
                n.setStatus(SystemNotification.Status.READ);
            }
            if (!relatedNotifs.isEmpty()) {
                notificationRepository.saveAll(relatedNotifs);
            }
        } catch (Exception ignored) {}

        notificationStreamService.publishChangedAfterCommit();
        auditLogService.log("REVIEW_LEAVE", "LEAVE", saved.getId(), null, "Leave status updated to " + saved.getStatus());
        return toDTO(saved);
    }

    @Transactional
    public void deleteLeave(Long id) {
        LeaveRequest leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave request not found with id: " + id));
        leaveRepository.delete(leave);
        auditLogService.log("DELETE_LEAVE", "LEAVE", id, null, "Deleted leave request #" + id);
    }

    public LeaveResponseDTO toDTO(LeaveRequest l) {
        String empName = employeeRepository.findById(l.getEmployeeId())
                .map(Employee::getName)
                .orElse("Unknown Employee");

        String reviewerName = null;
        if (l.getReviewedBy() != null) {
            reviewerName = userRepository.findById(l.getReviewedBy()).map(User::getUsername).orElse(null);
        }

        return LeaveResponseDTO.builder()
                .id(l.getId())
                .employeeId(l.getEmployeeId())
                .employeeName(empName)
                .fromDate(l.getFromDate())
                .toDate(l.getToDate())
                .leaveType(l.getLeaveType().name())
                .reason(l.getReason())
                .status(l.getStatus().name())
                .reviewedBy(l.getReviewedBy())
                .reviewedByName(reviewerName)
                .reviewedAt(l.getReviewedAt())
                .reviewNotes(l.getReviewNotes())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
