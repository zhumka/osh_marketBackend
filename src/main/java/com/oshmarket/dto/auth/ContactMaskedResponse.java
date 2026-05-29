package com.oshmarket.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContactMaskedResponse {
    private String resetToken;
    private String maskedEmail;
    private String maskedPhone;
    private boolean hasEmail;
    private boolean hasPhone;
}
