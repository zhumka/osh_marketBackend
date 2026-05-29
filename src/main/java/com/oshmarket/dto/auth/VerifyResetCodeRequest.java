package com.oshmarket.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyResetCodeRequest {

    @NotBlank
    private String resetToken;

    @NotBlank
    private String code;
}
