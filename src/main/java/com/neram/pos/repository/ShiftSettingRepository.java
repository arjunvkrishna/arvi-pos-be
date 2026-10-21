package com.neram.pos.repository;

import com.neram.pos.entity.ShiftSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShiftSettingRepository extends JpaRepository<ShiftSetting, Long> {
}
