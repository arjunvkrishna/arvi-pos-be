package com.neram.pos.repository;

import com.neram.pos.entity.PrinterSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrinterSettingRepository extends JpaRepository<PrinterSetting, Long> {
    java.util.Optional<PrinterSetting> findFirstByOrderByIdAsc();
}
