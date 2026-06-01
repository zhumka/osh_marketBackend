package com.oshmarket.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TenantListItemDto {
    private Long id;
    private String inn;
    private String fullName;
    private String placeNumber;
    private String phone;
    private String location;
    private BigDecimal monthlyRent;
    private BigDecimal debt;
    private BigDecimal penaltyDebt;
    private BigDecimal totalDebt;
    private String status;
}
