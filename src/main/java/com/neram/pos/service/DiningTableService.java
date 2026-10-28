package com.neram.pos.service;

import com.neram.pos.dto.DiningTableDTO;
import com.neram.pos.dto.DiningTableRequestDTO;
import com.neram.pos.entity.Bill;
import com.neram.pos.entity.DiningTable;
import com.neram.pos.repository.BillRepository;
import com.neram.pos.repository.DiningTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiningTableService {

    private final DiningTableRepository tableRepository;
    private final BillRepository billRepository;
    private final AuditLogService auditLogService;

    private static final List<String> DEFAULT_TABLE_NAMES = List.of(
            "T-01", "T-02", "T-03", "T-04", "T-05", "T-06", "T-07", "T-08", "Takeaway"
    );

    @Transactional
    public List<DiningTableDTO> getAllActiveTables() {
        List<DiningTable> tables = tableRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        // Starter tables are only made for a real branch; the cafe-wide view (no branch picked) never invents shared tables.
        if (tables.isEmpty() && currentBranch() != null) {
            int order = 1;
            for (String defaultName : DEFAULT_TABLE_NAMES) {
                DiningTable t = DiningTable.builder()
                        .name(defaultName)
                        .active(true)
                        .displayOrder(order++)
                        .build();
                tableRepository.save(t);
            }
            tables = tableRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        }
        return tables.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** The branch the caller works in (or picked); null when an owner or main manager looks at the whole cafe. */
    private static Long currentBranch() {
        Long branch = com.neram.pos.access.OutletContext.currentIdOrNull();
        return branch != null && branch != com.neram.pos.access.OutletContext.NO_OUTLET ? branch : null;
    }

    private static String cleanFloor(String floor) {
        if (floor == null || floor.isBlank()) return null;
        String f = floor.trim();
        if (f.length() > 50) throw new IllegalArgumentException("Floor name is too long (max 50 characters)");
        return f;
    }

    @Transactional
    public DiningTableDTO createTable(DiningTableRequestDTO request) {
        if (request == null || request.getName() == null || request.getName().trim().isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }
        String tableName = request.getName().trim();
        if (currentBranch() == null) {
            throw new IllegalArgumentException("Pick a branch first: a table always belongs to one branch");
        }
        String floor = cleanFloor(request.getFloor());

        // Check if an active table with same name exists
        if (tableRepository.existsByNameIgnoreCaseAndActiveTrue(tableName)) {
            throw new IllegalArgumentException("A table with name '" + tableName + "' already exists");
        }

        // Check if previously deactivated table exists to reactivate
        DiningTable table = tableRepository.findByNameIgnoreCase(tableName)
                .map(existing -> {
                    existing.setActive(true);
                    if (request.getFloor() != null) {
                        existing.setFloor(floor);
                    }
                    if (request.getDisplayOrder() != null) {
                        existing.setDisplayOrder(request.getDisplayOrder());
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    int order = request.getDisplayOrder() != null ? request.getDisplayOrder() : 0;
                    if (order == 0) {
                        List<DiningTable> current = tableRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
                        order = current.size() + 1;
                    }
                    return DiningTable.builder()
                            .name(tableName)
                            .floor(floor)
                            .active(true)
                            .displayOrder(order)
                            .build();
                });

        DiningTable saved = tableRepository.save(table);
        auditLogService.log("CREATE_TABLE", "DINING_TABLE", saved.getId(), null,
                "Created dining table: " + saved.getName());
        log.info("Created dining table: {}", saved.getName());
        return toDTO(saved);
    }

    @Transactional
    public void deleteTable(Long id) {
        DiningTable table = tableRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with id: " + id));

        // Guard: Check for active orders on this table
        List<Bill.Status> activeStatuses = List.of(
                Bill.Status.ACTIVE,
                Bill.Status.KOT_REQUESTED,
                Bill.Status.KOT_PRINTED,
                Bill.Status.BILL_REQUESTED,
                Bill.Status.BILL_PRINTED
        );
        boolean hasActiveOrder = billRepository.findFirstByTableNumberAndStatusInOrderByCreatedAtDesc(table.getName(), activeStatuses).isPresent();
        if (hasActiveOrder) {
            throw new IllegalStateException("Cannot delete table " + table.getName() + " because an active order is in progress!");
        }

        table.setActive(false);
        tableRepository.save(table);
        auditLogService.log("DELETE_TABLE", "DINING_TABLE", table.getId(), table.getName(),
                "Deactivated dining table: " + table.getName());
        log.info("Deleted/Deactivated dining table: {}", table.getName());
    }

    public DiningTableDTO toDTO(DiningTable table) {
        return DiningTableDTO.builder()
                .id(table.getId())
                .name(table.getName())
                .floor(table.getFloor())
                .active(table.getActive())
                .displayOrder(table.getDisplayOrder())
                .createdAt(table.getCreatedAt())
                .build();
    }
}
