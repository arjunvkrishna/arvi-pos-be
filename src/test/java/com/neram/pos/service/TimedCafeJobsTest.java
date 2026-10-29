package com.neram.pos.service;

import com.neram.pos.dto.MoneyReportsDTO;
import com.neram.pos.entity.TelegramSetting;
import com.neram.pos.repository.TelegramSettingRepository;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantExecutor;
import com.neram.pos.tenancy.TenantInfo;
import com.neram.pos.tenancy.TenantRecord;
import com.neram.pos.tenancy.TenantRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class TimedCafeJobsTest {

    private final TenantInfo blue = new TenantInfo(5, "bluedoor");
    private final TenantInfo green = new TenantInfo(6, "greenleaf");

    private TelegramSettingRepository telegramSettings;
    private TelegramNotificationService telegram;
    private DailyCloseService dailyClose;
    private TenantRegistry registry;
    private TaskScheduler scheduler;
    private TimedCafeJobs jobs;

    @BeforeEach
    void setUp() {
        telegramSettings = mock(TelegramSettingRepository.class);
        telegram = mock(TelegramNotificationService.class);
        dailyClose = mock(DailyCloseService.class);
        registry = mock(TenantRegistry.class);
        scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(i -> mock(ScheduledFuture.class));
        jobs = new TimedCafeJobs(telegramSettings, telegram, dailyClose, registry, new TenantExecutor(registry), scheduler, null);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static TelegramSetting telegramAt(boolean enabled, String time, String zone) {
        return TelegramSetting.builder().enabled(enabled).dailySendTime(time).timezone(zone).build();
    }

    private void refresh(TenantInfo cafe) {
        TenantContext.runAs(cafe, jobs::refreshCurrent);
    }

    private static TenantRecord active(TenantInfo t) {
        return new TenantRecord(t.id(), t.code(), t.code(), "ACTIVE", "PRO", 3, null, null, null, "h", 5432, "d", "u", "p", null, null, null, null, null);
    }

    /** When the trigger would next fire after the given instant. */
    private static Instant nextAfter(Trigger trigger, String now) {
        return trigger.nextExecution(new SimpleTriggerContext(Clock.fixed(Instant.parse(now), ZoneOffset.UTC)));
    }

    private List<Trigger> bookedTriggers(int expected) {
        ArgumentCaptor<Trigger> captor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler, times(expected)).schedule(any(Runnable.class), captor.capture());
        return captor.getAllValues();
    }

    @Test
    void eachJobIsBookedForExactlyTheMinuteTheCafeChose() {
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "09:00", "Asia/Kolkata")));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(true, "23:30"));
        refresh(blue);

        assertEquals(java.util.Map.of("bluedoor|telegram-report", "09:00@Asia/Kolkata", "bluedoor|daily-close", "23:30@Asia/Kolkata"), jobs.bookings());
        List<Trigger> triggers = bookedTriggers(2);
        // 00:00 UTC is 05:30 in India: the report is 3.5 h away, the close 18 h away
        assertEquals(Instant.parse("2026-10-29T03:30:00Z"), nextAfter(triggers.get(0), "2026-10-29T00:00:00Z"));
        assertEquals(Instant.parse("2026-10-29T18:00:00Z"), nextAfter(triggers.get(1), "2026-10-29T00:00:00Z"));
    }

    @Test
    void theCafesTimeZoneDecidesWhenItFires() {
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "09:00", "Asia/Dubai")));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(false, "23:30"));
        refresh(blue);
        assertEquals(Instant.parse("2026-10-29T05:00:00Z"), nextAfter(bookedTriggers(1).get(0), "2026-10-29T00:00:00Z"));
    }

    @Test
    void aJobThatIsSwitchedOffIsNotBookedAtAll() {
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(false, "09:00", "Asia/Kolkata")));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(false, "23:30"));
        refresh(blue);
        assertTrue(jobs.bookings().isEmpty());
        verify(scheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void aCafeWithNothingSavedYetBooksNothing() {
        when(telegramSettings.getSettings()).thenReturn(Optional.empty());
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(false, "23:30"));
        refresh(blue);
        assertTrue(jobs.bookings().isEmpty());
    }

    @Test
    void lookingAgainAtUnchangedSettingsBooksNothingNew() {
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "09:00", "Asia/Kolkata")));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(true, "23:30"));
        refresh(blue);
        refresh(blue);
        refresh(blue);
        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void changingATimeReplacesTheOldBookingAndSwitchingOffCancelsIt() {
        ScheduledFuture<?> first = mock(ScheduledFuture.class);
        when(scheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(i -> first).thenAnswer(i -> mock(ScheduledFuture.class));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(false, "23:30"));
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "09:00", "Asia/Kolkata")));
        refresh(blue);
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "21:15", "Asia/Kolkata")));
        refresh(blue);

        verify(first).cancel(false);
        assertEquals("21:15@Asia/Kolkata", jobs.bookings().get("bluedoor|telegram-report"));
        assertEquals(Instant.parse("2026-10-29T15:45:00Z"), nextAfter(bookedTriggers(2).get(1), "2026-10-29T00:00:00Z"));

        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(false, "21:15", "Asia/Kolkata")));
        refresh(blue);
        assertTrue(jobs.bookings().isEmpty());
    }

    @Test
    void aTimeThatIsNotHoursAndMinutesIsIgnoredRatherThanBookedWrongly() {
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "9am", "Asia/Kolkata")));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(true, "25:99"));
        refresh(blue);
        assertTrue(jobs.bookings().isEmpty());
    }

    @Test
    void twoCafesWithTheSameTimeAreBookedSeparately() {
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "09:00", "Asia/Kolkata")));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(false, "23:30"));
        refresh(blue);
        refresh(green);
        assertEquals(2, jobs.bookings().size());
        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void whenTheTimeComesTheJobRunsInsideThatCafe() {
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "09:00", "Asia/Kolkata")));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(false, "23:30"));
        AtomicReference<TenantInfo> ranIn = new AtomicReference<>();
        doAnswer(i -> { ranIn.set(TenantContext.current().orElse(null)); return null; }).when(telegram).executeDailyDispatch(anyBoolean());
        refresh(blue);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), any(Trigger.class));
        task.getValue().run();

        verify(telegram).executeDailyDispatch(false);
        assertEquals(blue, ranIn.get());
    }

    @Test
    void theEndOfDayJobCallsTheDailyCloseSend() {
        when(telegramSettings.getSettings()).thenReturn(Optional.empty());
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(true, "23:30"));
        refresh(blue);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), any(Trigger.class));
        task.getValue().run();
        verify(dailyClose).sendIfDue();
        verifyNoInteractions(telegram);
    }

    @Test
    void aCafeThatIsSuspendedLosesItsBookings() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(scheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(i -> future);
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "09:00", "Asia/Kolkata")));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(false, "23:30"));
        when(registry.findActive()).thenReturn(List.of(active(blue)));
        jobs.refreshAll();
        assertEquals(1, jobs.bookings().size());

        when(registry.findActive()).thenReturn(List.of(active(green)));
        jobs.refreshAll();

        verify(future).cancel(false);
        assertEquals(java.util.Set.of("greenleaf|telegram-report"), jobs.bookings().keySet());
    }

    @Test
    void savingSettingsRebooksTheCafeThatSavedThem() {
        when(telegramSettings.getSettings()).thenReturn(Optional.of(telegramAt(true, "09:00", "Asia/Kolkata")));
        when(dailyClose.getSettings()).thenReturn(new MoneyReportsDTO.DailyCloseSettings(false, "23:30"));
        TenantContext.runAs(blue, () -> jobs.onChanged(new TimedCafeJobs.Changed()));
        assertEquals("09:00@Asia/Kolkata", jobs.bookings().get("bluedoor|telegram-report"));
    }
}
