package com.neram.pos.service;

import com.neram.pos.dto.PrintJobStatusDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.PrintJob;
import com.neram.pos.entity.PrintJobStatus;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.PrintJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** What the server does with a phone's report on a ticket it printed itself. */
class DeviceResultTest {

    private static final String PHONE = "device:waiter1:abc";

    private PrintJobRepository repo;
    private BillRepository bills;
    private PrintBridgeService service;
    private PrintJob job;
    private AuditLogService audit;

    @BeforeEach
    void setUp() {
        repo = mock(PrintJobRepository.class);
        bills = mock(BillRepository.class);
        service = new PrintBridgeService(repo, bills, mock(JdbcTemplate.class), mock(TransactionTemplate.class));
        audit = mock(AuditLogService.class);
        ReflectionTestUtils.setField(service, "auditLogService", audit);
        job = PrintJob.builder().jobId("j1").billId(7L).billNumber("NRM-7").jobType("BILL").targetPrinterHost("192.168.1.37")
                .targetPrinterPort(9100).payload(new byte[]{1, 2, 3}).status(PrintJobStatus.CLAIMED).attemptCount(1)
                .claimedByBridgeId(PHONE).leaseExpiresAt(LocalDateTime.now().plusSeconds(40)).build();
        when(repo.findByJobId("j1")).thenReturn(Optional.of(job));
        when(repo.save(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void printed_completes_the_job_and_stamps_the_bill() {
        Bill bill = new Bill();
        when(bills.findById(7L)).thenReturn(Optional.of(bill));

        PrintJobStatusDTO out = service.recordDeviceResult("j1", PHONE, "PRINTED", null, 3);

        assertEquals("COMPLETED", out.getStatus());
        assertNotNull(job.getCompletedAt());
        assertNotNull(bill.getPrintedAt(), "same bookkeeping as a bridge print");
    }

    @Test
    void a_second_printed_report_changes_nothing() {
        when(bills.findById(7L)).thenReturn(Optional.of(new Bill()));
        service.recordDeviceResult("j1", PHONE, "PRINTED", null, 3);
        LocalDateTime done = job.getCompletedAt();

        service.recordDeviceResult("j1", PHONE, "PRINTED", null, 3);

        assertEquals(PrintJobStatus.COMPLETED, job.getStatus());
        assertEquals(done, job.getCompletedAt());
    }

    @Test
    void not_sent_hands_the_job_straight_to_the_cloud_bridge() {
        PrintJobStatusDTO out = service.recordDeviceResult("j1", PHONE, "NOT_SENT", "connect timed out", 0);

        assertEquals("PENDING", out.getStatus(), "PENDING is exactly what the bridge's claim query picks up");
        assertFalse(job.getNextEligibleAt().isAfter(LocalDateTime.now()), "no waiting");
        assertNull(job.getLeaseExpiresAt());
        assertTrue(job.getLastErrorMessage().contains("cloud bridge") && job.getLastErrorMessage().contains("connect timed out"));
    }

    @Test
    void unsure_goes_to_review_rather_than_risking_a_double_print() {
        PrintJobStatusDTO out = service.recordDeviceResult("j1", PHONE, "UNSURE", "reset by peer", 40);

        assertEquals("NEEDS_REVIEW", out.getStatus());
        assertEquals(40, job.getBytesPrinted());
    }

    @Test
    void another_device_cannot_report_on_the_ticket() {
        assertThrows(IllegalStateException.class, () -> service.recordDeviceResult("j1", "device:other:zzz", "PRINTED", null, 3));
        assertEquals(PrintJobStatus.CLAIMED, job.getStatus());
    }

    @Test
    void a_late_not_sent_does_not_undo_a_job_the_bridge_already_finished() {
        job.setStatus(PrintJobStatus.COMPLETED);

        service.recordDeviceResult("j1", PHONE, "NOT_SENT", "late", 0);

        assertEquals(PrintJobStatus.COMPLETED, job.getStatus());
    }

    @Test
    void unknown_job_and_unknown_outcome_are_rejected() {
        when(repo.findByJobId("nope")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.recordDeviceResult("nope", PHONE, "PRINTED", null, 1));
        assertThrows(IllegalArgumentException.class, () -> service.recordDeviceResult("j1", PHONE, "MAYBE", null, 1));
    }

    @Test
    void every_outcome_leaves_one_audit_line_naming_the_phone_the_ticket_and_the_printer() {
        job.setId(77L);
        when(bills.findById(7L)).thenReturn(Optional.of(new Bill()));
        service.recordDeviceResult("j1", PHONE, "PRINTED", null, 3);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("DIRECT_PRINT"), eq("PRINT_JOB"), eq(77L), isNull(), detail.capture());
        assertTrue(detail.getValue().contains("type=BILL") && detail.getValue().contains("bill=NRM-7")
                && detail.getValue().contains("printer=192.168.1.37:9100") && detail.getValue().contains("device=" + PHONE), detail.getValue());
    }

    @Test
    void a_handback_and_a_doubtful_print_are_audited_as_such() {
        job.setId(77L);
        service.recordDeviceResult("j1", PHONE, "NOT_SENT", "connect timed out", 0);
        verify(audit).log(eq("DIRECT_PRINT_FALLBACK"), eq("PRINT_JOB"), eq(77L), isNull(), contains("connect timed out"));

        job.setStatus(PrintJobStatus.CLAIMED);
        service.recordDeviceResult("j1", PHONE, "UNSURE", "reset", 40);
        verify(audit).log(eq("DIRECT_PRINT_REVIEW"), eq("PRINT_JOB"), eq(77L), isNull(), contains("device=" + PHONE));
    }

    @Test
    void a_failing_audit_write_never_blocks_the_print_result() {
        doThrow(new RuntimeException("audit down")).when(audit).log(any(), any(), any(), any(), any());
        when(bills.findById(7L)).thenReturn(Optional.of(new Bill()));
        assertEquals("COMPLETED", service.recordDeviceResult("j1", PHONE, "PRINTED", null, 3).getStatus());
    }
}
