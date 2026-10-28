package com.neram.pos.service;

import com.neram.pos.dto.TipPoolCalculateRequestDTO;
import com.neram.pos.dto.TipPoolDistributionDTO;
import com.neram.pos.entity.Employee;
import com.neram.pos.entity.TipPoolDistribution;
import com.neram.pos.entity.TipPoolEmployeeShare;
import com.neram.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tip shares are proportional to hours and always add up to the tips collected, to the paisa. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TipPoolServiceTest {
    @Mock private TipPoolDistributionRepository dists;
    @Mock private TipPoolEmployeeShareRepository shares;
    @Mock private BillRepository bills;
    @Mock private AttendanceRepository attendance;
    @Mock private EmployeeRepository employees;

    private TipPoolService svc;
    private final LocalDate day = LocalDate.of(2026, 10, 25);

    @BeforeEach
    void setUp() {
        svc = new TipPoolService(dists, shares, bills, attendance, employees);
        for (long id = 1; id <= 3; id++) {
            Employee e = new Employee();
            e.setId(id);
            e.setName("Emp" + id);
            when(employees.findById(id)).thenReturn(Optional.of(e));
        }
        when(dists.findByDistributionDate(day, null)).thenReturn(Optional.empty());
        when(dists.save(any(TipPoolDistribution.class))).thenAnswer(i -> i.getArgument(0));
    }

    private TipPoolCalculateRequestDTO request(String tips, String... hoursPerEmployee) {
        TipPoolCalculateRequestDTO r = new TipPoolCalculateRequestDTO();
        r.setDistributionDate(day);
        r.setOverrideTotalTips(new BigDecimal(tips));
        List<TipPoolCalculateRequestDTO.EmployeeHoursInput> in = new ArrayList<>();
        for (int i = 0; i < hoursPerEmployee.length; i++) {
            TipPoolCalculateRequestDTO.EmployeeHoursInput h = new TipPoolCalculateRequestDTO.EmployeeHoursInput();
            h.setEmployeeId((long) (i + 1));
            h.setHoursWorked(new BigDecimal(hoursPerEmployee[i]));
            in.add(h);
        }
        r.setCustomHours(in);
        return r;
    }

    private static BigDecimal sum(TipPoolDistributionDTO d) {
        return d.getShares().stream().map(s -> s.getTipAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void sharesAddUpExactlyEvenWhenTheRateDoesNotDivideEvenly() {
        TipPoolDistributionDTO d = svc.calculateAndDistribute(request("100.00", "8", "8", "8"), 1L);
        assertEquals(0, new BigDecimal("100.00").compareTo(sum(d)), "shares must total the tips: " + sum(d));
        assertEquals(3, d.getShares().size());
    }

    @Test
    void longerHoursEarnMore() {
        TipPoolDistributionDTO d = svc.calculateAndDistribute(request("300.00", "6", "3"), 1L);
        BigDecimal first = d.getShares().stream().filter(s -> s.getEmployeeId() == 1L).findFirst().get().getTipAmount();
        BigDecimal second = d.getShares().stream().filter(s -> s.getEmployeeId() == 2L).findFirst().get().getTipAmount();
        assertEquals(0, new BigDecimal("200.00").compareTo(first));
        assertEquals(0, new BigDecimal("100.00").compareTo(second));
    }

    @Test
    void zeroHoursCannotBeDistributed() {
        assertThrows(IllegalArgumentException.class, () -> svc.calculateAndDistribute(request("50.00", "0"), 1L));
    }

    @Test
    void aDayThatWasAlreadyPaidOutCannotBeRecalculated() {
        TipPoolDistribution existing = TipPoolDistribution.builder().id(5L).distributionDate(day)
                .totalTips(new BigDecimal("100")).totalHours(new BigDecimal("8")).ratePerHour(new BigDecimal("12.5")).build();
        TipPoolEmployeeShare paid = TipPoolEmployeeShare.builder().tipAmount(new BigDecimal("100")).hoursWorked(new BigDecimal("8")).paid(true).build();
        existing.setShares(new ArrayList<>(List.of(paid)));
        when(dists.findByDistributionDate(day, null)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> svc.calculateAndDistribute(request("120.00", "8"), 1L));
        assertEquals(1, existing.getShares().size());
    }
}
