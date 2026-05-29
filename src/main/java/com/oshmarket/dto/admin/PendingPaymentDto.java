package com.oshmarket.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PendingPaymentDto {
    private Long id;
    private Long tenantId;
    private String inn;
    private String fullName;
    private String placeNumber;
    private BigDecimal amount;
    private String paymentMethodName;
    private LocalDateTime submittedAt;
    private boolean hasReceipt;
    /** Относительный путь для получения чека. */
    private String receiptUrl;
}
