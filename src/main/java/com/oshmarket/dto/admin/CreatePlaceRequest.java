package com.oshmarket.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePlaceRequest {

    @NotBlank(message = "Номер места обязателен")
    @Pattern(regexp = "[A-ZА-Я]-\\d+", message = "Формат номера места: буква-цифры (A-12, M-05)")
    private String placeNumber;

    @NotNull(message = "Стоимость аренды обязательна")
    @DecimalMin(value = "0.0", inclusive = false, message = "Стоимость аренды должна быть больше 0")
    private BigDecimal monthlyRent;

    private String aisle;
    private String department;
}
