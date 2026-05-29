package com.oshmarket.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "ИНН обязателен")
    private String inn;

    @NotBlank(message = "Пароль обязателен")
    private String password;
}
