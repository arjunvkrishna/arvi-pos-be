package com.neram.pos.service;

import com.neram.pos.config.JobMode;
import com.neram.pos.entity.TelegramSetting;
import com.neram.pos.repository.TelegramSettingRepository;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantExecutor;
import com.neram.pos.tenancy.TenantInfo;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Runs the two "send at HH:mm" jobs (the Telegram low-stock report and the end-of-day close) at the time each cafe chose,
 * instead of waking every minute to look inside every cafe's database.
 * <p>
 * Each cafe's times are read once, a trigger is booked for exactly that minute, and nothing touches the cafe's database
 * again until it fires. A save of either setting re-books that cafe at once ({@link Changed}); a slow refresh also picks up
 * cafes that were added, suspended or changed by another process.
 */
@Slf4j
@Component
public class TimedCafeJobs {

    /** Published after a cafe's report times were saved; handled for the cafe the request belongs to. */
    public record Changed() {
    }

    static final String TELEGRAM_REPORT = "telegram-report";
    static final String DAILY_CLOSE = "daily-close";
    private static final Pattern HHMM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    private record Booked(TenantInfo tenant, String when, ScheduledFuture<?> future) {
    }

    private final TelegramSettingRepository telegramSettings;
    private final TelegramNotificationService telegram;
    private final DailyCloseService dailyClose;
    private final TenantRegistry registry;
    private final TenantExecutor tenantExecutor;
    private final com.neram.pos.plan.PlanService plans;
    private final TaskScheduler scheduler;
    private final ThreadPoolTaskScheduler ownScheduler;
    private final Map<String, Booked> booked = new ConcurrentHashMap<>();

    @Autowired
    public TimedCafeJobs(TelegramSettingRepository telegramSettings, TelegramNotificationService telegram, DailyCloseService dailyClose,
                         TenantRegistry registry, TenantExecutor tenantExecutor, com.neram.pos.plan.PlanService plans) {
        this(telegramSettings, telegram, dailyClose, registry, tenantExecutor, plans, newScheduler());
    }

    private TimedCafeJobs(TelegramSettingRepository telegramSettings, TelegramNotificationService telegram, DailyCloseService dailyClose,
                          TenantRegistry registry, TenantExecutor tenantExecutor, com.neram.pos.plan.PlanService plans, ThreadPoolTaskScheduler own) {
        this(telegramSettings, telegram, dailyClose, registry, tenantExecutor, plans, own, own);
    }

    /** For tests: a scheduler that books triggers without ever running them. */
    TimedCafeJobs(TelegramSettingRepository telegramSettings, TelegramNotificationService telegram, DailyCloseService dailyClose,
                  TenantRegistry registry, TenantExecutor tenantExecutor, com.neram.pos.plan.PlanService plans, TaskScheduler scheduler, ThreadPoolTaskScheduler own) {
        this.telegramSettings = telegramSettings;
        this.telegram = telegram;
        this.dailyClose = dailyClose;
        this.registry = registry;
        this.tenantExecutor = tenantExecutor;
        this.plans = plans;
        this.scheduler = scheduler;
        this.ownScheduler = own;
    }

    private static ThreadPoolTaskScheduler newScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("cafe-timed-jobs-");
        s.setRemoveOnCancelPolicy(true);
        s.setDaemon(true);
        s.initialize();
        return s;
    }

    @PreDestroy
    void stop() {
        if (ownScheduler != null) ownScheduler.shutdown();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        refreshAll();
    }

    /** Looks at every cafe's two settings now and then, not every minute: a cafe's own save already re-books it immediately. */
    @Scheduled(fixedDelayString = "${app.jobs.schedule-refresh-ms:1800000}", initialDelayString = "${app.jobs.schedule-refresh-ms:1800000}")
    public void refreshAll() {
        if (!JobMode.interactive()) return;
        List<TenantInfo> active = registry.findActive().stream().map(TenantRecord::info).toList();
        tenantExecutor.runFor(active, "timed-jobs-refresh", this::refreshCurrent);
        Set<String> codes = active.stream().map(TenantInfo::code).collect(Collectors.toSet());
        booked.entrySet().removeIf(e -> {
            if (codes.contains(e.getValue().tenant().code())) return false;
            e.getValue().future().cancel(false);
            return true;
        });
    }

    @EventListener
    public void onChanged(Changed event) {
        if (JobMode.interactive()) refreshCurrent();
    }

    /** Books (or re-books, or cancels) the current cafe's two jobs from its saved settings. */
    void refreshCurrent() {
        TenantInfo tenant = TenantContext.require();
        boolean telegramInPlan = plans.has(com.neram.pos.plan.Feature.TELEGRAM);
        Optional<TelegramSetting> tg = telegramSettings.getSettings();
        ZoneId zone = zoneOf(tg.map(TelegramSetting::getTimezone).orElse(null));

        String reportTime = telegramInPlan ? tg.filter(s -> Boolean.TRUE.equals(s.getEnabled())).map(TelegramSetting::getDailySendTime).orElse(null) : null;
        book(tenant, TELEGRAM_REPORT, reportTime, zone, () -> telegram.executeDailyDispatch(false));

        var close = dailyClose.getSettings();
        book(tenant, DAILY_CLOSE, telegramInPlan && close.isEnabled() ? close.getTime() : null, zone, dailyClose::sendIfDue);
    }

    private void book(TenantInfo tenant, String job, String hhmm, ZoneId zone, Runnable task) {
        String key = tenant.code() + "|" + job;
        String when = hhmm != null && HHMM.matcher(hhmm).matches() ? hhmm + "@" + zone : null;
        Booked current = booked.get(key);
        if (current != null && current.when().equals(when)) return;
        if (current != null) {
            current.future().cancel(false);
            booked.remove(key);
        }
        if (when == null) return;
        String[] t = hhmm.split(":");
        CronTrigger trigger = new CronTrigger("0 " + Integer.parseInt(t[1]) + " " + Integer.parseInt(t[0]) + " * * *", zone);
        ScheduledFuture<?> future = scheduler.schedule(() -> fire(tenant, job, task), trigger);
        booked.put(key, new Booked(tenant, when, future));
        log.info("Booked {} for cafe '{}' at {} ({})", job, tenant.code(), hhmm, zone);
    }

    private void fire(TenantInfo tenant, String job, Runnable task) {
        if (!JobMode.interactive()) return;
        tenantExecutor.runFor(List.of(tenant), job, task);
    }

    private static ZoneId zoneOf(String id) {
        try {
            return id == null || id.isBlank() ? DEFAULT_ZONE : ZoneId.of(id);
        } catch (Exception e) {
            return DEFAULT_ZONE;
        }
    }

    /** Test view: what is booked, as "cafe|job" to "HH:mm@zone". */
    Map<String, String> bookings() {
        return booked.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().when()));
    }
}
