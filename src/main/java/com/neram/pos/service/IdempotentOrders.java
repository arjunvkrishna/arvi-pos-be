package com.neram.pos.service;

import com.neram.pos.dto.BillResponseDTO;
import com.neram.pos.dto.TableOrderRequestDTO;
import com.neram.pos.entity.IdempotencyKey;
import com.neram.pos.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Saves an order once per Idempotency-Key. The same key again returns the bill the first request produced instead of saving a second time.
 * The orders screen sends a key when it replays an order that was taken while the connection was down.
 */
@Service
@RequiredArgsConstructor
public class IdempotentOrders {
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:-]{8,80}$");
    private static final int KEEP_DAYS = 14;

    private final IdempotencyKeyRepository keys;
    private final BillService billService;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public BillResponseDTO save(String key, TableOrderRequestDTO request) {
        if (key == null || key.isBlank()) return billService.saveOrUpdateTableOrder(request);
        String k = key.trim();
        if (!VALID.matcher(k).matches()) throw new IllegalArgumentException("Idempotency-Key must be 8 to 80 letters, digits, dots, dashes, colons or underscores.");
        synchronized (locks.computeIfAbsent(k, x -> new Object())) {
            Optional<IdempotencyKey> hit = keys.findById(k);
            if (hit.isPresent()) return billService.getBillById(hit.get().getBillId());
            BillResponseDTO saved = billService.saveOrUpdateTableOrder(request);
            keys.save(new IdempotencyKey(k, saved.getId(), LocalDateTime.now()));
            keys.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(KEEP_DAYS));
            return saved;
        }
    }
}
