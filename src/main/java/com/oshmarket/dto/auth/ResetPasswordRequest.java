package com.oshmarket.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank
    private String resetToken;

    @NotBlank
    @Size(min = 6, message = "Пароль должен содержать не менее 6 символов")
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
