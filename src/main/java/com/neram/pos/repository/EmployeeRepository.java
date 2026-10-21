package com.neram.pos.repository;

import com.neram.pos.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByActiveTrue();
    List<Employee> findByRole(Employee.Role role);
    List<Employee> findByRoleAndActiveTrue(Employee.Role role);
    java.util.Optional<Employee> findByNameIgnoreCase(String name);
}
