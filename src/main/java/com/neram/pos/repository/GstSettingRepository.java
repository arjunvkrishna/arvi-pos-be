package com.neram.pos.repository;

import com.neram.pos.entity.GstSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GstSettingRepository extends JpaRepository<GstSetting, Long> {
}
