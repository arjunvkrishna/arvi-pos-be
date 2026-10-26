package com.neram.pos.service;

import com.neram.pos.dto.BillResponseDTO;
import com.neram.pos.dto.TableOrderRequestDTO;
import com.neram.pos.entity.IdempotencyKey;
import com.neram.pos.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Sending the same order twice (a lost reply, a retry after reconnecting) must never create it twice. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotentOrdersTest {
    @Mock private IdempotencyKeyRepository keys;
    @Mock private BillService billService;
    private IdempotentOrders orders;
    private final Map<String, IdempotencyKey> store = new ConcurrentHashMap<>();
    private final AtomicLong nextBill = new AtomicLong(100);

    private static BillResponseDTO bill(long id) { BillResponseDTO b = new BillResponseDTO(); b.setId(id); return b; }

    @BeforeEach
    void setUp() {
        orders = new IdempotentOrders(keys, billService);
        when(keys.findById(anyString())).thenAnswer(a -> Optional.ofNullable(store.get(a.<String>getArgument(0))));
        when(keys.save(any(IdempotencyKey.class))).thenAnswer(a -> { IdempotencyKey k = a.getArgument(0); store.put(k.getKey(), k); return k; });
        when(billService.saveOrUpdateTableOrder(any())).thenAnswer(a -> bill(nextBill.getAndIncrement()));
        when(billService.getBillById(anyLong())).thenAnswer(a -> bill(a.getArgument(0)));
    }

    @Test
    void theSameKeyTwiceSavesOnceAndReturnsTheSameBill() {
        BillResponseDTO first = orders.save("offline-0001-abcd", new TableOrderRequestDTO());
        BillResponseDTO again = orders.save("offline-0001-abcd", new TableOrderRequestDTO());
        assertEquals(first.getId(), again.getId());
        verify(billService, times(1)).saveOrUpdateTableOrder(any());
    }

    @Test
    void differentKeysSaveSeparately() {
        BillResponseDTO a = orders.save("offline-0001-abcd", new TableOrderRequestDTO());
        BillResponseDTO b = orders.save("offline-0002-abcd", new TableOrderRequestDTO());
        assertNotEquals(a.getId(), b.getId());
        verify(billService, times(2)).saveOrUpdateTableOrder(any());
    }

    @Test
    void noKeyBehavesAsBefore() {
        orders.save(null, new TableOrderRequestDTO()); orders.save("  ", new TableOrderRequestDTO()); orders.save(null, new TableOrderRequestDTO());
        verify(billService, times(3)).saveOrUpdateTableOrder(any());
        verify(keys, never()).save(any());
    }

    @Test
    void aMalformedKeyIsRefusedAndNothingIsSaved() {
        for (String bad : new String[]{"short", "has space in it 123", "x".repeat(81), "semi;colon;key;12345"}) assertThrows(IllegalArgumentException.class, () -> orders.save(bad, new TableOrderRequestDTO()), bad);
        verify(billService, never()).saveOrUpdateTableOrder(any());
    }

    @Test
    void aFailedSaveIsNotRememberedSoTheRetryCanSucceed() {
        when(billService.saveOrUpdateTableOrder(any())).thenThrow(new IllegalArgumentException("Table is busy")).thenAnswer(a -> bill(7));
        assertThrows(IllegalArgumentException.class, () -> orders.save("offline-0003-abcd", new TableOrderRequestDTO()));
        assertTrue(store.isEmpty());
        assertEquals(7L, orders.save("offline-0003-abcd", new TableOrderRequestDTO()).getId());
    }

    @Test
    void twoSimultaneousRepliesOfTheSameOrderSaveOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Long> call = () -> { go.await(); return orders.save("offline-0004-abcd", new TableOrderRequestDTO()).getId(); };
        Future<Long> a = pool.submit(call), b = pool.submit(call);
        go.countDown();
        assertEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
        verify(billService, times(1)).saveOrUpdateTableOrder(any());
        pool.shutdownNow();
    }

    @Test
    void oldKeysAreCleanedUp() {
        orders.save("offline-0005-abcd", new TableOrderRequestDTO());
        verify(keys).deleteByCreatedAtBefore(any());
    }
}
