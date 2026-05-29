package com.oshmarket.dto.tenant;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PaymentHistoryItemDto {
    private Long id;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String monthName;
    private String description;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private String paymentMethodName;
    private String rejectReason;
}
