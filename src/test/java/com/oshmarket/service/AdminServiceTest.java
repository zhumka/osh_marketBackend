package com.oshmarket.service;

import com.oshmarket.dto.admin.AssignPlaceRequest;
import com.oshmarket.dto.admin.CreateTenantRequest;
import com.oshmarket.dto.admin.RecordPaymentRequest;
import com.oshmarket.dto.admin.TenantDetailDto;
import com.oshmarket.dto.admin.TenantListItemDto;
import com.oshmarket.dto.admin.UpdateTenantRequest;
import com.oshmarket.entity.Payment;
import com.oshmarket.entity.PaymentStatus;
import com.oshmarket.entity.Place;
import com.oshmarket.entity.RentContract;
import com.oshmarket.entity.Tenant;
import com.oshmarket.entity.User;
import com.oshmarket.exception.ApiException;
import com.oshmarket.repository.PaymentMethodRepository;
import com.oshmarket.repository.PaymentRepository;
import com.oshmarket.repository.PlaceRepository;
import com.oshmarket.repository.RentContractRepository;
import com.oshmarket.repository.TenantRepository;
import com.oshmarket.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private RentContractRepository contractRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMethodRepository paymentMethodRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DebtCalculationService debtCalculationService;
    @Mock
    private EmailService emailService;
    @Mock
    private StorageService storageService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminService adminService;

    @Test
    void getAllTenantsMarksZeroDebtWithoutApprovedPaymentAsUnpaid() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, BigDecimal.ZERO);

        when(tenantRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId())).thenReturn(Optional.of(contract));
        when(paymentRepository.existsByContractIdAndStatus(contract.getId(), PaymentStatus.APPROVED))
                .thenReturn(false);

        List<TenantListItemDto> tenants = adminService.getAllTenants();

        assertThat(tenants).singleElement()
                .extracting(TenantListItemDto::getStatus)
                .isEqualTo("Не оплачено");
    }

    @Test
    void getAllTenantsMarksZeroDebtWithApprovedPaymentAsPaid() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, BigDecimal.ZERO);

        when(tenantRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId())).thenReturn(Optional.of(contract));
        when(paymentRepository.existsByContractIdAndStatus(contract.getId(), PaymentStatus.APPROVED))
                .thenReturn(true);

        List<TenantListItemDto> tenants = adminService.getAllTenants();

        assertThat(tenants).singleElement()
                .extracting(TenantListItemDto::getStatus)
                .isEqualTo("Оплачено");
    }

    @Test
    void getAllTenantsMarksPenaltyDebtAsUnpaidAndReturnsItSeparately() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, BigDecimal.ZERO);
        contract.setPenaltyDebt(new BigDecimal("190.00"));

        when(tenantRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId())).thenReturn(Optional.of(contract));
        when(paymentRepository.existsByContractIdAndStatus(contract.getId(), PaymentStatus.APPROVED))
                .thenReturn(true);

        List<TenantListItemDto> tenants = adminService.getAllTenants();

        assertThat(tenants).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getStatus()).isEqualTo("Не оплачено");
                    assertThat(dto.getDebt()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(dto.getPenaltyDebt()).isEqualByComparingTo(new BigDecimal("190.00"));
                    assertThat(dto.getTotalDebt()).isEqualByComparingTo(new BigDecimal("190.00"));
                });
    }

    @Test
    void getAllTenantsReturnsLastApprovedPaymentDate() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, BigDecimal.ZERO);
        Payment payment = approvedPayment(LocalDate.of(2026, 6, 14));

        when(tenantRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId())).thenReturn(Optional.of(contract));
        when(paymentRepository.findFirstByContractIdAndStatusOrderByPaymentDateDescIdDesc(
                contract.getId(), PaymentStatus.APPROVED))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.existsByContractIdAndStatus(contract.getId(), PaymentStatus.APPROVED))
                .thenReturn(true);

        List<TenantListItemDto> tenants = adminService.getAllTenants();

        assertThat(tenants).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 6, 14));
                    assertThat(dto.getStatus()).isEqualTo("Оплачено");
                });
    }

    @Test
    void getTenantDetailReturnsStatusAndLastApprovedPaymentDate() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, BigDecimal.ZERO);
        Payment payment = approvedPayment(LocalDate.of(2026, 6, 14));

        when(tenantRepository.findByIdAndDeletedFalse(tenant.getId())).thenReturn(Optional.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId())).thenReturn(Optional.of(contract));
        when(paymentRepository.findFirstByContractIdAndStatusOrderByPaymentDateDescIdDesc(
                contract.getId(), PaymentStatus.APPROVED))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findAllByTenantId(tenant.getId())).thenReturn(List.of(payment));
        when(paymentRepository.existsByContractIdAndStatus(contract.getId(), PaymentStatus.APPROVED))
                .thenReturn(true);

        TenantDetailDto detail = adminService.getTenantDetail(tenant.getId());

        assertThat(detail.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(detail.getStatus()).isEqualTo("Оплачено");
    }

    @Test
    void createTenantWithPlaceSendsPaymentReminderForFirstRent() {
        LocalDate startDate = LocalDate.now().plusDays(3);
        CreateTenantRequest request = createTenantRequest(startDate);
        Place place = place();
        AtomicReference<Tenant> savedTenant = new AtomicReference<>();
        AtomicReference<RentContract> savedContract = new AtomicReference<>();

        when(tenantRepository.existsByInnAndDeletedFalse(request.getInn())).thenReturn(false);
        when(placeRepository.findByIdAndDeletedFalse(request.getPlaceId())).thenReturn(Optional.of(place));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(1L);
            savedTenant.set(tenant);
            return tenant;
        });
        when(placeRepository.save(place)).thenReturn(place);
        when(contractRepository.save(any(RentContract.class))).thenAnswer(invocation -> {
            RentContract contract = invocation.getArgument(0);
            contract.setId(3L);
            savedContract.set(contract);
            return contract;
        });
        when(tenantRepository.findByIdAndDeletedFalse(1L))
                .thenAnswer(invocation -> Optional.of(savedTenant.get()));
        when(contractRepository.findByTenantIdAndActiveTrue(1L))
                .thenAnswer(invocation -> Optional.of(savedContract.get()));
        when(paymentRepository.findFirstByContractIdAndStatusOrderByPaymentDateDescIdDesc(3L, PaymentStatus.APPROVED))
                .thenReturn(Optional.empty());
        when(paymentRepository.findAllByTenantId(1L)).thenReturn(List.of());
        when(paymentRepository.existsByContractIdAndStatus(3L, PaymentStatus.APPROVED))
                .thenReturn(false);

        adminService.createTenantWithPlace(request);

        assertThat(savedContract.get().getPlannedEndDate()).isEqualTo(request.getPlannedEndDate());
        verify(notificationService).createSystemNotification(same(savedTenant.get()), anyString());
        verify(notificationService).createPaymentReminder(
                same(savedTenant.get()), eq(place.getMonthlyRent()), eq(startDate));
    }

    @Test
    void createTenantWithPlaceRejectsPlannedEndDateOnStartDate() {
        LocalDate startDate = LocalDate.of(2026, 6, 14);
        CreateTenantRequest request = createTenantRequest(startDate);
        request.setPlannedEndDate(startDate);

        assertThatThrownBy(() -> adminService.createTenantWithPlace(request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void assignPlaceToExistingTenantCreatesContractForTenantWithoutActiveRent() {
        LocalDate startDate = LocalDate.now().plusDays(5);
        AssignPlaceRequest request = assignPlaceRequest(startDate);
        Tenant tenant = tenant();
        Place place = place();
        AtomicReference<RentContract> savedContract = new AtomicReference<>();

        when(tenantRepository.findByIdAndDeletedFalse(tenant.getId())).thenReturn(Optional.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId()))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(savedContract.get()));
        when(placeRepository.findByIdAndDeletedFalse(request.getPlaceId())).thenReturn(Optional.of(place));
        when(contractRepository.existsByPlaceIdAndActiveTrue(place.getId())).thenReturn(false);
        when(placeRepository.save(place)).thenReturn(place);
        when(contractRepository.save(any(RentContract.class))).thenAnswer(invocation -> {
            RentContract contract = invocation.getArgument(0);
            contract.setId(3L);
            savedContract.set(contract);
            return contract;
        });
        when(paymentRepository.findFirstByContractIdAndStatusOrderByPaymentDateDescIdDesc(3L, PaymentStatus.APPROVED))
                .thenReturn(Optional.empty());
        when(paymentRepository.findAllByTenantId(tenant.getId())).thenReturn(List.of());
        when(paymentRepository.existsByContractIdAndStatus(3L, PaymentStatus.APPROVED))
                .thenReturn(false);

        adminService.assignPlaceToExistingTenant(tenant.getId(), request);

        assertThat(place.isOccupied()).isTrue();
        assertThat(savedContract.get().getTenant()).isSameAs(tenant);
        assertThat(savedContract.get().getPlace()).isSameAs(place);
        assertThat(savedContract.get().getStartDate()).isEqualTo(startDate);
        assertThat(savedContract.get().getPlannedEndDate()).isEqualTo(request.getPlannedEndDate());
        verify(notificationService).createSystemNotification(same(tenant), anyString());
        verify(notificationService).createPaymentReminder(
                same(tenant), eq(place.getMonthlyRent()), eq(startDate));
    }

    @Test
    void assignPlaceToExistingTenantRejectsPlannedEndDateOnStartDate() {
        LocalDate startDate = LocalDate.of(2026, 6, 14);
        AssignPlaceRequest request = assignPlaceRequest(startDate);
        request.setPlannedEndDate(startDate);

        assertThatThrownBy(() -> adminService.assignPlaceToExistingTenant(tenant().getId(), request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateTenantChangesInnOnTenantAndUser() {
        Tenant tenant = tenant();
        User user = new User();
        user.setInn(tenant.getInn());
        tenant.setUser(user);
        UpdateTenantRequest request = new UpdateTenantRequest();
        request.setInn("99999999999999");

        when(tenantRepository.findByIdAndDeletedFalse(tenant.getId())).thenReturn(Optional.of(tenant));
        when(tenantRepository.existsByInnAndDeletedFalse(request.getInn())).thenReturn(false);
        when(userRepository.existsByInnAndDeletedFalse(request.getInn())).thenReturn(false);
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId())).thenReturn(Optional.empty());
        when(paymentRepository.findAllByTenantId(tenant.getId())).thenReturn(List.of());

        adminService.updateTenant(tenant.getId(), request);

        assertThat(tenant.getInn()).isEqualTo(request.getInn());
        assertThat(user.getInn()).isEqualTo(request.getInn());
        verify(userRepository).save(same(user));
        verify(tenantRepository).save(same(tenant));
    }

    @Test
    void updateTenantRejectsPlannedEndDateOnStartDate() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, BigDecimal.ZERO);
        UpdateTenantRequest request = new UpdateTenantRequest();
        request.setPlannedEndDate(contract.getStartDate());

        when(tenantRepository.findByIdAndDeletedFalse(tenant.getId())).thenReturn(Optional.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId())).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> adminService.updateTenant(tenant.getId(), request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void recordPaymentClearsRentDebtBeforePenaltyDebt() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, new BigDecimal("1000.00"));
        contract.setPenaltyDebt(new BigDecimal("200.00"));
        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("1100.00"));
        request.setPaymentDate(LocalDate.of(2026, 6, 1));

        when(tenantRepository.findByIdAndDeletedFalse(tenant.getId())).thenReturn(Optional.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId())).thenReturn(Optional.of(contract));

        adminService.recordPayment(tenant.getId(), request);

        assertThat(contract.getDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(contract.getPenaltyDebt()).isEqualByComparingTo(new BigDecimal("100.00"));
        verify(contractRepository).save(same(contract));
    }

    private static Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setInn("12345678901234");
        tenant.setFullName("Тестовый Арендатор");
        tenant.setPhone("+996700000000");
        return tenant;
    }

    private static RentContract contract(Tenant tenant, BigDecimal debt) {
        Place place = new Place();
        place.setId(2L);
        place.setPlaceNumber("A-1");
        place.setAisle("Ряд A");
        place.setDepartment("Продукты");
        place.setMonthlyRent(new BigDecimal("3800"));

        RentContract contract = new RentContract();
        contract.setId(3L);
        contract.setTenant(tenant);
        contract.setPlace(place);
        contract.setStartDate(LocalDate.of(2026, 6, 1));
        contract.setDebt(debt);
        return contract;
    }

    private static Payment approvedPayment(LocalDate paymentDate) {
        Payment payment = new Payment();
        payment.setId(9L);
        payment.setAmount(new BigDecimal("3800.00"));
        payment.setPaymentDate(paymentDate);
        payment.setStatus(PaymentStatus.APPROVED);
        return payment;
    }

    private static CreateTenantRequest createTenantRequest(LocalDate startDate) {
        CreateTenantRequest request = new CreateTenantRequest();
        request.setFullName("Новый Арендатор");
        request.setInn("98765432101234");
        request.setPhone("+996700111222");
        request.setPlaceId(2L);
        request.setStartDate(startDate);
        request.setPlannedEndDate(startDate.plusMonths(1));
        return request;
    }

    private static AssignPlaceRequest assignPlaceRequest(LocalDate startDate) {
        AssignPlaceRequest request = new AssignPlaceRequest();
        request.setPlaceId(2L);
        request.setStartDate(startDate);
        request.setPlannedEndDate(startDate.plusMonths(1));
        return request;
    }

    private static Place place() {
        Place place = new Place();
        place.setId(2L);
        place.setPlaceNumber("A-1");
        place.setAisle("Ряд A");
        place.setDepartment("Продукты");
        place.setMonthlyRent(new BigDecimal("3800"));
        place.setOccupied(false);
        return place;
    }
}
