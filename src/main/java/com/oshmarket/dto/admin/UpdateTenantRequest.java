package com.oshmarket.dto.admin;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateTenantRequest {
    @Pattern(regexp = "\\d{14}", message = "ИНН должен содержать ровно 14 цифр")
    private String inn;
    private String fullName;
    private String phone;
    private String email;
    private String passportSeries;
    private String passportNumber;
    private LocalDate passportIssuedDate;
    private String passportIssuedBy;
    private BigDecimal monthlyRent;
    private LocalDate plannedEndDate;
}
