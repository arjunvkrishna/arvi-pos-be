package com.neram.pos.dto;

import com.neram.pos.service.BillSplit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** What the new bills of a split will say, before anything is printed. The amounts add up to {@code netAmount}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitBillPreviewDTO {
    private Long billId;
    private String billNumber;
    private String status;
    private BigDecimal netAmount;
    private List<BillSplit.Portion> portions;
}
