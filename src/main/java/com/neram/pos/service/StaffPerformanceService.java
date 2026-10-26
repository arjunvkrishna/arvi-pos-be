package com.neram.pos.service;

import com.neram.pos.dto.MoneyReportsDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillRefund;
import com.neram.pos.entity.TipPayout;
import com.neram.pos.entity.User;
import com.neram.pos.repository.BillRefundRepository;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.TipPayoutRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/** How each person is doing (orders, sales, average bill, discounts, refunds) and the tips they received and were handed. */
@Service
@RequiredArgsConstructor
public class StaffPerformanceService {

    private final BillRepository bills;
    private final BillRefundRepository refunds;
    private final TipPayoutRepository payouts;
    private final UserRepository users;
    private final AuditLogService auditLog;

    private static double d(BigDecimal v) { return v == null ? 0.0 : v.doubleValue(); }

    @Transactional(readOnly = true)
    public MoneyReportsDTO.StaffReport report(String period, LocalDate customStart, LocalDate customEnd, LocalDate today) {
        AnalyticsPeriod p = AnalyticsPeriod.resolve(period, customStart, customEnd, today);
        var from = p.start().atStartOfDay(); var to = p.end().atTime(LocalTime.MAX);
        List<Bill> paidBills = bills.findByCreatedAtBetweenAndStatusOrderByCreatedAtDesc(from, to, Bill.Status.PAID);
        List<BillRefund> ok = refunds.findByRefundedAtBetween(from, to).stream().filter(r -> r.getStatus() == BillRefund.RefundStatus.SUCCESS).toList();

        Map<Long, Double> received = new HashMap<>();
        for (Object[] row : bills.sumTipsByCreator()) received.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        Map<Long, Double> paidAll = new HashMap<>();
        for (TipPayout t : payouts.findAll()) paidAll.merge(t.getStaffId(), d(t.getAmount()), Double::sum);
        Map<Long, Double> paidIn = new HashMap<>();
        for (TipPayout t : payouts.findByPaidOnBetweenOrderByPaidOnDescIdDesc(p.start(), p.end())) paidIn.merge(t.getStaffId(), d(t.getAmount()), Double::sum);

        Set<Long> ids = new HashSet<>(received.keySet()); ids.addAll(paidIn.keySet());
        paidBills.stream().map(Bill::getCreatedBy).filter(Objects::nonNull).forEach(ids::add);
        Map<Long, String> names = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        return StaffPerformanceMath.build(paidBills, ok, names, paidIn, received, paidAll, p.start(), p.end());
    }

    @Transactional(readOnly = true)
    public List<MoneyReportsDTO.Payout> recentPayouts() {
        return payouts.findTop100ByOrderByPaidOnDescIdDesc().stream().map(StaffPerformanceService::toDTO).toList();
    }

    /** Records tips handed to a staff member; refuses more than they are owed. */
    @Transactional
    public MoneyReportsDTO.Payout pay(Long staffId, Double amount, String note, String username, LocalDate today) {
        if (staffId == null) throw new IllegalArgumentException("Choose a staff member.");
        if (amount == null || amount.isNaN() || amount.isInfinite() || amount <= 0) throw new IllegalArgumentException("Enter an amount greater than zero.");
        User u = users.findById(staffId).orElseThrow(() -> new IllegalArgumentException("Staff member not found."));
        double received = 0;
        for (Object[] row : bills.sumTipsByCreator()) if (((Number) row[0]).longValue() == staffId) received = ((Number) row[1]).doubleValue();
        double paid = payouts.findAll().stream().filter(t -> staffId.equals(t.getStaffId())).mapToDouble(t -> d(t.getAmount())).sum();
        double owed = Math.round((received - paid) * 100.0) / 100.0;
        if (amount > owed + 0.004) throw new IllegalArgumentException(u.getUsername() + " is owed ₹" + String.format("%.2f", Math.max(0, owed)) + " in tips. You cannot hand over ₹" + String.format("%.2f", amount) + ".");
        TipPayout t = new TipPayout();
        t.setStaffId(staffId); t.setStaffName(u.getUsername());
        t.setAmount(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP));
        t.setPaidOn(today); t.setNote(note == null || note.isBlank() ? null : note.trim().substring(0, Math.min(200, note.trim().length()))); t.setCreatedBy(username);
        t = payouts.save(t);
        auditLog.log("TIP_PAYOUT", "TIP_PAYOUT", t.getId(), null, u.getUsername() + " " + t.getAmount().toPlainString());
        return toDTO(t);
    }

    private static MoneyReportsDTO.Payout toDTO(TipPayout t) {
        return MoneyReportsDTO.Payout.builder().id(t.getId()).staffId(t.getStaffId()).staffName(t.getStaffName()).amount(d(t.getAmount())).paidOn(t.getPaidOn()).note(t.getNote()).createdBy(t.getCreatedBy()).build();
    }
}
