package com.oshmarket.dto.admin;

import com.oshmarket.dto.tenant.PaymentHistoryItemDto;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TenantDetailDto {
    private Long id;
    private String inn;
    private String fullName;
    private String phone;
    private String email;
    private String passportSeries;
    private String passportNumber;
    private LocalDate passportIssuedDate;
    private String passportIssuedBy;
    private String placeNumber;
    private String aisle;
    private String department;
    private BigDecimal monthlyRent;
    private LocalDate startDate;
    private LocalDate plannedEndDate;
    private BigDecimal debt;
    private BigDecimal penaltyDebt;
    private BigDecimal totalDebt;
    private String status;
    private LocalDate lastPaymentDate;
    private boolean hasActiveContract;
    private List<PaymentHistoryItemDto> paymentHistory;
}
