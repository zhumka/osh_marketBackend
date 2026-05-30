package com.oshmarket.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "ИНН обязателен")
    @Pattern(regexp = "\\d{14}", message = "ИНН должен содержать ровно 14 цифр")
    private String inn;

    @NotBlank(message = "Пароль обязателен")
    private String password;
}
