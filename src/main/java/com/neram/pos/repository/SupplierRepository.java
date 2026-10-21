package com.neram.pos.repository;

import com.neram.pos.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByActiveTrue();
    List<Supplier> findByCategoryAndActiveTrue(String category);
    boolean existsByNameIgnoreCase(String name);
}
