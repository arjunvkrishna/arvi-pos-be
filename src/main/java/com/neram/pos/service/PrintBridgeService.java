package com.neram.pos.service;

import com.neram.pos.dto.*;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.PrintJob;
import com.neram.pos.entity.PrintJobStatus;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.PrintJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintBridgeService {

    private static final long BRIDGE_ONLINE_THRESHOLD_MS = 30_000L; // 30 seconds
    private static final int MAX_ATTEMPTS = 3;
    private static final int CLAIM_LEASE_SECONDS = 45;

    private final PrintJobRepository printJobRepository;
    private final BillRepository billRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    private final AtomicReference<PrintBridgeHeartbeatDTO> latestHeartbeat = new AtomicReference<>(null);
    private final AtomicLong lastHeartbeatTimestamp = new AtomicLong(0L);

    // Notification lock/condition for immediate wake-up of long-polling bridge requests
    private final ReentrantLock pollLock = new ReentrantLock();
    private final Condition jobAvailableCondition = pollLock.newCondition();

    /**
     * Check if a bridge container has reported a heartbeat within the last 30 seconds
     */
    public boolean isBridgeOnline() {
        long last = lastHeartbeatTimestamp.get();
        return last > 0 && (System.currentTimeMillis() - last) <= BRIDGE_ONLINE_THRESHOLD_MS;
    }

    /**
     * Record a heartbeat from a local print bridge
     */
    public void recordHeartbeat(PrintBridgeHeartbeatDTO heartbeat) {
        latestHeartbeat.set(heartbeat);
        lastHeartbeatTimestamp.set(System.currentTimeMillis());
        log.debug("Printer bridge heartbeat from {}: printerReachable={}, latency={}ms, unsyncedJobs={}",
                heartbeat.getBridgeIp(), heartbeat.getPrinterReachable(), heartbeat.getPrinterLatencyMs(), heartbeat.getUnsyncedJobsCount());
    }

    /**
     * Notify any long-polling threads that a new job is available in the database
     */
    public void notifyJobAvailable() {
        pollLock.lock();
        try {
            jobAvailableCondition.signalAll();
        } finally {
            pollLock.unlock();
        }
    }

    /**
     * Enqueue a new print job into the database
     */
    @Transactional
    public PrintJob enqueuePrintJob(PrintJob job) {
        // Check idempotency if key provided
        if (job.getIdempotencyKey() != null && !job.getIdempotencyKey().isBlank()) {
            Optional<PrintJob> existing = printJobRepository.findByIdempotencyKey(job.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Duplicate print job request detected for idempotency key '{}'. Returning existing job {}",
                        job.getIdempotencyKey(), existing.get().getJobId());
                return existing.get();
            }
        }

        // Check if an identical job is already PENDING or CLAIMED for this bill and type
        if (job.getBillId() != null && job.getJobType() != null) {
            List<PrintJob> activeJobs = printJobRepository.findByBillIdAndJobTypeAndStatusIn(
                    job.getBillId(),
                    job.getJobType(),
                    List.of(PrintJobStatus.PENDING, PrintJobStatus.CLAIMED, PrintJobStatus.RETRY_PENDING)
            );
            if (!activeJobs.isEmpty()) {
                PrintJob active = activeJobs.get(0);
                log.info("Existing active print job {} found for bill {} ({}). Returning active job.",
                        active.getJobId(), job.getBillId(), job.getJobType());
                return active;
            }
        }

        if (job.getStatus() == null) {
            job.setStatus(PrintJobStatus.PENDING);
        }
        if (job.getAttemptCount() == null) {
            job.setAttemptCount(0);
        }
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now());
        }
        if (job.getUpdatedAt() == null) {
            job.setUpdatedAt(LocalDateTime.now());
        }
        if (job.getNextEligibleAt() == null) {
            job.setNextEligibleAt(LocalDateTime.now());
        }

        PrintJob saved = printJobRepository.save(job);
        log.info("Enqueued print job {} ({}) for {}:{} into database queue",
                saved.getJobId(), saved.getJobType(), saved.getTargetPrinterHost(), saved.getTargetPrinterPort());

        // Wake up waiting bridge long-pollers
        notifyJobAvailable();

        return saved;
    }

    /**
     * Poll next eligible job from database with long-polling timeout and atomic claim
     */
    public PrintBridgeJobDTO pollJob(String bridgeId, int timeoutSeconds) {
        String effectiveBridgeId = (bridgeId != null && !bridgeId.isBlank()) ? bridgeId : "neram-bridge-main";
        if (timeoutSeconds <= 0) {
            Long claimedJobId = claimNextEligibleJob(effectiveBridgeId);
            if (claimedJobId != null) {
                Optional<PrintJob> jobOpt = printJobRepository.findById(claimedJobId);
                if (jobOpt.isPresent()) {
                    PrintJob job = jobOpt.get();
                    log.info("Bridge '{}' claimed print job {} (Attempt #{})",
                            effectiveBridgeId, job.getJobId(), job.getAttemptCount());
                    return toBridgeJobDTO(job);
                }
            }
            return null;
        }

        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            Long claimedJobId = claimNextEligibleJob(effectiveBridgeId);
            if (claimedJobId != null) {
                Optional<PrintJob> jobOpt = printJobRepository.findById(claimedJobId);
                if (jobOpt.isPresent()) {
                    PrintJob job = jobOpt.get();
                    log.info("Bridge '{}' claimed print job {} (Attempt #{})",
                            effectiveBridgeId, job.getJobId(), job.getAttemptCount());
                    return toBridgeJobDTO(job);
                }
            }

            long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0) {
                break;
            }

            // Wait for notification or slice of timeout
            pollLock.lock();
            try {
                jobAvailableCondition.await(Math.min(remainingMs, 1500L), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                pollLock.unlock();
            }
        }

        return null;
    }

    /**
     * Atomically claim the next eligible job using PostgreSQL SELECT ... FOR UPDATE SKIP LOCKED
     */
    private Long claimNextEligibleJob(String bridgeId) {
        String sql = """
            UPDATE print_jobs
            SET status = 'CLAIMED',
                claimed_by_bridge_id = ?,
                claimed_at = ?,
                lease_expires_at = ?,
                attempt_count = attempt_count + 1,
                updated_at = ?
            WHERE id = (
                SELECT id FROM print_jobs
                WHERE status IN ('PENDING', 'RETRY_PENDING')
                  AND (next_eligible_at IS NULL OR next_eligible_at <= ?)
                ORDER BY id ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id
            """;

        return transactionTemplate.execute(status -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime leaseExpires = now.plusSeconds(CLAIM_LEASE_SECONDS);
                return jdbcTemplate.queryForObject(
                        sql,
                        Long.class,
                        bridgeId,
                        now,
                        leaseExpires,
                        now,
                        now
                );
            } catch (EmptyResultDataAccessException e) {
                return null;
            } catch (Exception e) {
                log.error("Error executing atomic job claim: {}", e.getMessage());
                return null;
            }
        });
    }

    /**
     * Bridge acknowledges local persistence of claimed job
     */
    @Transactional
    public boolean acknowledgeJobReceipt(String jobId, String bridgeId) {
        Optional<PrintJob> jobOpt = printJobRepository.findByJobId(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("Receipt ACK for unknown job: {}", jobId);
            return false;
        }

        PrintJob job = jobOpt.get();
        if (job.getStatus() == PrintJobStatus.CLAIMED) {
            // Extend lease now that bridge has received and locally journaled the job
            job.setLeaseExpiresAt(LocalDateTime.now().plusSeconds(60));
            job.setClaimedByBridgeId(bridgeId);
            job.setUpdatedAt(LocalDateTime.now());
            printJobRepository.save(job);
            log.info("Job {} receipt acknowledged by bridge '{}'; lease extended.", jobId, bridgeId);
        }
        return true;
    }

    /**
     * Bridge reports job has been sent over local network socket to printer
     */
    @Transactional
    public boolean recordSentToPrinter(String jobId, String bridgeId) {
        Optional<PrintJob> jobOpt = printJobRepository.findByJobId(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("Sent report for unknown job: {}", jobId);
            return false;
        }

        PrintJob job = jobOpt.get();
        if (job.getStatus() != PrintJobStatus.COMPLETED) {
            job.setStatus(PrintJobStatus.SENT_TO_PRINTER);
            job.setSentAt(LocalDateTime.now());
            job.setClaimedByBridgeId(bridgeId);
            job.setUpdatedAt(LocalDateTime.now());
            printJobRepository.save(job);
            log.info("Job {} marked SENT_TO_PRINTER by bridge '{}'", jobId, bridgeId);
        }
        return true;
    }

    /**
     * Bridge reports successful printing
     */
    @Transactional
    public boolean recordJobCompleted(String jobId, String bridgeId, String message, Integer bytesPrinted) {
        Optional<PrintJob> jobOpt = printJobRepository.findByJobId(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("Completion report for unknown job: {}", jobId);
            return false;
        }

        PrintJob job = jobOpt.get();
        job.setStatus(PrintJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        if (job.getSentAt() == null) {
            job.setSentAt(LocalDateTime.now());
        }
        job.setClaimedByBridgeId(bridgeId);
        job.setLastErrorMessage(message);
        if (bytesPrinted != null && bytesPrinted > 0) {
            job.setBytesPrinted(bytesPrinted);
        }
        job.setUpdatedAt(LocalDateTime.now());
        printJobRepository.save(job);

        // Update corresponding bill timestamp if applicable
        if (job.getBillId() != null) {
            billRepository.findById(job.getBillId()).ifPresent(bill -> {
                if ("KOT".equalsIgnoreCase(job.getJobType()) || "JUICE_KOT".equalsIgnoreCase(job.getJobType())) {
                    bill.setKotPrintedAt(LocalDateTime.now());
                } else if ("BILL".equalsIgnoreCase(job.getJobType())) {
                    bill.setPrintedAt(LocalDateTime.now());
                }
                billRepository.save(bill);
            });
        }

        log.info("Job {} COMPLETED by bridge '{}': {}", jobId, bridgeId, message);
        return true;
    }

    /**
     * Bridge reports print failure or ambiguous outcome
     */
    @Transactional
    public boolean recordJobFailed(String jobId, String bridgeId, Boolean ambiguous, String message, Integer bytesPrinted) {
        Optional<PrintJob> jobOpt = printJobRepository.findByJobId(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("Failure report for unknown job: {}", jobId);
            return false;
        }

        PrintJob job = jobOpt.get();

        // If the outcome is ambiguous (e.g. socket reset during data transmission),
        // we must never blindly retry to prevent paper waste and duplicate receipts
        if (Boolean.TRUE.equals(ambiguous)) {
            job.setStatus(PrintJobStatus.NEEDS_REVIEW);
            log.warn("Job {} marked NEEDS_REVIEW due to ambiguous bridge outcome: {}", jobId, message);
        } else if (job.getAttemptCount() < MAX_ATTEMPTS) {
            job.setStatus(PrintJobStatus.RETRY_PENDING);
            // Increasing backoff delay: 10s, 20s, 30s
            int delaySeconds = 10 * Math.max(1, job.getAttemptCount());
            job.setNextEligibleAt(LocalDateTime.now().plusSeconds(delaySeconds));
            log.warn("Job {} marked RETRY_PENDING (Eligible in {}s, Attempt #{}/{}): {}",
                    jobId, delaySeconds, job.getAttemptCount(), MAX_ATTEMPTS, message);
            notifyJobAvailable();
        } else {
            job.setStatus(PrintJobStatus.FAILED);
            log.error("Job {} permanently FAILED after {} attempts: {}", jobId, job.getAttemptCount(), message);
        }

        job.setClaimedByBridgeId(bridgeId);
        job.setLastErrorMessage(message);
        if (bytesPrinted != null) {
            job.setBytesPrinted(bytesPrinted);
        }
        job.setUpdatedAt(LocalDateTime.now());
        printJobRepository.save(job);
        return true;
    }

    /**
     * Sync results for jobs executed or queued while bridge was offline
     */
    @Transactional
    public int syncOfflineResults(List<PrintJobOfflineSyncDTO> results, String bridgeId) {
        if (results == null || results.isEmpty()) {
            return 0;
        }

        int synced = 0;
        for (PrintJobOfflineSyncDTO res : results) {
            if (res.getJobId() == null || res.getJobId().isBlank()) continue;
            Optional<PrintJob> jobOpt = printJobRepository.findByJobId(res.getJobId());
            if (jobOpt.isEmpty()) {
                log.warn("Offline sync referenced unknown job: {}", res.getJobId());
                continue;
            }

            PrintJob job = jobOpt.get();
            // Idempotent: If already COMPLETED, keep COMPLETED
            if (job.getStatus() == PrintJobStatus.COMPLETED) {
                synced++;
                continue;
            }

            if ("COMPLETED".equalsIgnoreCase(res.getStatus()) || "SENT_TO_PRINTER".equalsIgnoreCase(res.getStatus())) {
                recordJobCompleted(res.getJobId(), bridgeId, res.getMessage(), res.getBytesPrinted());
            } else if (Boolean.TRUE.equals(res.getAmbiguous()) || "NEEDS_REVIEW".equalsIgnoreCase(res.getStatus())) {
                recordJobFailed(res.getJobId(), bridgeId, true, res.getMessage(), res.getBytesPrinted());
            } else {
                recordJobFailed(res.getJobId(), bridgeId, false, res.getMessage(), res.getBytesPrinted());
            }
            synced++;
        }

        log.info("Synced {} offline print job results from bridge '{}'", synced, bridgeId);
        return synced;
    }

    /**
     * Query detailed status of a print job for UI or bridge
     */
    public PrintJobStatusDTO getJobStatus(String jobId) {
        PrintJob job = printJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Print job not found: " + jobId));

        boolean retryable = (job.getStatus() == PrintJobStatus.FAILED || job.getStatus() == PrintJobStatus.NEEDS_REVIEW);

        return PrintJobStatusDTO.builder()
                .jobId(job.getJobId())
                .billId(job.getBillId())
                .billNumber(job.getBillNumber())
                .jobType(job.getJobType())
                .status(job.getStatus().name())
                .attemptCount(job.getAttemptCount())
                .nextEligibleAt(job.getNextEligibleAt())
                .createdAt(job.getCreatedAt())
                .claimedAt(job.getClaimedAt())
                .sentAt(job.getSentAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .claimedByBridgeId(job.getClaimedByBridgeId())
                .lastErrorMessage(job.getLastErrorMessage())
                .bytesPrinted(job.getBytesPrinted())
                .retryable(retryable)
                .build();
    }

    /**
     * Deliberately retry a failed or ambiguous job (authorized operator action)
     */
    @Transactional
    public PrintJobStatusDTO retryJob(String jobId) {
        PrintJob job = printJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Print job not found: " + jobId));

        if (job.getStatus() != PrintJobStatus.FAILED && job.getStatus() != PrintJobStatus.NEEDS_REVIEW) {
            log.info("Job {} is currently {}; no manual retry required", jobId, job.getStatus());
            return getJobStatus(jobId);
        }

        job.setStatus(PrintJobStatus.PENDING);
        job.setNextEligibleAt(LocalDateTime.now());
        job.setAttemptCount(0);
        job.setLastErrorMessage("Manually retried by operator");
        job.setUpdatedAt(LocalDateTime.now());
        printJobRepository.save(job);

        log.info("Job {} manually marked PENDING for retry by operator", jobId);
        notifyJobAvailable();
        return getJobStatus(jobId);
    }

    /**
     * Background cleaner for expired claims (e.g. bridge crashed or network lost before report)
     */
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void sweepExpiredLeases() {
        LocalDateTime now = LocalDateTime.now();
        List<PrintJob> expired = printJobRepository.findExpiredClaimedJobs(PrintJobStatus.CLAIMED, now);
        for (PrintJob job : expired) {
            if (job.getAttemptCount() < MAX_ATTEMPTS) {
                job.setStatus(PrintJobStatus.RETRY_PENDING);
                int delaySeconds = 10 * Math.max(1, job.getAttemptCount());
                job.setNextEligibleAt(now.plusSeconds(delaySeconds));
                job.setLastErrorMessage("Claim lease expired on bridge '" + job.getClaimedByBridgeId() + "'");
                job.setUpdatedAt(now);
                log.warn("Job {} claim lease expired. Scheduled for retry in {}s (Attempt #{})",
                        job.getJobId(), delaySeconds, job.getAttemptCount());
            } else {
                job.setStatus(PrintJobStatus.NEEDS_REVIEW);
                job.setLastErrorMessage("Claim lease expired after " + job.getAttemptCount() + " attempts; marked for review");
                job.setUpdatedAt(now);
                log.error("Job {} exceeded max attempts without bridge report. Marked NEEDS_REVIEW", job.getJobId());
            }
            printJobRepository.save(job);
        }
        if (!expired.isEmpty()) {
            notifyJobAvailable();
        }
    }

    /**
     * Purge payload binary blob after 24 hours retention, keeping compact status record
     */
    @Scheduled(cron = "0 0 * * * *") // hourly
    @Transactional
    public void purgeOldPayloads() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        int purged = printJobRepository.purgePayloadsOlderThan(threshold);
        if (purged > 0) {
            log.info("Purged ESC/POS payloads for {} completed/failed print jobs older than 24h", purged);
        }
    }

    /**
     * Convert DB entity to bridge long-poll DTO
     */
    private PrintBridgeJobDTO toBridgeJobDTO(PrintJob job) {
        String base64 = (job.getPayload() != null && job.getPayload().length > 0)
                ? Base64.getEncoder().encodeToString(job.getPayload())
                : null;

        return PrintBridgeJobDTO.builder()
                .jobId(job.getJobId())
                .printType(job.getJobType())
                .billId(job.getBillId())
                .billNumber(job.getBillNumber())
                .targetHost(job.getTargetPrinterHost())
                .targetPort(job.getTargetPrinterPort())
                .dataBase64(base64)
                .status(job.getStatus().name())
                .attemptCount(job.getAttemptCount())
                .createdAt(job.getCreatedAt())
                .build();
    }

    /**
     * Get current status of bridge and target printer
     */
    public PrintBridgeStatusDTO getBridgeStatus(String currentPrinterHost, Integer currentPrinterPort) {
        boolean online = isBridgeOnline();
        long last = lastHeartbeatTimestamp.get();
        Long secondsAgo = (last > 0) ? Math.max(0, (System.currentTimeMillis() - last) / 1000) : null;
        PrintBridgeHeartbeatDTO hb = latestHeartbeat.get();

        LocalDateTime lastHbTime = (last > 0)
                ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(last), ZoneId.of("Asia/Kolkata"))
                : null;

        long pendingInDb = printJobRepository.countByStatus(PrintJobStatus.PENDING)
                + printJobRepository.countByStatus(PrintJobStatus.RETRY_PENDING)
                + printJobRepository.countByStatus(PrintJobStatus.CLAIMED);

        String effectiveBillHost = (hb != null && hb.getBillPrinterHost() != null) ? hb.getBillPrinterHost()
                : (hb != null && hb.getPrinterHost() != null ? hb.getPrinterHost() : currentPrinterHost);
        Integer effectiveBillPort = (hb != null && hb.getBillPrinterPort() != null) ? hb.getBillPrinterPort()
                : (hb != null && hb.getPrinterPort() != null ? hb.getPrinterPort() : currentPrinterPort);
        Boolean billReachable = (hb != null && hb.getBillPrinterReachable() != null) ? hb.getBillPrinterReachable()
                : (hb != null ? hb.getPrinterReachable() : null);
        Long billLatency = (hb != null && hb.getBillPrinterLatencyMs() != null) ? hb.getBillPrinterLatencyMs()
                : (hb != null ? hb.getPrinterLatencyMs() : null);

        String effectiveKotHost = (hb != null && hb.getKotPrinterHost() != null) ? hb.getKotPrinterHost()
                : effectiveBillHost;
        Integer effectiveKotPort = (hb != null && hb.getKotPrinterPort() != null) ? hb.getKotPrinterPort()
                : effectiveBillPort;
        Boolean kotReachable = (hb != null && hb.getKotPrinterReachable() != null) ? hb.getKotPrinterReachable()
                : billReachable;
        Long kotLatency = (hb != null && hb.getKotPrinterLatencyMs() != null) ? hb.getKotPrinterLatencyMs()
                : billLatency;

        String effectiveJuiceKotHost = (hb != null && hb.getJuiceKotPrinterHost() != null) ? hb.getJuiceKotPrinterHost()
                : effectiveKotHost;
        Integer effectiveJuiceKotPort = (hb != null && hb.getJuiceKotPrinterPort() != null) ? hb.getJuiceKotPrinterPort()
                : effectiveKotPort;
        Boolean juiceKotReachable = (hb != null && hb.getJuiceKotPrinterReachable() != null) ? hb.getJuiceKotPrinterReachable()
                : kotReachable;
        Long juiceKotLatency = (hb != null && hb.getJuiceKotPrinterLatencyMs() != null) ? hb.getJuiceKotPrinterLatencyMs()
                : kotLatency;

        Long freeHeapVal = (hb != null) ? (hb.getFreeHeap() != null ? hb.getFreeHeap() : hb.getFreeHeapBytes()) : null;
        Long freePsramVal = (hb != null) ? (hb.getFreePsram() != null ? hb.getFreePsram() : hb.getFreePsramBytes()) : null;
        String hwVal = (hb != null) ? (hb.getHardwareModel() != null ? hb.getHardwareModel() : (hb.getHardware() != null ? hb.getHardware() : "ESP32-S3-N16R8")) : "ESP32-S3-N16R8";

        return PrintBridgeStatusDTO.builder()
                .bridgeOnline(online)
                .lastHeartbeatSecondsAgo(secondsAgo)
                .bridgeId(hb != null ? hb.getBridgeId() : "neram-bridge-main")
                .bridgeIp(hb != null ? hb.getBridgeIp() : null)
                .targetPrinterHost(effectiveBillHost)
                .targetPrinterPort(effectiveBillPort)
                .printerReachable(billReachable)
                .printerLatencyMs(billLatency)
                .billPrinterHost(effectiveBillHost)
                .billPrinterPort(effectiveBillPort)
                .billPrinterReachable(billReachable)
                .billPrinterLatencyMs(billLatency)
                .kotPrinterHost(effectiveKotHost)
                .kotPrinterPort(effectiveKotPort)
                .kotPrinterReachable(kotReachable)
                .kotPrinterLatencyMs(kotLatency)
                .juiceKotPrinterHost(effectiveJuiceKotHost)
                .juiceKotPrinterPort(effectiveJuiceKotPort)
                .juiceKotPrinterReachable(juiceKotReachable)
                .juiceKotPrinterLatencyMs(juiceKotLatency)
                .freeHeap(freeHeapVal)
                .freeHeapBytes(freeHeapVal)
                .freePsram(freePsramVal)
                .freePsramBytes(freePsramVal)
                .uptimeSeconds(hb != null ? hb.getUptimeSeconds() : null)
                .lastError(hb != null ? hb.getLastError() : null)
                .hardware(hwVal)
                .hardwareModel(hwVal)
                .pendingJobs((int) pendingInDb)
                .unsyncedJobsCount((hb != null && hb.getUnsyncedJobsCount() != null) ? hb.getUnsyncedJobsCount() : 0)
                .mode(online ? "BRIDGE" : "DIRECT")
                .lastHeartbeatAt(lastHbTime)
                .build();
    }

    public PrintBridgeHeartbeatDTO getLatestHeartbeat() {
        return latestHeartbeat.get();
    }
}
