package com.oshmarket.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChooseResetMethodRequest {

    @NotBlank
    private String resetToken;

    @NotBlank
    @Pattern(regexp = "email|phone", message = "Метод должен быть 'email' или 'phone'")
    private String method;
}
