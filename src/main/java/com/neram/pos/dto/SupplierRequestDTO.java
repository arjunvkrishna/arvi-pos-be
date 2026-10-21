package com.neram.pos.dto;

import lombok.Data;

@Data
public class SupplierRequestDTO {
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
}
