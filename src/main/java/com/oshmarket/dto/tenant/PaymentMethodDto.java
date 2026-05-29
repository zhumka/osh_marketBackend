package com.oshmarket.dto.tenant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentMethodDto {
    private Long id;
    private String name;
    private String requisites;
    /** Есть ли загруженный QR-код (если нет — оплата по этому способу пока недоступна). */
    private boolean hasQr;
    /** Относительный путь для получения QR-изображения. */
    private String qrUrl;
}
