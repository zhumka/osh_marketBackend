package com.oshmarket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    @Value("${app.sms.enabled}")
    private boolean enabled;

    public void sendPasswordResetCode(String phone, String code) {
        if (!enabled) {
            log.info("[SMS STUB] To: {}, Code: {}", phone, code);
            return;
        }
        // Integrate actual SMS provider (e.g. Beeline KG, O! SMS, MegaCom)
        log.warn("SMS provider not configured. Phone: {}", phone);
    }

    public void sendTenantCredentials(String phone, String inn, String tempPassword) {
        if (!enabled) {
            log.info("[SMS STUB] To: {}, INN: {}, Password: {}", phone, inn, tempPassword);
            return;
        }
        log.warn("SMS provider not configured. Phone: {}", phone);
    }
}
