package com.oshmarket.dto.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateTenantRequest {
    private String fullName;
    private String phone;
    private String email;
    private String passportSeries;
    private String passportNumber;
    private LocalDate passportIssuedDate;
    private String passportIssuedBy;
    private BigDecimal monthlyRent;
}
