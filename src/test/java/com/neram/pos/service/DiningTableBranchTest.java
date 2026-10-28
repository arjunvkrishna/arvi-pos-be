package com.neram.pos.service;

import com.neram.pos.access.OutletContext;
import com.neram.pos.dto.DiningTableDTO;
import com.neram.pos.dto.DiningTableRequestDTO;
import com.neram.pos.entity.DiningTable;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.DiningTableRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tables belong to one branch: they can be created only inside a branch, carry a floor, and are never invented cafe-wide. */
class DiningTableBranchTest {

    private DiningTableRepository tables;
    private DiningTableService service;

    @BeforeEach
    void setUp() {
        tables = mock(DiningTableRepository.class);
        service = new DiningTableService(tables, mock(BillRepository.class), mock(AuditLogService.class));
        when(tables.save(any(DiningTable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tables.findByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of());
        when(tables.existsByNameIgnoreCaseAndActiveTrue(any())).thenReturn(false);
        when(tables.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void clear() {
        OutletContext.clear();
    }

    private static DiningTableRequestDTO request(String name, String floor) {
        return DiningTableRequestDTO.builder().name(name).floor(floor).build();
    }

    @Test
    @DisplayName("an owner looking at the whole cafe must pick a branch before adding a table")
    void tableNeedsABranch() {
        assertThrows(IllegalArgumentException.class, () -> service.createTable(request("T1", "Ground")));
        verify(tables, never()).save(any());
    }

    @Test
    @DisplayName("inside a branch a table is created with its floor, trimmed")
    void tableKeepsItsFloor() {
        OutletContext.set(7L);
        DiningTableDTO created = service.createTable(request("T1", "  First floor "));
        assertEquals("First floor", created.getFloor());
        assertEquals("T1", created.getName());
    }

    @Test
    @DisplayName("a branch without floors just leaves the floor empty")
    void floorIsOptional() {
        OutletContext.set(7L);
        assertNull(service.createTable(request("T2", "  ")).getFloor());
        assertNull(service.createTable(request("T3", null)).getFloor());
    }

    @Test
    @DisplayName("a floor name that is too long is refused")
    void floorNameIsBounded() {
        OutletContext.set(7L);
        assertThrows(IllegalArgumentException.class, () -> service.createTable(request("T4", "x".repeat(51))));
    }

    @Test
    @DisplayName("starter tables are made for a new branch's first visit, but never as cafe-wide shared tables")
    void starterTablesOnlyInsideABranch() {
        // whole cafe, no branch picked: nothing is invented
        assertTrue(service.getAllActiveTables().isEmpty());
        verify(tables, never()).save(any());

        // inside a branch: the starter set is created
        OutletContext.set(9L);
        service.getAllActiveTables();
        verify(tables, times(9)).save(any(DiningTable.class));
    }
}
