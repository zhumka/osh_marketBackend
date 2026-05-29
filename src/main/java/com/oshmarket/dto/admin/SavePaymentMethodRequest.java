package com.oshmarket.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SavePaymentMethodRequest {

    @NotBlank(message = "Название способа оплаты обязательно")
    @Size(max = 100, message = "Название не должно превышать 100 символов")
    private String name;

    @Size(max = 1000, message = "Реквизиты не должны превышать 1000 символов")
    private String requisites;

    private Boolean active;

    private Integer sortOrder;
}
