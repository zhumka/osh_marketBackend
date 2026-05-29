package com.oshmarket.dto.tenant;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TenantProfileDto {
    private String inn;
    private String fullName;
    private String placeNumber;
    private String aisle;
    private String department;
    private BigDecimal monthlyRent;
    private BigDecimal debt;
    private boolean isActive;
    private LocalDate lastPaymentDate;
    private LocalDate startDate;
}
