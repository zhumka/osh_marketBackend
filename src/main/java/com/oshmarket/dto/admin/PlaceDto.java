package com.oshmarket.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlaceDto {
    private Long id;
    private String placeNumber;
    private String aisle;
    private String department;
    private BigDecimal monthlyRent;
    private boolean occupied;
}
