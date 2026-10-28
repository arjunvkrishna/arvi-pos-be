package com.neram.pos.service;

import com.neram.pos.entity.GiftCard;
import com.neram.pos.entity.GiftCardTransaction;
import com.neram.pos.repository.CustomerRepository;
import com.neram.pos.repository.GiftCardRepository;
import com.neram.pos.repository.GiftCardTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GiftCardServiceTest {
    @Mock private GiftCardRepository cards;
    @Mock private GiftCardTransactionRepository txns;
    @Mock private CustomerRepository customers;
    @Mock private CashierShiftService shifts;

    private GiftCardService svc;

    @BeforeEach
    void setUp() {
        svc = new GiftCardService(cards, txns, customers, shifts);
        when(cards.save(any(GiftCard.class))).thenAnswer(i -> { GiftCard g = i.getArgument(0); g.setId(9L); return g; });
    }

    private GiftCard card(String balance, GiftCard.Status status) {
        return GiftCard.builder().id(9L).code("GC-AAAA-BBBB-CCCC").initialBalance(new BigDecimal("500"))
                .currentBalance(new BigDecimal(balance)).status(status).paymentMode("CASH").build();
    }

    @Test
    void cashIssueIsRecordedInTheDrawerButUpiIssueIsNot() {
        svc.issueGiftCard(new BigDecimal("500"), "cash", null, null, 1L);
        verify(shifts).recordGiftCardCashSale(new BigDecimal("500"));

        clearInvocations(shifts);
        svc.issueGiftCard(new BigDecimal("300"), "UPI", null, null, 1L);
        verify(shifts, never()).recordGiftCardCashSale(any());
    }

    @Test
    void issueRequiresAPositiveAmountAndAnExplicitMode() {
        assertThrows(IllegalArgumentException.class, () -> svc.issueGiftCard(BigDecimal.ZERO, "CASH", null, null, 1L));
        assertThrows(IllegalArgumentException.class, () -> svc.issueGiftCard(new BigDecimal("10"), " ", null, null, 1L));
    }

    @Test
    void redeemDeductsAtomicallyAndWritesANegativeLedgerRow() {
        when(cards.findByCode("GC-AAAA-BBBB-CCCC")).thenReturn(Optional.of(card("500", GiftCard.Status.ACTIVE)));
        when(cards.deductBalanceSafely(eq(9L), any(), any())).thenReturn(1);

        svc.redeem("gc-aaaa-bbbb-cccc", new BigDecimal("120"), 77L);

        verify(txns).save(argThat(t -> t.getAmount().compareTo(new BigDecimal("-120")) == 0
                && t.getBalanceAfter().compareTo(new BigDecimal("380")) == 0 && t.getBillId() == 77L));
    }

    @Test
    void redeemFailsWhenBalanceIsInsufficientOrCardIsNotActive() {
        when(cards.findByCode("GC-AAAA-BBBB-CCCC")).thenReturn(Optional.of(card("50", GiftCard.Status.ACTIVE)));
        when(cards.deductBalanceSafely(eq(9L), any(), any())).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> svc.redeem("GC-AAAA-BBBB-CCCC", new BigDecimal("120"), 1L));

        when(cards.findByCode("GC-AAAA-BBBB-CCCC")).thenReturn(Optional.of(card("0", GiftCard.Status.EXHAUSTED)));
        assertThrows(IllegalArgumentException.class, () -> svc.redeem("GC-AAAA-BBBB-CCCC", BigDecimal.ONE, 1L));
        verify(txns, never()).save(any());
    }

    @Test
    void expiredCardIsRejected() {
        GiftCard c = card("100", GiftCard.Status.ACTIVE);
        c.setExpiresAt(LocalDate.now().minusDays(1));
        when(cards.findByCode("GC-AAAA-BBBB-CCCC")).thenReturn(Optional.of(c));
        assertThrows(IllegalArgumentException.class, () -> svc.redeem("GC-AAAA-BBBB-CCCC", BigDecimal.ONE, 1L));
    }

    @Test
    void refundRestoresTheBalanceOnceAndIsIdempotent() {
        GiftCardTransaction spent = GiftCardTransaction.builder().giftCardId(9L).billId(5L)
                .amount(new BigDecimal("-120")).balanceAfter(new BigDecimal("380")).build();
        when(txns.findByBillId(5L)).thenReturn(List.of(spent));
        when(cards.findById(9L)).thenReturn(Optional.of(card("500", GiftCard.Status.ACTIVE)));

        svc.refundForBill(5L);
        verify(cards, times(1)).restoreBalanceSafely(9L, new BigDecimal("120"));

        // a second refund call sees the positive restore row and does nothing
        GiftCardTransaction restored = GiftCardTransaction.builder().giftCardId(9L).billId(5L)
                .amount(new BigDecimal("120")).balanceAfter(new BigDecimal("500")).build();
        when(txns.findByBillId(5L)).thenReturn(List.of(spent, restored));
        svc.refundForBill(5L);
        verify(cards, times(1)).restoreBalanceSafely(anyLong(), any());
    }

    @Test
    void generatedCodesUseTheSafeAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = GiftCardService.generateCode();
            assertTrue(code.matches("GC-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}"), code);
        }
    }
}
