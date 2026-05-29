package com.oshmarket.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AnalyticsDto {
    private long totalTenants;
    private long occupiedPlaces;
    private long freePlaces;
    private long debtors;
    private BigDecimal totalDebt;
    private BigDecimal totalMonthlyRevenue;
    private List<DebtorDto> topDebtors;
}
