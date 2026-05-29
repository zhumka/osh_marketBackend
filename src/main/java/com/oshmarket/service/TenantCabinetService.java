package com.oshmarket.service;

import com.oshmarket.dto.FileContent;
import com.oshmarket.dto.tenant.*;
import com.oshmarket.entity.Payment;
import com.oshmarket.entity.PaymentMethod;
import com.oshmarket.entity.PaymentStatus;
import com.oshmarket.entity.RentContract;
import com.oshmarket.entity.Tenant;
import com.oshmarket.exception.ApiException;
import com.oshmarket.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantCabinetService {

    private final TenantRepository tenantRepository;
    private final RentContractRepository contractRepository;
    private final PaymentRepository paymentRepository;
    private final BankLinkRepository bankLinkRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final NotificationService notificationService;
    private final DebtCalculationService debtCalculationService;
    private final StorageService storageService;

    public TenantProfileDto getProfile(Long userId) {
        Tenant tenant = getTenantByUserId(userId);
        Optional<RentContract> contractOpt = contractRepository.findByTenantIdAndActiveTrue(tenant.getId());

        if (contractOpt.isEmpty()) {
            return TenantProfileDto.builder()
                    .inn(tenant.getInn())
                    .fullName(tenant.getFullName())
                    .isActive(false)
                    .build();
        }

        RentContract contract = contractOpt.get();
        Optional<Payment> lastPayment = paymentRepository
                .findFirstByContractIdAndStatusOrderByPaymentDateDesc(contract.getId(), PaymentStatus.APPROVED);

        return TenantProfileDto.builder()
                .inn(tenant.getInn())
                .fullName(tenant.getFullName())
                .placeNumber(contract.getPlace().getPlaceNumber())
                .aisle(contract.getPlace().getAisle())
                .department(contract.getPlace().getDepartment())
                .monthlyRent(contract.getPlace().getMonthlyRent())
                .debt(contract.getDebt())
                .isActive(true)
                .lastPaymentDate(lastPayment.map(Payment::getPaymentDate).orElse(null))
                .startDate(contract.getStartDate())
                .build();
    }

    public NextPaymentDto getNextPayment(Long userId) {
        Tenant tenant = getTenantByUserId(userId);
        RentContract contract = contractRepository.findByTenantIdAndActiveTrue(tenant.getId())
                .orElseThrow(() -> ApiException.notFound("Активный договор не найден"));

        BigDecimal monthlyRent = contract.getPlace().getMonthlyRent();
        BigDecimal debt = contract.getDebt();
        BigDecimal totalDue = monthlyRent.add(debt.max(BigDecimal.ZERO));

        return NextPaymentDto.builder()
                .dueDate(debtCalculationService.calculateNextDueDate(contract))
                .monthlyRent(monthlyRent)
                .debt(debt)
                .totalDue(totalDue)
                .build();
    }

    public List<PaymentHistoryItemDto> getPaymentHistory(Long userId) {
        Tenant tenant = getTenantByUserId(userId);
        return paymentRepository.findAllByTenantId(tenant.getId())
                .stream()
                .map(p -> PaymentHistoryItemDto.builder()
                        .id(p.getId())
                        .amount(p.getAmount())
                        .paymentDate(p.getPaymentDate())
                        .monthName(getMonthName(p.getPaymentDate().getMonth()))
                        .description(p.getDescription())
                        .status(p.getStatus().name())
                        .paymentMethodName(p.getPaymentMethod() != null ? p.getPaymentMethod().getName() : null)
                        .rejectReason(p.getRejectReason())
                        .build())
                .toList();
    }

    public List<NotificationDto> getNotifications(Long userId) {
        Tenant tenant = getTenantByUserId(userId);
        return notificationService.getForTenant(tenant.getId());
    }

    public long getUnreadNotificationCount(Long userId) {
        Tenant tenant = getTenantByUserId(userId);
        return notificationService.countUnread(tenant.getId());
    }

    @Transactional
    public void markAllNotificationsRead(Long userId) {
        Tenant tenant = getTenantByUserId(userId);
        notificationService.markAllAsRead(tenant.getId());
    }

    public List<BankLinkDto> getBankLinks() {
        return bankLinkRepository.findAllByActiveTrueOrderByBankName()
                .stream()
                .map(b -> BankLinkDto.builder()
                        .id(b.getId())
                        .bankName(b.getBankName())
                        .bankCode(b.getBankCode())
                        .url(b.getUrl())
                        .build())
                .toList();
    }

    public List<PaymentMethodDto> getPaymentMethods() {
        return paymentMethodRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(m -> PaymentMethodDto.builder()
                        .id(m.getId())
                        .name(m.getName())
                        .requisites(m.getRequisites())
                        .hasQr(m.getQrObjectKey() != null)
                        .qrUrl(m.getQrObjectKey() != null
                                ? "/api/tenant/payment-methods/" + m.getId() + "/qr" : null)
                        .build())
                .toList();
    }

    public FileContent getQr(Long methodId) {
        PaymentMethod method = paymentMethodRepository.findById(methodId)
                .orElseThrow(() -> ApiException.notFound("Способ оплаты не найден"));
        if (method.getQrObjectKey() == null) {
            throw ApiException.notFound("QR-код для этого способа оплаты не загружен");
        }
        return storageService.get(method.getQrObjectKey(), method.getQrContentType());
    }

    /**
     * Арендатор отправляет платёж на проверку: фиксируем сумму, способ оплаты и чек.
     * Долг НЕ списывается — это произойдёт только после подтверждения администратором.
     */
    @Transactional
    public Long submitPayment(Long userId, Long methodId, BigDecimal amount, MultipartFile receipt) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Сумма платежа должна быть больше 0");
        }
        if (receipt == null || receipt.isEmpty()) {
            throw ApiException.badRequest("Прикрепите чек об оплате");
        }

        Tenant tenant = getTenantByUserId(userId);
        RentContract contract = contractRepository.findByTenantIdAndActiveTrue(tenant.getId())
                .orElseThrow(() -> ApiException.notFound("Активный договор не найден"));
        PaymentMethod method = paymentMethodRepository.findById(methodId)
                .orElseThrow(() -> ApiException.notFound("Способ оплаты не найден"));
        if (!method.isActive()) {
            throw ApiException.badRequest("Этот способ оплаты недоступен");
        }

        String receiptKey = storageService.upload("receipts", receipt);

        Payment payment = new Payment();
        payment.setContract(contract);
        payment.setAmount(amount);
        payment.setPaymentDate(LocalDate.now());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(method);
        payment.setReceiptObjectKey(receiptKey);
        payment.setReceiptContentType(receipt.getContentType());
        payment.setSubmittedAt(LocalDateTime.now());
        payment.setDescription("Онлайн-оплата (" + method.getName() + ")");
        paymentRepository.save(payment);

        notificationService.createPaymentPending(tenant, amount);
        return payment.getId();
    }

    private Tenant getTenantByUserId(Long userId) {
        return tenantRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> ApiException.notFound("Арендатор не найден"));
    }

    private String getMonthName(Month month) {
        return month.getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
    }
}
