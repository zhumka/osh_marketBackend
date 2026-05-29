package com.oshmarket.controller;

import com.oshmarket.dto.FileContent;
import com.oshmarket.dto.tenant.*;
import com.oshmarket.security.CustomUserDetails;
import com.oshmarket.service.TenantCabinetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantCabinetService tenantCabinetService;

    @GetMapping("/profile")
    public ResponseEntity<TenantProfileDto> getProfile(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(tenantCabinetService.getProfile(user.getId()));
    }

    @GetMapping("/payments/next")
    public ResponseEntity<NextPaymentDto> getNextPayment(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(tenantCabinetService.getNextPayment(user.getId()));
    }

    @GetMapping("/payments/history")
    public ResponseEntity<List<PaymentHistoryItemDto>> getPaymentHistory(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(tenantCabinetService.getPaymentHistory(user.getId()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(@AuthenticationPrincipal CustomUserDetails user) {
        List<NotificationDto> notifications = tenantCabinetService.getNotifications(user.getId());
        long unread = tenantCabinetService.getUnreadNotificationCount(user.getId());
        return ResponseEntity.ok(Map.of(
                "notifications", notifications,
                "unreadCount", unread
        ));
    }

    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Map<String, String>> markAllRead(@AuthenticationPrincipal CustomUserDetails user) {
        tenantCabinetService.markAllNotificationsRead(user.getId());
        return ResponseEntity.ok(Map.of("message", "Все уведомления прочитаны"));
    }

    @GetMapping("/bank-links")
    public ResponseEntity<List<BankLinkDto>> getBankLinks() {
        return ResponseEntity.ok(tenantCabinetService.getBankLinks());
    }

    // ===== Оплата по QR =====

    @GetMapping("/payment-methods")
    public ResponseEntity<List<PaymentMethodDto>> getPaymentMethods() {
        return ResponseEntity.ok(tenantCabinetService.getPaymentMethods());
    }

    @GetMapping("/payment-methods/{id}/qr")
    public ResponseEntity<byte[]> getQr(@PathVariable Long id) {
        FileContent qr = tenantCabinetService.getQr(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(qr.contentType()))
                .body(qr.bytes());
    }

    /**
     * Арендатор отправляет платёж на проверку: способ оплаты, сумма и чек.
     * Статус платежа становится PENDING (в обработке).
     */
    @PostMapping(value = "/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> submitPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam("methodId") Long methodId,
            @RequestParam("amount") BigDecimal amount,
            @RequestPart("receipt") MultipartFile receipt) {
        Long paymentId = tenantCabinetService.submitPayment(user.getId(), methodId, amount, receipt);
        return ResponseEntity.ok(Map.of(
                "paymentId", paymentId,
                "status", "PENDING",
                "message", "Платёж отправлен на проверку администратору"
        ));
    }
}
