package com.oshmarket.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BookPlaceRequest {

    @NotBlank(message = "ФИО арендатора обязательно")
    private String fullName;

    @NotBlank(message = "ИНН арендатора обязателен")
    @Pattern(regexp = "\\d{14}", message = "ИНН должен содержать ровно 14 цифр")
    private String inn;

    private String phone;
    private String email;
    private String passportSeries;
    private String passportNumber;
    private LocalDate passportIssuedDate;
    private String passportIssuedBy;

    @NotNull(message = "Дата начала аренды обязательна")
    private LocalDate startDate;
}
