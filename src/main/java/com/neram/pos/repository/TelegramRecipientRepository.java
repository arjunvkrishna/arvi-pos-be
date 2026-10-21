package com.neram.pos.repository;

import com.neram.pos.entity.TelegramRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramRecipientRepository extends JpaRepository<TelegramRecipient, Long> {

    Optional<TelegramRecipient> findByUserId(Long userId);

    Optional<TelegramRecipient> findByLinkCode(String linkCode);

    Optional<TelegramRecipient> findByTelegramChatId(String chatId);

    List<TelegramRecipient> findBySelectedTrueAndStatus(TelegramRecipient.Status status);

    long countBySelectedTrueAndStatus(TelegramRecipient.Status status);
}
