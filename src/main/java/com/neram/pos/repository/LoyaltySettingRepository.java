package com.neram.pos.repository;

import com.neram.pos.entity.LoyaltySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoyaltySettingRepository extends JpaRepository<LoyaltySetting, Long> {
}
