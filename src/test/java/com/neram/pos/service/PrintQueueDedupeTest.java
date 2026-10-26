package com.neram.pos.service;

import com.neram.pos.entity.PrintJob;
import com.neram.pos.entity.PrintJobStatus;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.PrintJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A second kitchen round must never be swallowed by a first ticket that is still waiting in the queue. */
class PrintQueueDedupeTest {

    private PrintJobRepository repo;
    private PrintBridgeService service;

    @BeforeEach
    void setUp() {
        repo = mock(PrintJobRepository.class);
        service = new PrintBridgeService(repo, mock(BillRepository.class), mock(JdbcTemplate.class), mock(TransactionTemplate.class));
        when(repo.save(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));
    }

    private PrintJob kotJob(String jobId, String key) {
        return PrintJob.builder().jobId(jobId).billId(26L).billNumber("NRM-1").jobType("KOT")
                .targetPrinterHost("192.168.1.47").targetPrinterPort(9100).payload(new byte[]{1})
                .idempotencyKey(key).build();
    }

    @Test
    void secondRoundWithNewKeyBecomesItsOwnJobEvenWhileFirstIsStillPending() {
        PrintJob firstStillPending = kotJob("job-1", "KOT_26_v0");
        firstStillPending.setStatus(PrintJobStatus.PENDING);
        when(repo.findByIdempotencyKey("KOT_26_v1")).thenReturn(Optional.empty());
        when(repo.findByBillIdAndJobTypeAndStatusIn(eq26(), eq("KOT"), any())).thenReturn(List.of(firstStillPending));

        PrintJob result = service.enqueuePrintJob(kotJob("job-2", "KOT_26_v1"));

        assertEquals("job-2", result.getJobId(), "the new round must not be replaced by the waiting first job");
        verify(repo).save(any(PrintJob.class));
    }

    @Test
    void sameKeyIsStillTreatedAsADuplicateClick() {
        PrintJob existing = kotJob("job-1", "KOT_26_v0");
        when(repo.findByIdempotencyKey("KOT_26_v0")).thenReturn(Optional.of(existing));

        PrintJob result = service.enqueuePrintJob(kotJob("job-1b", "KOT_26_v0"));

        assertEquals("job-1", result.getJobId());
        verify(repo, never()).save(any(PrintJob.class));
    }

    @Test
    void withoutAKeyTheOldWaitingJobGuardStillApplies() {
        PrintJob waiting = kotJob("job-1", null);
        waiting.setStatus(PrintJobStatus.PENDING);
        when(repo.findByBillIdAndJobTypeAndStatusIn(eq26(), eq("KOT"), any())).thenReturn(List.of(waiting));

        PrintJob result = service.enqueuePrintJob(kotJob("job-2", null));

        assertEquals("job-1", result.getJobId());
        verify(repo, never()).save(any(PrintJob.class));
    }

    private static Long eq26() { return org.mockito.ArgumentMatchers.eq(26L); }
}
