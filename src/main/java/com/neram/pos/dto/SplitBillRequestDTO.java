package com.neram.pos.dto;

import com.neram.pos.service.BillSplit;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** How to divide a bill by items: one entry per new bill, each listing how many of which line go on it. */
@Data
public class SplitBillRequestDTO {
    private List<Portion> portions;

    @Data
    public static class Portion {
        private List<Line> items;
    }

    @Data
    public static class Line {
        private Long billItemId;
        private Integer quantity;
    }

    public List<List<BillSplit.Share>> shares() {
        List<List<BillSplit.Share>> out = new ArrayList<>();
        if (portions == null) return out;
        for (Portion p : portions) {
            List<BillSplit.Share> one = new ArrayList<>();
            if (p != null && p.getItems() != null) {
                for (Line l : p.getItems()) {
                    if (l == null || l.getBillItemId() == null || l.getQuantity() == null) continue;
                    one.add(new BillSplit.Share(l.getBillItemId(), l.getQuantity()));
                }
            }
            out.add(one);
        }
        return out;
    }
}
