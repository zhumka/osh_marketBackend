package com.oshmarket.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardDto {
    private long totalTenants;
    private long occupiedPlaces;
    private long freePlaces;
    private long debtors;
    private BigDecimal totalDebt;
    private BigDecimal totalPenaltyDebt;
    private List<TenantListItemDto> recentTenants;
}
