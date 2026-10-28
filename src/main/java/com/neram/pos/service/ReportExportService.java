package com.neram.pos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neram.pos.dto.ReportCatalogDTO.*;
import com.neram.pos.entity.ReportExportData;
import com.neram.pos.entity.ReportExportJob;
import com.neram.pos.repository.ReportExportDataRepository;
import com.neram.pos.repository.ReportExportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExportService {

    private final ReportExportJobRepository jobRepository;
    private final ReportExportDataRepository dataRepository;
    private final ReportGenerationService reportGenerationService;
    private final ObjectMapper objectMapper;

    private static final List<ReportMetadata> CATALOG = List.of(
            ReportMetadata.builder().id("sales_summary").title("Sales Summary").category("SALES").description("Orders count, net sales, GST, and tips aggregated daily").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("daily_close").title("Daily Close & Drawer Variance").category("SALES").description("Opening float, cash sales, expenses, and drawer reconciliation").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("profit_and_loss").title("Statutory Profit & Loss").category("FINANCIAL").description("Statutory P&L excluding GST and tips from revenue, subtracting COGS, OpEx and Payroll").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("balance_sheet").title("Management Balance Sheet").category("FINANCIAL").description("Balance sheet snapshot with cash, inventory at cost, supplier payables, and gift card liabilities").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("gst_summary").title("GST / Tax Summary").category("FINANCIAL").description("Taxable turnover breakdown by CGST and SGST slabs").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("food_cost").title("Food Cost Percentage").category("INVENTORY").description("Ingredient purchases vs net food sales ratio").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("top_selling_dishes").title("Top Selling Dishes").category("SALES").description("Ranked menu items by sales volume and revenue").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("slow_moving_dishes").title("Slow Moving Dishes").category("SALES").description("Lowest moving menu items needing menu engineering").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("item_modifiers_sales").title("Modifiers & Add-ons Sales").category("SALES").description("Revenue and attachment rate for customizable add-ons").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("combo_meals_sales").title("Combo Meals Sales").category("SALES").description("Combo meal bundle sales and child item choices").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("customer_retention").title("Customer Retention & CRM").category("CUSTOMERS").description("Registered customers, marketing consent and retention metrics").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("loyalty_ledger").title("Loyalty Points Audit").category("CUSTOMERS").description("Audit trail of loyalty points earned and redeemed").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("gift_cards_summary").title("Gift Cards Portfolio").category("FINANCIAL").description("Issued cards, unspent balances, and outstanding liability").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("discount_summary").title("Discounts & Coupons Concessions").category("SALES").description("Discounts conceded across manual cuts, promotions and coupons").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("operating_expenses").title("Operating Expenses (OpEx)").category("FINANCIAL").description("Overhead expenses itemized by category and payee").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("supplier_procurement").title("Supplier Procurement").category("INVENTORY").description("Invoices received, payment status and outstanding balances").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("staff_performance").title("Staff Performance & Tips").category("STAFF").description("Billing throughput and tip distribution by staff member").supportedFormats(List.of("CSV", "XLSX", "PDF")).build(),
            ReportMetadata.builder().id("peak_hours_heatmap").title("Hourly Peak Traffic Heatmap").category("SALES").description("Order frequency and sales volume by hour of day").supportedFormats(List.of("CSV", "XLSX", "PDF")).build()
    );

    public List<ReportMetadata> getCatalog() {
        return CATALOG;
    }

    @Transactional
    public ExportJobResponse submitExportJob(ExportJobRequest request, Long userId) {
        String reportId = request.getReportId() != null ? request.getReportId().trim().toLowerCase() : "sales_summary";
        String format = request.getFormat() != null ? request.getFormat().trim().toUpperCase() : "CSV";

        UUID jobId = UUID.randomUUID();
        String filterJson = null;
        try {
            if (request.getFilterParams() != null) {
                filterJson = objectMapper.writeValueAsString(request.getFilterParams());
            }
        } catch (Exception ignored) {}

        ReportExportJob job = ReportExportJob.builder()
                .id(jobId)
                .reportId(reportId)
                .format(format)
                .status("PENDING")
                .filterParams(filterJson)
                .downloadKey("/api/reports/export/" + jobId + "/download")
                .createdBy(userId)
                .outletId(callerOutlet())
                .createdAt(Instant.now())
                .build();

        jobRepository.save(job);
        log.info("Submitted report export job {} for report '{}' ({})", jobId, reportId, format);

        return toResponse(job);
    }

    public ExportJobResponse getJobStatus(UUID jobId) {
        ReportExportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Export job not found: " + jobId));
        assertMayRead(job);
        return toResponse(job);
    }

    public byte[] getJobData(UUID jobId) {
        // The job is loaded first so the branch guard and the owner check apply to the file as well.
        ReportExportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Export job not found: " + jobId));
        assertMayRead(job);
        ReportExportData data = dataRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Export data not ready or not found for job: " + jobId));
        return data.getFileData();
    }

    /** The branch the caller is limited to, or null for the whole cafe. */
    private static Long callerOutlet() {
        Long outlet = com.neram.pos.access.OutletContext.currentIdOrNull();
        return outlet != null && outlet != com.neram.pos.access.OutletContext.NO_OUTLET ? outlet : null;
    }

    /**
     * A report can hold payroll, tips and takings, so it is for the person who asked for it, or for the owner and main
     * managers who run the whole cafe. (Other branches' reports are already invisible to a branch user.)
     * Background work has no logged-in user and is not restricted.
     */
    private static void assertMayRead(ReportExportJob job) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.neram.pos.access.CafeUserDetails user)) {
            return;
        }
        boolean requester = job.getCreatedBy() != null && job.getCreatedBy().equals(user.getUserId());
        if (!requester && !com.neram.pos.access.AccessPolicy.hasCafeWideAccess(user.getRole())) {
            throw new org.springframework.security.access.AccessDeniedException("This report was requested by someone else.");
        }
    }

    /** Removes finished export jobs (and their files) created before the cut-off. Returns how many were removed. */
    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        List<ReportExportJob> old = jobRepository.findOlderThan(cutoff);
        if (!old.isEmpty()) {
            jobRepository.deleteAll(old);
        }
        return old.size();
    }

    /**
     * Claims the oldest PENDING job and marks it PROCESSING inside one short transaction, so the
     * SKIP LOCKED row lock is held until the claim is committed and two workers can never run the same job.
     */
    @Transactional
    public Optional<ReportExportJob> claimNextJob() {
        Optional<ReportExportJob> next = jobRepository.findNextPendingJob();
        next.ifPresent(job -> {
            job.setStatus("PROCESSING");
            job.setStartedAt(Instant.now());
            job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
            jobRepository.save(job);
        });
        return next;
    }

    /** Generates the file for a job already claimed by {@link #claimNextJob()}; runs outside any transaction. */
    public void executeJob(ReportExportJob job) {
        log.info("Executing report export job {} for '{}' in {}", job.getId(), job.getReportId(), job.getFormat());
        try {
            Map<String, Object> filters = Collections.emptyMap();
            if (job.getFilterParams() != null && !job.getFilterParams().isBlank()) {
                filters = objectMapper.readValue(job.getFilterParams(), Map.class);
            }

            byte[] bytes = reportGenerationService.generateReport(job.getReportId(), job.getFormat(), filters);
            dataRepository.save(ReportExportData.builder().jobId(job.getId()).fileData(bytes).build());

            job.setStatus("COMPLETED");
            job.setErrorMessage(null);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            log.info("Successfully completed export job {}", job.getId());
        } catch (Exception e) {
            log.error("Failed to execute export job {}: {}", job.getId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    public static final int MAX_ATTEMPTS = 3;

    /** Jobs stuck in PROCESSING (worker died) go back to PENDING until MAX_ATTEMPTS, then FAILED. Returns jobs touched. */
    @Transactional
    public int recoverStuckJobs(Instant startedBefore) {
        List<ReportExportJob> stuck = jobRepository.findByStatusAndStartedAtBefore("PROCESSING", startedBefore);
        for (ReportExportJob job : stuck) {
            if (job.getAttempts() != null && job.getAttempts() >= MAX_ATTEMPTS) {
                job.setStatus("FAILED");
                job.setErrorMessage("Export job did not finish after " + MAX_ATTEMPTS + " attempts");
                job.setCompletedAt(Instant.now());
            } else {
                job.setStatus("PENDING");
                job.setStartedAt(null);
            }
            jobRepository.save(job);
        }
        return stuck.size();
    }

    private ExportJobResponse toResponse(ReportExportJob j) {
        return ExportJobResponse.builder()
                .jobId(j.getId())
                .reportId(j.getReportId())
                .format(j.getFormat())
                .status(j.getStatus())
                .downloadUrl("COMPLETED".equals(j.getStatus()) ? j.getDownloadKey() : null)
                .errorMessage(j.getErrorMessage())
                .createdAt(j.getCreatedAt())
                .completedAt(j.getCompletedAt())
                .build();
    }
}
