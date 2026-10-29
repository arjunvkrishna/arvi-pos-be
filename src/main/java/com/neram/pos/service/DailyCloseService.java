package com.neram.pos.service;

import com.neram.pos.dto.InsightsDTO;
import com.neram.pos.dto.MoneyReportsDTO;
import com.neram.pos.entity.CafeExpense;
import com.neram.pos.entity.CashierShift;
import com.neram.pos.repository.CafeExpenseRepository;
import com.neram.pos.repository.CashierShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** One page for the end of the day: sales, losses, cash drawer, expenses, stock and budget, built from the data already recorded. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyCloseService {

    static final String KEY_ENABLED = "DAILY_CLOSE_ENABLED";
    static final String KEY_TIME = "DAILY_CLOSE_TIME";
    static final String KEY_LAST_SENT = "DAILY_CLOSE_LAST_SENT";
    static final String DEFAULT_TIME = "23:30";
    private static final Pattern HHMM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final InsightsService insights;
    private final CashierShiftRepository shiftRepository;
    private final CashierShiftService shiftService;
    private final CafeExpenseRepository expenses;
    private final BudgetService budgetService;
    private final AppSettingService settings;
    private final TelegramNotificationService telegram;
    private final StaffPerformanceService staffService;
    private final org.springframework.context.ApplicationEventPublisher events;

    private static double d(BigDecimal v) { return v == null ? 0.0 : v.doubleValue(); }

    @Transactional(readOnly = true)
    public MoneyReportsDTO.DailyClose build(LocalDate date, LocalDate today) {
        if (date == null) date = today;
        if (date.isAfter(today)) throw new IllegalArgumentException("That day has not happened yet.");
        InsightsDTO i = insights.getInsights("CUSTOM", date, date);
        InsightsDTO.Compare c = i.getCompare();

        List<MoneyReportsDTO.ShiftLine> shifts = new ArrayList<>();
        double variance = 0; int open = 0;
        for (CashierShift s : shiftRepository.findByOpenedAtBetweenOrderByOpenedAtDesc(date.atStartOfDay(), date.atTime(LocalTime.MAX))) {
            boolean isOpen = s.getStatus() == CashierShift.ShiftStatus.OPEN;
            Double expected = isOpen ? Double.valueOf(d(shiftService.calculateExpectedCash(s))) : (s.getExpectedCash() != null ? Double.valueOf(d(s.getExpectedCash())) : null);
            Double v = s.getIsCorrected() != null && s.getIsCorrected() && s.getCorrectedVariance() != null ? Double.valueOf(d(s.getCorrectedVariance())) : (s.getVariance() != null ? Double.valueOf(d(s.getVariance())) : null);
            if (isOpen) open++; else if (v != null) variance += v;
            shifts.add(MoneyReportsDTO.ShiftLine.builder().id(s.getId()).cashier(s.getCashierName()).openedAt(s.getOpenedAt()).closedAt(s.getClosedAt()).status(s.getStatus().name())
                    .openingFloat(d(s.getOpeningFloat())).cashSales(d(s.getCashSales())).expectedCash(expected).countedCash(s.getCountedCash() != null ? d(s.getCountedCash()) : null).variance(v).build());
        }
        shifts.sort(Comparator.comparing(MoneyReportsDTO.ShiftLine::getOpenedAt));

        double spent = 0; Map<String, Double> byType = new HashMap<>();
        for (CafeExpense e : expenses.findByExpenseDateBetween(date, date)) {
            double a = d(e.getAmount()); spent += a;
            if (e.getType() != null) byType.merge(e.getType().name(), a, Double::sum);
        }
        String top = byType.entrySet().stream().max(Map.Entry.comparingByValue()).map(e -> BudgetService.label(e.getKey())).orElse(null);

        double tips = staffService.report("CUSTOM", date, date, today).getTotalTips();
        MoneyReportsDTO.Budget budget = budgetService.report(date.getYear(), date.getMonthValue(), today);
        List<String> over = budget.getRows().stream().filter(r -> "OVER".equals(r.getStatus())).map(MoneyReportsDTO.BudgetRow::getLabel).toList();

        InsightsDTO.HourPoint busiest = i.getByHour().stream().filter(h -> h.getSales() > 0).max(Comparator.comparingDouble(InsightsDTO.HourPoint::getSales)).orElse(null);
        MoneyReportsDTO.DailyClose out = MoneyReportsDTO.DailyClose.builder()
                .date(date).sales(c.getSales().getCurrent()).orders((int) c.getOrders().getCurrent()).avgBill(c.getAvgOrder().getCurrent())
                .previousDaySales(c.getSales().getPrevious()).changePercent(c.getSales().getChangePercent())
                .payments(i.getByPaymentMode().stream().map(s -> MoneyReportsDTO.NameAmount.builder().name(s.getLabel()).amount(s.getAmount()).count(s.getOrders()).build()).toList())
                .discounts(i.getDiscounts().getTotal()).refunds(i.getLosses().getRefundAmount()).refundCount(i.getLosses().getRefundCount())
                .voidedCount(i.getLosses().getVoidedCount()).voidedValue(i.getLosses().getVoidedValue()).cancelledCount(i.getLosses().getCancelledCount())
                .gst(i.getTax().getTotalTax()).tips(tips).expenses(Math.round(spent * 100.0) / 100.0).topExpense(top)
                .shifts(shifts).cashVarianceTotal(Math.round(variance * 100.0) / 100.0).openShifts(open)
                .lowStock(i.getStock().getLowCount()).belowZero(i.getStock().getNegativeCount()).expiringSoon(i.getStock().getExpiringCount()).expired(i.getStock().getExpiredCount()).wasteCost(i.getLosses().getWasteCost())
                .topDishes(i.getDishes().stream().sorted(Comparator.comparingInt(InsightsDTO.DishRow::getQuantity).reversed()).limit(3).map(x -> MoneyReportsDTO.NameAmount.builder().name(x.getName()).amount(x.getRevenue()).count(x.getQuantity()).build()).toList())
                .busiestHour(busiest != null ? DailyCloseText.hour(busiest.getHour()) : null)
                .topStaff(i.getByStaff().stream().limit(3).map(x -> MoneyReportsDTO.NameAmount.builder().name(x.getStaff()).amount(x.getSales()).count(x.getOrders()).build()).toList())
                .overBudget(over).build();
        out.setText(DailyCloseText.format(out));
        return out;
    }

    public MoneyReportsDTO.DailyCloseSettings getSettings() {
        String t = settings.getSetting(KEY_TIME, DEFAULT_TIME);
        return new MoneyReportsDTO.DailyCloseSettings("true".equalsIgnoreCase(settings.getSetting(KEY_ENABLED, "false")), HHMM.matcher(t).matches() ? t : DEFAULT_TIME);
    }

    public MoneyReportsDTO.DailyCloseSettings saveSettings(MoneyReportsDTO.DailyCloseSettings s, String username) {
        if (s.getTime() == null || !HHMM.matcher(s.getTime()).matches()) throw new IllegalArgumentException("Time must look like 23:30.");
        settings.setSetting(KEY_ENABLED, String.valueOf(s.isEnabled()), username);
        settings.setSetting(KEY_TIME, s.getTime(), username);
        events.publishEvent(new TimedCafeJobs.Changed());
        return getSettings();
    }

    /** Sends the report for a day to the selected Telegram managers now. */
    public MoneyReportsDTO.SendResult send(LocalDate date, LocalDate today) {
        MoneyReportsDTO.DailyClose r = build(date, today);
        Map<String, Object> res = telegram.broadcast(r.getText(), "DAILY_CLOSE", r.getDate());
        int sent = (int) res.get("sent"), failed = (int) res.get("failed"), recipients = (int) res.get("recipients");
        String msg = recipients == 0 ? "No Telegram manager is linked and selected. Link one in the Telegram screen first."
                : sent + " sent" + (failed > 0 ? ", " + failed + " failed" : "") + ".";
        return new MoneyReportsDTO.SendResult(sent, failed, recipients, msg);
    }

    /** Sends today's report once, when enabled. Called at the configured time by {@link TimedCafeJobs}; a second call the same day does nothing. */
    public void sendIfDue() {
        try {
            MoneyReportsDTO.DailyCloseSettings s = getSettings();
            if (!s.isEnabled()) return;
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(telegram.reportZone());
            String today = now.toLocalDate().toString();
            if (today.equals(settings.getSetting(KEY_LAST_SENT, ""))) return;
            settings.setSetting(KEY_LAST_SENT, today, "SYSTEM");
            MoneyReportsDTO.SendResult r = send(now.toLocalDate(), now.toLocalDate());
            log.info("Daily close report for {}: {}", today, r.getMessage());
        } catch (Exception e) {
            log.error("Daily close report failed: {}", e.getMessage());
        }
    }
}
