package com.neram.pos.repository;

import com.neram.pos.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByEmployeeId(Long employeeId);
    java.util.List<User> findByRoleAndActiveTrue(User.Role role);
    java.util.List<User> findByRoleInAndActiveTrue(java.util.List<User.Role> roles);
    org.springframework.data.domain.Page<User> findByRoleInAndActiveTrue(java.util.List<User.Role> roles, org.springframework.data.domain.Pageable pageable);
    boolean existsByRole(User.Role role);
    org.springframework.data.domain.Page<User> findByRoleNot(User.Role role, org.springframework.data.domain.Pageable pageable);
}
