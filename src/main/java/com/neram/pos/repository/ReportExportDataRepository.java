package com.neram.pos.repository;

import com.neram.pos.entity.ReportExportData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportExportDataRepository extends JpaRepository<ReportExportData, UUID> {
}
