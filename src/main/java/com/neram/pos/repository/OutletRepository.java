package com.neram.pos.repository;

import com.neram.pos.entity.Outlet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutletRepository extends JpaRepository<Outlet, Long> {
    List<Outlet> findByTenantId(Long tenantId);
    List<Outlet> findByTenantIdAndIsActiveTrue(Long tenantId);
    Optional<Outlet> findByTenantIdAndCode(Long tenantId, String code);
    long countByTenantId(Long tenantId);
}
