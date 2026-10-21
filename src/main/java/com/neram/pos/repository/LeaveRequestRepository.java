package com.neram.pos.repository;

import com.neram.pos.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeId(Long employeeId);
    List<LeaveRequest> findByStatus(LeaveRequest.Status status);
    long countByStatus(LeaveRequest.Status status);
    List<LeaveRequest> findAllByOrderByCreatedAtDesc();
}
