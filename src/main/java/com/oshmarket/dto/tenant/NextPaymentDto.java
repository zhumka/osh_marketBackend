package com.oshmarket.dto.tenant;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class NextPaymentDto {
    private LocalDate dueDate;
    private BigDecimal monthlyRent;
    private BigDecimal debt;
    private BigDecimal penaltyDebt;
    private BigDecimal totalDue;
}
