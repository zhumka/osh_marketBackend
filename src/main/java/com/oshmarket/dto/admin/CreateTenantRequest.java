package com.oshmarket.dto.admin;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateTenantRequest {

    @NotBlank(message = "ФИО обязательно")
    private String fullName;

    @NotBlank(message = "ИНН обязателен")
    private String inn;

    private String phone;
    private String email;
    private String passportSeries;
    private String passportNumber;
    private LocalDate passportIssuedDate;
    private String passportIssuedBy;

    @NotBlank(message = "Номер места обязателен")
    @Pattern(regexp = "[A-ZА-Я]-\\d+", message = "Формат номера места: буква-цифры (A-12, M-05)")
    private String placeNumber;

    @NotNull(message = "Стоимость аренды обязательна")
    @DecimalMin(value = "0.0", inclusive = false, message = "Стоимость аренды должна быть больше 0")
    private BigDecimal monthlyRent;

    private String aisle;
    private String department;

    @NotNull(message = "Дата начала аренды обязательна")
    private LocalDate startDate;
}
