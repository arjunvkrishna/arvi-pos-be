package com.neram.pos.service;

import com.neram.pos.access.OutletContext;
import com.neram.pos.entity.ReportExportJob;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantExecutor;
import com.neram.pos.tenancy.TenantInfo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates the report files asked for on the Reports screen.
 * <p>
 * Nothing polls the database: a job is started the moment it is submitted, on a small shared thread pool, inside the
 * cafe that asked (its own database) and inside the branch scope of the person who asked, so a branch manager's report
 * only ever contains that branch. One nightly sweep per cafe recovers jobs stuck by a crash and purges old files.
 */
@Slf4j
@Component
public class ReportExportWorker {

    private static final int KEEP_DAYS = 7;
    private static final int STUCK_AFTER_MINUTES = 10;

    private final ReportExportService exportService;
    private final TenantExecutor tenantExecutor;
    private final boolean backgroundEnabled;

    private final AtomicInteger threadNumber = new AtomicInteger();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "report-export-" + threadNumber.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    public ReportExportWorker(ReportExportService exportService, TenantExecutor tenantExecutor,
                              @Value("${app.jobs.background-enabled:true}") boolean backgroundEnabled) {
        this.exportService = exportService;
        this.tenantExecutor = tenantExecutor;
        this.backgroundEnabled = backgroundEnabled;
    }

    /** Starts the waiting jobs of the current cafe on a background thread. Call after the job has been committed. */
    public void dispatchForCurrentCafe() {
        if (!backgroundEnabled) {
            return;
        }
        TenantInfo tenant = TenantContext.require();
        pool.execute(() -> TenantContext.runAs(tenant, this::drain));
    }

    /** Runs every pending job of the cafe the thread is working for, one after the other. */
    void drain() {
        try {
            exportService.recoverStuckJobs(Instant.now().minus(STUCK_AFTER_MINUTES, ChronoUnit.MINUTES));
            Optional<ReportExportJob> next;
            while ((next = exportService.claimNextJob()).isPresent()) {
                ReportExportJob job = next.get();
                // The file is built with the same branch limits as the person who asked for it.
                OutletContext.runAs(job.getOutletId(), () -> exportService.executeJob(job));
            }
        } catch (Exception e) {
            log.error("Report export worker failed: {}", e.getMessage(), e);
        }
    }

    /** Nightly, once per cafe: pick up jobs a crash left behind, then drop files older than a week. */
    @Scheduled(cron = "0 0 3 * * *")
    public void nightlySweep() {
        if (!backgroundEnabled) {
            return;
        }
        tenantExecutor.forEachActiveTenant("report-export-sweep", () -> {
            drain();
            int purged = exportService.purgeOlderThan(Instant.now().minus(KEEP_DAYS, ChronoUnit.DAYS));
            if (purged > 0) {
                log.info("Purged {} report export job(s) older than {} days", purged, KEEP_DAYS);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }
}
