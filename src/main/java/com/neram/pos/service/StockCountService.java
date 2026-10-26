package com.neram.pos.service;

import com.neram.pos.dto.StockOpsDTO;
import com.neram.pos.entity.InventoryItem;
import com.neram.pos.entity.InventoryTransaction;
import com.neram.pos.entity.StockCount;
import com.neram.pos.entity.StockCountLine;
import com.neram.pos.entity.User;
import com.neram.pos.repository.InventoryItemRepository;
import com.neram.pos.repository.InventoryTransactionRepository;
import com.neram.pos.repository.StockCountLineRepository;
import com.neram.pos.repository.StockCountRepository;
import com.neram.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Physical stock count. Starting a count freezes the book quantity of each item; people enter what they found; applying books the
 * difference (counted minus the frozen book figure) on top of the CURRENT stock, so sales made while counting are not lost.
 */
@Service
@RequiredArgsConstructor
public class StockCountService {

    private final StockCountRepository counts;
    private final StockCountLineRepository lines;
    private final InventoryItemRepository items;
    private final InventoryTransactionRepository transactions;
    private final UserRepository users;
    private final AuditLogService auditLog;

    @Transactional
    public StockOpsDTO.Count start(String category, String note, String username) {
        if (counts.existsByStatus("DRAFT")) {
            throw new IllegalArgumentException("A stock count is already open. Finish or cancel it first.");
        }
        String cat = category == null || category.isBlank() || category.equalsIgnoreCase("ALL") ? null : InventoryService.normalizeCategory(category);
        List<InventoryItem> list = (cat == null ? items.findByActiveTrue() : items.findByItemCategoryIgnoreCaseAndActiveTrue(cat));
        if (list.isEmpty()) throw new IllegalArgumentException("There are no stock items to count.");

        StockCount c = new StockCount();
        c.setScope(cat == null ? "All items" : cat);
        c.setNote(note == null || note.isBlank() ? null : note.trim());
        c.setCreatedBy(username);
        c = counts.save(c);
        Long id = c.getId();
        lines.saveAll(list.stream().map(i -> new StockCountLine(null, id, i.getId(), i.getName(), i.getUnit(),
                i.getQuantity() != null ? i.getQuantity() : 0.0, null, i.getCostPerUnit() != null ? i.getCostPerUnit() : java.math.BigDecimal.ZERO)).toList());
        auditLog.log("STOCK_COUNT_START", "STOCK_COUNT", id, null, c.getScope());
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<StockOpsDTO.Count> list() {
        return counts.findTop50ByOrderByCreatedAtDesc().stream().map(c -> toDTO(c, lines.findByCountIdOrderByItemNameAsc(c.getId()), false)).toList();
    }

    @Transactional(readOnly = true)
    public StockOpsDTO.Count get(Long id) {
        StockCount c = counts.findById(id).orElseThrow(() -> new IllegalArgumentException("Stock count not found."));
        return toDTO(c, lines.findByCountIdOrderByItemNameAsc(id), true);
    }

    /** Saves counted figures. An empty value clears the entry (item not counted). */
    @Transactional
    public StockOpsDTO.Count saveLines(Long id, List<StockOpsDTO.LineEntry> entries) {
        StockCount c = counts.findById(id).orElseThrow(() -> new IllegalArgumentException("Stock count not found."));
        if (!"DRAFT".equals(c.getStatus())) throw new IllegalArgumentException("This stock count is already " + c.getStatus().toLowerCase() + ".");
        Map<Long, StockCountLine> byId = lines.findByCountIdOrderByItemNameAsc(id).stream().collect(Collectors.toMap(StockCountLine::getId, Function.identity()));
        for (StockOpsDTO.LineEntry e : entries == null ? List.<StockOpsDTO.LineEntry>of() : entries) {
            StockCountLine l = byId.get(e.getLineId());
            if (l == null) throw new IllegalArgumentException("Line " + e.getLineId() + " is not part of this count.");
            Double v = e.getCountedQty();
            if (v != null && (v < 0 || v.isNaN() || v.isInfinite())) throw new IllegalArgumentException("Counted quantity for " + l.getItemName() + " must be zero or more.");
            l.setCountedQty(v == null ? null : StockUnits.round3(v));
        }
        lines.saveAll(byId.values());
        return get(id);
    }

    @Transactional
    public StockOpsDTO.Count apply(Long id, String username) {
        StockCount c = counts.findById(id).orElseThrow(() -> new IllegalArgumentException("Stock count not found."));
        if (!"DRAFT".equals(c.getStatus())) throw new IllegalArgumentException("This stock count is already " + c.getStatus().toLowerCase() + ".");
        User user = username != null ? users.findByUsername(username).orElse(null) : null;
        String role = user != null && user.getRole() != null ? user.getRole().name() : "MANAGER";
        int booked = 0;
        for (StockCountLine l : lines.findByCountIdOrderByItemNameAsc(id)) {
            Double diff = StockOpsMath.variance(l.getBookQty(), l.getCountedQty());
            if (diff == null || Math.abs(diff) < 0.0005) continue;
            InventoryItem item = items.findById(l.getItemId()).orElse(null);
            if (item == null) continue;
            double before = item.getQuantity() != null ? item.getQuantity() : 0.0;
            double after = StockUnits.round3(before + diff);
            item.setQuantity(after);
            double unitVol = item.getUnitVolume() != null ? item.getUnitVolume() : 1.0;
            item.setTotalVolume(after * unitVol);
            item.setLastUpdated(LocalDateTime.now());
            items.save(item);
            transactions.save(InventoryTransaction.builder()
                    .itemId(item.getId()).transactionType("ADJUSTMENT").quantity(Math.abs(diff))
                    .previousStock(before).newStock(after).unitVolume(unitVol).volumeUnit(item.getVolumeUnit())
                    .totalVolume(Math.abs(diff) * unitVol).costPerVolume(item.getCostPerVolume())
                    .userId(user != null ? user.getId() : null).userName(username != null ? username : "SYSTEM").userRole(role)
                    .itemCategory(item.getItemCategory()).reasonType("COUNT")
                    .notes("Stock count #" + id + ": book " + trim(l.getBookQty()) + ", counted " + trim(l.getCountedQty()) + " " + item.getUnit()
                            + (diff < 0 ? " (missing " : " (extra ") + trim(Math.abs(diff)) + ")")
                    .createdAt(LocalDateTime.now()).build());
            booked++;
        }
        c.setStatus("APPLIED");
        c.setAppliedBy(username);
        c.setAppliedAt(LocalDateTime.now());
        counts.save(c);
        auditLog.log("STOCK_COUNT_APPLY", "STOCK_COUNT", id, null, booked + " item(s) adjusted");
        return get(id);
    }

    @Transactional
    public StockOpsDTO.Count cancel(Long id, String username) {
        StockCount c = counts.findById(id).orElseThrow(() -> new IllegalArgumentException("Stock count not found."));
        if (!"DRAFT".equals(c.getStatus())) throw new IllegalArgumentException("This stock count is already " + c.getStatus().toLowerCase() + ".");
        c.setStatus("CANCELLED");
        counts.save(c);
        auditLog.log("STOCK_COUNT_CANCEL", "STOCK_COUNT", id, null, username);
        return get(id);
    }

    private static String trim(Double v) {
        if (v == null) return "-";
        return v == Math.rint(v) ? String.valueOf(v.longValue()) : String.valueOf(v);
    }

    static StockOpsDTO.Count toDTO(StockCount c, List<StockCountLine> ls, boolean withLines) {
        int counted = 0, diffs = 0; double value = 0;
        List<StockOpsDTO.CountLine> out = new java.util.ArrayList<>();
        for (StockCountLine l : ls) {
            Double v = StockOpsMath.variance(l.getBookQty(), l.getCountedQty());
            if (l.getCountedQty() != null) counted++;
            boolean differs = v != null && Math.abs(v) >= 0.0005;
            if (differs) { diffs++; value += v * l.getCostPerUnit().doubleValue(); }
            if (withLines) out.add(StockOpsDTO.CountLine.builder().id(l.getId()).itemId(l.getItemId()).name(l.getItemName()).unit(l.getUnit())
                    .bookQty(l.getBookQty()).countedQty(l.getCountedQty()).variance(v).costPerUnit(l.getCostPerUnit().doubleValue())
                    .varianceValue(v == null ? null : Double.valueOf(differs ? StockOpsMath.round(v * l.getCostPerUnit().doubleValue(), 2) : 0.0)).build());
        }
        return StockOpsDTO.Count.builder().id(c.getId()).status(c.getStatus()).scope(c.getScope()).note(c.getNote())
                .createdBy(c.getCreatedBy()).createdAt(c.getCreatedAt()).appliedBy(c.getAppliedBy()).appliedAt(c.getAppliedAt())
                .itemCount(ls.size()).countedCount(counted).differenceCount(diffs).varianceValue(StockOpsMath.round(value, 2))
                .lines(withLines ? out : null).build();
    }
}
