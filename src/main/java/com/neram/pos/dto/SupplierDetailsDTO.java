package com.neram.pos.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDetailsDTO {
    private SupplierDTO supplier;
    private List<SupplierInvoiceDTO> recentInvoices;
    private List<SupplierPaymentDTO> recentPayments;
}
