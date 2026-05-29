package com.oshmarket.dto.tenant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BankLinkDto {
    private Long id;
    private String bankName;
    private String bankCode;
    private String url;
}
