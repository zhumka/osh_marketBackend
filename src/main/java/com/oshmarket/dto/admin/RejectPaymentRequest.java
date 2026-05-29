package com.oshmarket.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectPaymentRequest {

    @NotBlank(message = "Укажите причину отклонения")
    @Size(max = 500, message = "Причина не должна превышать 500 символов")
    private String reason;
}
