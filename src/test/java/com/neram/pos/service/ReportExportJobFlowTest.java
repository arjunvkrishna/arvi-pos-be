package com.neram.pos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neram.pos.entity.ReportExportData;
import com.neram.pos.entity.ReportExportJob;
import com.neram.pos.repository.ReportExportDataRepository;
import com.neram.pos.repository.ReportExportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Claim marks a job PROCESSING in one step; recovery requeues stuck jobs and gives up after MAX_ATTEMPTS. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportExportJobFlowTest {
    @Mock private ReportExportJobRepository jobs;
    @Mock private ReportExportDataRepository data;
    @Mock private ReportGenerationService generator;

    private ReportExportService svc;

    @BeforeEach
    void setUp() {
        svc = new ReportExportService(jobs, data, generator, new ObjectMapper());
    }

    private ReportExportJob job(String status, int attempts) {
        return ReportExportJob.builder().id(UUID.randomUUID()).reportId("sales_summary").format("CSV")
                .status(status).attempts(attempts).createdAt(Instant.now().minusSeconds(3600)).build();
    }

    @Test
    void claimMarksTheJobProcessingWithStartTimeAndAttempt() {
        ReportExportJob j = job("PENDING", 0);
        when(jobs.findNextPendingJob()).thenReturn(Optional.of(j));

        Optional<ReportExportJob> claimed = svc.claimNextJob();

        assertTrue(claimed.isPresent());
        assertEquals("PROCESSING", j.getStatus());
        assertNotNull(j.getStartedAt());
        assertEquals(1, j.getAttempts());
        verify(jobs).save(j);
    }

    @Test
    void claimWithNothingPendingDoesNothing() {
        when(jobs.findNextPendingJob()).thenReturn(Optional.empty());
        assertTrue(svc.claimNextJob().isEmpty());
        verify(jobs, never()).save(any());
    }

    @Test
    void executeStoresTheFileAndCompletesTheJob() {
        ReportExportJob j = job("PROCESSING", 1);
        when(generator.generateReport(eq("sales_summary"), eq("CSV"), any())).thenReturn(new byte[]{1, 2, 3});

        svc.executeJob(j);

        assertEquals("COMPLETED", j.getStatus());
        assertNotNull(j.getCompletedAt());
        verify(data).save(any(ReportExportData.class));
    }

    @Test
    void executeMarksTheJobFailedWhenGenerationThrows() {
        ReportExportJob j = job("PROCESSING", 1);
        when(generator.generateReport(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        svc.executeJob(j);

        assertEquals("FAILED", j.getStatus());
        assertEquals("boom", j.getErrorMessage());
        verify(data, never()).save(any());
    }

    @Test
    void stuckJobIsRequeuedUntilItHasUsedAllAttempts() {
        ReportExportJob retry = job("PROCESSING", 1);
        ReportExportJob exhausted = job("PROCESSING", ReportExportService.MAX_ATTEMPTS);
        when(jobs.findByStatusAndStartedAtBefore(eq("PROCESSING"), any())).thenReturn(List.of(retry, exhausted));

        int touched = svc.recoverStuckJobs(Instant.now());

        assertEquals(2, touched);
        assertEquals("PENDING", retry.getStatus());
        assertNull(retry.getStartedAt());
        assertEquals("FAILED", exhausted.getStatus());
        assertNotNull(exhausted.getErrorMessage());
    }
}
