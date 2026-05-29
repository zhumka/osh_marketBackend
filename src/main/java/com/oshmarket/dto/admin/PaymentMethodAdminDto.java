package com.oshmarket.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentMethodAdminDto {
    private Long id;
    private String name;
    private String requisites;
    private boolean active;
    private int sortOrder;
    private boolean hasQr;
    private String qrUrl;
}
