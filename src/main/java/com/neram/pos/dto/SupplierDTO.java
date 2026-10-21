package com.neram.pos.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDTO {
    private Long id;
    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String gstNumber;
    private Integer paymentTerms;
    private String category;
    private String bankName;
    private String bankAccount;
    private String bankIfsc;
    private String upiId;
    private String notes;
    private Boolean active;
    private LocalDateTime createdAt;

    // Computed fields from analytics
    private Double totalInvoiced;
    private Double totalPaid;
    private Double totalOutstanding;
    private Integer invoiceCount;
}
