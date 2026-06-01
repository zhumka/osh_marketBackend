package com.oshmarket.dto.admin;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateTenantRequest {

    @NotBlank(message = "ФИО обязательно")
    private String fullName;

    @NotBlank(message = "ИНН обязателен")
    @Pattern(regexp = "\\d{14}", message = "ИНН должен содержать ровно 14 цифр")
    private String inn;

    private String phone;
    private String email;
    private String passportSeries;
    private String passportNumber;
    private LocalDate passportIssuedDate;
    private String passportIssuedBy;

    @NotNull(message = "ID места обязателен")
    private Long placeId;

    @NotNull(message = "Дата начала аренды обязательна")
    private LocalDate startDate;

    @NotNull(message = "Дата окончания аренды обязательна")
    private LocalDate plannedEndDate;
}
