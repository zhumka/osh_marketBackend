package com.oshmarket.service;

import com.oshmarket.dto.FileContent;
import com.oshmarket.dto.admin.*;
import com.oshmarket.dto.tenant.PaymentHistoryItemDto;
import com.oshmarket.entity.*;
import com.oshmarket.exception.ApiException;
import com.oshmarket.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final RentContractRepository contractRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final NotificationService notificationService;
    private final DebtCalculationService debtCalculationService;
    private final EmailService emailService;
    private final StorageService storageService;
    private final PasswordEncoder passwordEncoder;

    public DashboardDto getDashboard() {
        List<TenantListItemDto> recentTenants = tenantRepository
                .findAllByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .limit(20)
                .map(this::toListItem)
                .collect(Collectors.toList());

        return DashboardDto.builder()
                .totalTenants(tenantRepository.countActive())
                .occupiedPlaces(placeRepository.countByOccupiedTrueAndDeletedFalse())
                .freePlaces(placeRepository.countByOccupiedFalseAndDeletedFalse())
                .debtors(contractRepository.countDebtors())
                .totalDebt(contractRepository.sumTotalDebt())
                .recentTenants(recentTenants)
                .build();
    }

    public List<TenantListItemDto> getAllTenants() {
        return tenantRepository.findAllByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
    }

    public TenantDetailDto getTenantDetail(Long tenantId) {
        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> ApiException.notFound("Арендатор не найден"));

        Optional<RentContract> contractOpt = contractRepository.findByTenantIdAndActiveTrue(tenantId);
        Optional<Payment> lastPayment = paymentRepository.findFirstByContractIdAndStatusOrderByPaymentDateDesc(
                contractOpt.map(RentContract::getId).orElse(-1L), PaymentStatus.APPROVED);

        List<PaymentHistoryItemDto> history = paymentRepository.findAllByTenantId(tenantId)
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
                .collect(Collectors.toList());

        TenantDetailDto.TenantDetailDtoBuilder builder = TenantDetailDto.builder()
                .id(tenant.getId())
                .inn(tenant.getInn())
                .fullName(tenant.getFullName())
                .phone(tenant.getPhone())
                .email(tenant.getEmail())
                .passportSeries(tenant.getPassportSeries())
                .passportNumber(tenant.getPassportNumber())
                .passportIssuedDate(tenant.getPassportIssuedDate())
                .passportIssuedBy(tenant.getPassportIssuedBy())
                .hasActiveContract(contractOpt.isPresent())
                .lastPaymentDate(lastPayment.map(p -> p.getPaymentDate()).orElse(null))
                .paymentHistory(history);

        contractOpt.ifPresent(c -> builder
                .placeNumber(c.getPlace().getPlaceNumber())
                .aisle(c.getPlace().getAisle())
                .department(c.getPlace().getDepartment())
                .monthlyRent(c.getPlace().getMonthlyRent())
                .startDate(c.getStartDate())
                .debt(c.getDebt()));

        return builder.build();
    }

    @Transactional
    public TenantDetailDto createTenantWithPlace(CreateTenantRequest req) {
        if (tenantRepository.existsByInnAndDeletedFalse(req.getInn())) {
            throw ApiException.conflict("Пользователь с таким ИНН уже зарегистрирован");
        }

        Place place = placeRepository.findByIdAndDeletedFalse(req.getPlaceId())
                .orElseThrow(() -> ApiException.notFound("Место не найдено"));
        if (place.isOccupied()) {
            throw ApiException.conflict("Выбранное место уже занято");
        }

        String tempPassword = generateTempPassword();

        User user = new User();
        user.setInn(req.getInn());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setRole(UserRole.TENANT);
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        userRepository.save(user);

        Tenant tenant = new Tenant();
        tenant.setUser(user);
        tenant.setInn(req.getInn());
        tenant.setFullName(req.getFullName());
        tenant.setPhone(req.getPhone());
        tenant.setEmail(req.getEmail());
        tenant.setPassportSeries(req.getPassportSeries());
        tenant.setPassportNumber(req.getPassportNumber());
        tenant.setPassportIssuedDate(req.getPassportIssuedDate());
        tenant.setPassportIssuedBy(req.getPassportIssuedBy());
        tenantRepository.save(tenant);

        place.setOccupied(true);
        placeRepository.save(place);

        RentContract contract = new RentContract();
        contract.setTenant(tenant);
        contract.setPlace(place);
        contract.setStartDate(req.getStartDate());
        contractRepository.save(contract);

        sendCredentials(req.getEmail(), req.getInn(), tempPassword);
        notificationService.createSystemNotification(tenant,
                "Добро пожаловать! Ваше место " + place.getPlaceNumber() + " забронировано.");

        log.info("Tenant created: INN={}, Place={}", req.getInn(), place.getPlaceNumber());
        return getTenantDetail(tenant.getId());
    }

    @Transactional
    public TenantDetailDto updateTenant(Long tenantId, UpdateTenantRequest req) {
        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> ApiException.notFound("Арендатор не найден"));

        if (req.getFullName() != null) tenant.setFullName(req.getFullName());
        if (req.getPhone() != null) tenant.setPhone(req.getPhone());
        if (req.getEmail() != null) tenant.setEmail(req.getEmail());
        if (req.getPassportSeries() != null) tenant.setPassportSeries(req.getPassportSeries());
        if (req.getPassportNumber() != null) tenant.setPassportNumber(req.getPassportNumber());
        if (req.getPassportIssuedDate() != null) tenant.setPassportIssuedDate(req.getPassportIssuedDate());
        if (req.getPassportIssuedBy() != null) tenant.setPassportIssuedBy(req.getPassportIssuedBy());
        tenantRepository.save(tenant);

        if (req.getMonthlyRent() != null) {
            contractRepository.findByTenantIdAndActiveTrue(tenantId)
                    .ifPresent(c -> {
                        c.getPlace().setMonthlyRent(req.getMonthlyRent());
                        placeRepository.save(c.getPlace());
                    });
        }

        return getTenantDetail(tenantId);
    }

    @Transactional
    public void deleteTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> ApiException.notFound("Арендатор не найден"));

        contractRepository.findByTenantIdAndActiveTrue(tenantId).ifPresent(c -> {
            c.setActive(false);
            c.setEndDate(LocalDate.now());
            contractRepository.save(c);
            c.getPlace().setOccupied(false);
            placeRepository.save(c.getPlace());
        });

        tenant.setDeleted(true);
        tenantRepository.save(tenant);

        User user = tenant.getUser();
        user.setDeleted(true);
        userRepository.save(user);

        log.info("Tenant soft-deleted: id={}", tenantId);
    }

    @Transactional
    public void recordPayment(Long tenantId, RecordPaymentRequest req) {
        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> ApiException.notFound("Арендатор не найден"));
        RentContract contract = contractRepository.findByTenantIdAndActiveTrue(tenantId)
                .orElseThrow(() -> ApiException.notFound("Активный договор не найден"));

        LocalDate paymentDate = req.getPaymentDate() != null ? req.getPaymentDate() : LocalDate.now();

        Payment payment = new Payment();
        payment.setContract(contract);
        payment.setAmount(req.getAmount());
        payment.setPaymentDate(paymentDate);
        payment.setDescription(req.getDescription());
        // Ручной (наличный) платёж админа считается сразу подтверждённым.
        payment.setStatus(PaymentStatus.APPROVED);
        paymentRepository.save(payment);

        contract.setDebt(contract.getDebt().subtract(req.getAmount()));
        contractRepository.save(contract);

        notificationService.createPaymentSuccess(tenant, req.getAmount(), paymentDate);
    }

    // ===== Модерация онлайн-платежей =====

    public List<PendingPaymentDto> getPendingPayments() {
        return paymentRepository.findAllByStatusOrderBySubmittedAtAsc(PaymentStatus.PENDING)
                .stream()
                .map(p -> {
                    RentContract c = p.getContract();
                    return PendingPaymentDto.builder()
                            .id(p.getId())
                            .tenantId(c.getTenant().getId())
                            .inn(c.getTenant().getInn())
                            .fullName(c.getTenant().getFullName())
                            .placeNumber(c.getPlace().getPlaceNumber())
                            .amount(p.getAmount())
                            .paymentMethodName(p.getPaymentMethod() != null ? p.getPaymentMethod().getName() : null)
                            .submittedAt(p.getSubmittedAt())
                            .hasReceipt(p.getReceiptObjectKey() != null)
                            .receiptUrl(p.getReceiptObjectKey() != null
                                    ? "/api/admin/payments/" + p.getId() + "/receipt" : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public FileContent getReceipt(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Платёж не найден"));
        if (payment.getReceiptObjectKey() == null) {
            throw ApiException.notFound("К платежу не прикреплён чек");
        }
        return storageService.get(payment.getReceiptObjectKey(), payment.getReceiptContentType());
    }

    @Transactional
    public void approvePayment(Long paymentId, Long adminUserId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Платёж не найден"));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.badRequest("Платёж уже обработан");
        }

        payment.setStatus(PaymentStatus.APPROVED);
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedBy(adminUserId);
        paymentRepository.save(payment);

        RentContract contract = payment.getContract();
        contract.setDebt(contract.getDebt().subtract(payment.getAmount()));
        contractRepository.save(contract);

        notificationService.createPaymentSuccess(contract.getTenant(), payment.getAmount(), payment.getPaymentDate());
        log.info("Payment approved: id={}, amount={}, by admin={}", paymentId, payment.getAmount(), adminUserId);
    }

    @Transactional
    public void rejectPayment(Long paymentId, Long adminUserId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Платёж не найден"));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.badRequest("Платёж уже обработан");
        }

        payment.setStatus(PaymentStatus.REJECTED);
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedBy(adminUserId);
        payment.setRejectReason(reason);
        paymentRepository.save(payment);

        notificationService.createPaymentRejected(payment.getContract().getTenant(), payment.getAmount(), reason);
        log.info("Payment rejected: id={}, by admin={}, reason={}", paymentId, adminUserId, reason);
    }

    // ===== Управление способами оплаты =====

    public List<PaymentMethodAdminDto> getPaymentMethods() {
        return paymentMethodRepository.findAllByOrderBySortOrderAscNameAsc()
                .stream()
                .map(this::toPaymentMethodAdminDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentMethodAdminDto createPaymentMethod(SavePaymentMethodRequest req) {
        PaymentMethod method = new PaymentMethod();
        applyPaymentMethod(method, req);
        paymentMethodRepository.save(method);
        return toPaymentMethodAdminDto(method);
    }

    @Transactional
    public PaymentMethodAdminDto updatePaymentMethod(Long id, SavePaymentMethodRequest req) {
        PaymentMethod method = paymentMethodRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Способ оплаты не найден"));
        applyPaymentMethod(method, req);
        paymentMethodRepository.save(method);
        return toPaymentMethodAdminDto(method);
    }

    @Transactional
    public PaymentMethodAdminDto uploadQr(Long id, MultipartFile qr) {
        PaymentMethod method = paymentMethodRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Способ оплаты не найден"));

        String oldKey = method.getQrObjectKey();
        String key = storageService.upload("qr", qr);
        method.setQrObjectKey(key);
        method.setQrContentType(qr.getContentType());
        paymentMethodRepository.save(method);

        if (oldKey != null) {
            storageService.remove(oldKey);
        }
        return toPaymentMethodAdminDto(method);
    }

    private void applyPaymentMethod(PaymentMethod method, SavePaymentMethodRequest req) {
        method.setName(req.getName());
        method.setRequisites(req.getRequisites());
        if (req.getActive() != null) method.setActive(req.getActive());
        if (req.getSortOrder() != null) method.setSortOrder(req.getSortOrder());
    }

    private PaymentMethodAdminDto toPaymentMethodAdminDto(PaymentMethod m) {
        return PaymentMethodAdminDto.builder()
                .id(m.getId())
                .name(m.getName())
                .requisites(m.getRequisites())
                .active(m.isActive())
                .sortOrder(m.getSortOrder())
                .hasQr(m.getQrObjectKey() != null)
                .qrUrl(m.getQrObjectKey() != null ? "/api/tenant/payment-methods/" + m.getId() + "/qr" : null)
                .build();
    }

    public List<PlaceDto> getAllPlaces() {
        return placeRepository.findAllByDeletedFalseOrderByPlaceNumber()
                .stream()
                .map(this::toPlaceDto)
                .collect(Collectors.toList());
    }

    public List<PlaceDto> getFreePlaces() {
        return placeRepository.findAllByOccupiedFalseAndDeletedFalseOrderByPlaceNumber()
                .stream()
                .map(this::toPlaceDto)
                .collect(Collectors.toList());
    }

    public List<PlaceDto> getOccupiedPlaces() {
        return placeRepository.findAllByOccupiedTrueAndDeletedFalseOrderByPlaceNumber()
                .stream()
                .map(this::toPlaceDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PlaceDto createPlace(CreatePlaceRequest req) {
        if (placeRepository.existsByPlaceNumberAndDeletedFalse(req.getPlaceNumber())) {
            throw ApiException.conflict("Место с таким номером уже существует");
        }
        Place place = new Place();
        place.setPlaceNumber(req.getPlaceNumber());
        place.setAisle(req.getAisle());
        place.setDepartment(req.getDepartment());
        place.setMonthlyRent(req.getMonthlyRent());
        placeRepository.save(place);
        log.info("Place created: {}", place.getPlaceNumber());
        return toPlaceDto(place);
    }

    @Transactional
    public PlaceDto updatePlace(Long placeId, CreatePlaceRequest req) {
        Place place = placeRepository.findByIdAndDeletedFalse(placeId)
                .orElseThrow(() -> ApiException.notFound("Место не найдено"));

        if (!place.getPlaceNumber().equals(req.getPlaceNumber())
                && placeRepository.existsByPlaceNumberAndDeletedFalse(req.getPlaceNumber())) {
            throw ApiException.conflict("Место с таким номером уже существует");
        }

        place.setPlaceNumber(req.getPlaceNumber());
        place.setAisle(req.getAisle());
        place.setDepartment(req.getDepartment());
        place.setMonthlyRent(req.getMonthlyRent());
        placeRepository.save(place);
        return toPlaceDto(place);
    }

    @Transactional
    public void deletePlace(Long placeId) {
        Place place = placeRepository.findByIdAndDeletedFalse(placeId)
                .orElseThrow(() -> ApiException.notFound("Место не найдено"));
        if (place.isOccupied()) {
            throw ApiException.conflict("Нельзя удалить занятое место");
        }
        place.setDeleted(true);
        placeRepository.save(place);
        log.info("Place soft-deleted: id={}", placeId);
    }

    @Transactional
    public void releasePlace(Long placeId) {
        Place place = placeRepository.findByIdAndDeletedFalse(placeId)
                .orElseThrow(() -> ApiException.notFound("Место не найдено"));

        contractRepository.findAllByActiveTrueOrderByCreatedAtDesc().stream()
                .filter(c -> c.getPlace().getId().equals(placeId))
                .findFirst()
                .ifPresent(c -> {
                    c.setActive(false);
                    c.setEndDate(LocalDate.now());
                    contractRepository.save(c);
                });

        place.setOccupied(false);
        placeRepository.save(place);
    }

    public List<DebtorDto> getDebtors() {
        return contractRepository.findAllActiveWithDebtOrderByDebtDesc()
                .stream()
                .map(c -> DebtorDto.builder()
                        .tenantId(c.getTenant().getId())
                        .inn(c.getTenant().getInn())
                        .fullName(c.getTenant().getFullName())
                        .placeNumber(c.getPlace().getPlaceNumber())
                        .phone(c.getTenant().getPhone())
                        .location(c.getPlace().getAisle() + ", " + c.getPlace().getDepartment())
                        .monthlyRent(c.getPlace().getMonthlyRent())
                        .debt(c.getDebt())
                        .build())
                .collect(Collectors.toList());
    }

    public AnalyticsDto getAnalytics() {
        List<DebtorDto> topDebtors = contractRepository.findAllActiveWithDebtOrderByDebtDesc()
                .stream().limit(5)
                .map(c -> DebtorDto.builder()
                        .tenantId(c.getTenant().getId())
                        .placeNumber(c.getPlace().getPlaceNumber())
                        .inn(c.getTenant().getInn())
                        .fullName(c.getTenant().getFullName())
                        .debt(c.getDebt())
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalRevenue = placeRepository.findAllByOccupiedTrueAndDeletedFalseOrderByPlaceNumber()
                .stream()
                .map(Place::getMonthlyRent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AnalyticsDto.builder()
                .totalTenants(tenantRepository.countActive())
                .occupiedPlaces(placeRepository.countByOccupiedTrueAndDeletedFalse())
                .freePlaces(placeRepository.countByOccupiedFalseAndDeletedFalse())
                .debtors(contractRepository.countDebtors())
                .totalDebt(contractRepository.sumTotalDebt())
                .totalMonthlyRevenue(totalRevenue)
                .topDebtors(topDebtors)
                .build();
    }

    private TenantListItemDto toListItem(Tenant tenant) {
        Optional<RentContract> contractOpt = contractRepository.findByTenantIdAndActiveTrue(tenant.getId());
        BigDecimal debt = contractOpt.map(RentContract::getDebt).orElse(BigDecimal.ZERO);
        String placeNumber = contractOpt.map(c -> c.getPlace().getPlaceNumber()).orElse("-");
        String location = contractOpt.map(c -> c.getPlace().getAisle() + ", " + c.getPlace().getDepartment())
                .orElse("-");
        BigDecimal monthlyRent = contractOpt.map(c -> c.getPlace().getMonthlyRent()).orElse(BigDecimal.ZERO);

        return TenantListItemDto.builder()
                .id(tenant.getId())
                .inn(tenant.getInn())
                .fullName(tenant.getFullName())
                .placeNumber(placeNumber)
                .phone(tenant.getPhone())
                .location(location)
                .monthlyRent(monthlyRent)
                .debt(debt)
                .status(debt.compareTo(BigDecimal.ZERO) > 0 ? "Не оплачено" : "Оплачено")
                .build();
    }

    private PlaceDto toPlaceDto(Place p) {
        return PlaceDto.builder()
                .id(p.getId())
                .placeNumber(p.getPlaceNumber())
                .aisle(p.getAisle())
                .department(p.getDepartment())
                .monthlyRent(p.getMonthlyRent())
                .occupied(p.isOccupied())
                .build();
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    private void sendCredentials(String email, String inn, String tempPassword) {
        if (email != null) {
            emailService.sendTenantCredentials(email, inn, tempPassword);
        } else {
            log.warn("Не удалось отправить доступы: у арендатора нет email (INN={})", inn);
        }
    }

    private String getMonthName(Month month) {
        return month.getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
    }
}
