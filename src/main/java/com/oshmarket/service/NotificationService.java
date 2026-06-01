package com.oshmarket.service;

import com.oshmarket.dto.tenant.NotificationDto;
import com.oshmarket.entity.Notification;
import com.oshmarket.entity.NotificationType;
import com.oshmarket.entity.Tenant;
import com.oshmarket.exception.ApiException;
import com.oshmarket.repository.NotificationRepository;
import com.oshmarket.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TenantRepository tenantRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public List<NotificationDto> getForTenant(Long tenantId) {
        return notificationRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public long countUnread(Long tenantId) {
        return notificationRepository.countByTenantIdAndReadFalse(tenantId);
    }

    @Transactional
    public void markAllAsRead(Long tenantId) {
        tenantRepository.findByIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> ApiException.notFound("Арендатор не найден"));
        notificationRepository.markAllAsReadByTenantId(tenantId);
    }

    @Transactional
    public void createPaymentSuccess(Tenant tenant, BigDecimal amount, LocalDate paymentDate) {
        String monthName = paymentDate.getMonth()
                .getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, new Locale("ru"));
        String message = String.format("Ваш платёж за %s %d принят. Сумма: %s сом.",
                monthName, paymentDate.getYear(), formatAmount(amount));
        create(tenant, NotificationType.PAYMENT_SUCCESS, message);
    }

    @Transactional
    public void createPaymentPending(Tenant tenant, BigDecimal amount) {
        String message = String.format("Ваш платёж на сумму %s сом принят и находится на проверке у администратора.",
                formatAmount(amount));
        create(tenant, NotificationType.PAYMENT_PENDING, message);
    }

    @Transactional
    public void createPaymentRejected(Tenant tenant, BigDecimal amount, String reason) {
        String message = String.format("Ваш платёж на сумму %s сом отклонён. Причина: %s",
                formatAmount(amount), reason);
        create(tenant, NotificationType.PAYMENT_REJECTED, message);
    }

    @Transactional
    public void createPaymentPenalty(Tenant tenant, BigDecimal amount, LocalDate dueDate) {
        String message = String.format("За просрочку оплаты аренды до %s начислен штраф: %s сом.",
                dueDate.format(DATE_FMT), formatAmount(amount));
        create(tenant, NotificationType.PAYMENT_PENALTY, message);
    }

    @Transactional
    public void createPaymentReminder(Tenant tenant, BigDecimal amount, LocalDate dueDate) {
        String message = String.format("Напоминание: оплата аренды до %s. Сумма: %s сом.",
                dueDate.format(DATE_FMT), formatAmount(amount));
        create(tenant, NotificationType.PAYMENT_REMINDER, message);
    }

    @Transactional
    public void createSystemNotification(Tenant tenant, String message) {
        create(tenant, NotificationType.SYSTEM, message);
    }

    private void create(Tenant tenant, NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setTenant(tenant);
        notification.setType(type);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType())
                .message(n.getMessage())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private String formatAmount(BigDecimal amount) {
        return String.format("%,.0f", amount).replace(',', ' ');
    }
}
