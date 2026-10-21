package com.neram.pos.repository;

import com.neram.pos.entity.TelegramSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramSettingRepository extends JpaRepository<TelegramSetting, Long> {
    default Optional<TelegramSetting> getSettings() {
        return findById(1L);
    }
}
