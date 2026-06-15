package com.oshmarket.service;

import com.oshmarket.dto.tenant.PaymentHistoryItemDto;
import com.oshmarket.dto.tenant.TenantProfileDto;
import com.oshmarket.entity.Payment;
import com.oshmarket.entity.PaymentMethod;
import com.oshmarket.entity.PaymentStatus;
import com.oshmarket.entity.Place;
import com.oshmarket.entity.RentContract;
import com.oshmarket.entity.Tenant;
import com.oshmarket.repository.BankLinkRepository;
import com.oshmarket.repository.PaymentMethodRepository;
import com.oshmarket.repository.PaymentRepository;
import com.oshmarket.repository.RentContractRepository;
import com.oshmarket.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantCabinetServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private RentContractRepository contractRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BankLinkRepository bankLinkRepository;
    @Mock
    private PaymentMethodRepository paymentMethodRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DebtCalculationService debtCalculationService;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private TenantCabinetService tenantCabinetService;

    @Test
    void getProfileMarksZeroDebtWithoutApprovedPaymentAsUnpaid() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, BigDecimal.ZERO);

        when(tenantRepository.findByUserIdAndDeletedFalse(tenant.getUser().getId()))
                .thenReturn(Optional.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId()))
                .thenReturn(Optional.of(contract));
        when(paymentRepository.findFirstByContractIdAndStatusOrderByPaymentDateDescIdDesc(
                contract.getId(), PaymentStatus.APPROVED))
                .thenReturn(Optional.empty());

        TenantProfileDto profile = tenantCabinetService.getProfile(tenant.getUser().getId());

        assertThat(profile.getTotalDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(profile.getLastPaymentDate()).isNull();
        assertThat(profile.getStatus()).isEqualTo("Не оплачено");
    }

    @Test
    void getProfileReturnsLastApprovedPaymentDateAndPaidStatus() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, BigDecimal.ZERO);
        Payment payment = approvedPayment(contract, LocalDate.of(2026, 6, 15));

        when(tenantRepository.findByUserIdAndDeletedFalse(tenant.getUser().getId()))
                .thenReturn(Optional.of(tenant));
        when(contractRepository.findByTenantIdAndActiveTrue(tenant.getId()))
                .thenReturn(Optional.of(contract));
        when(paymentRepository.findFirstByContractIdAndStatusOrderByPaymentDateDescIdDesc(
                contract.getId(), PaymentStatus.APPROVED))
                .thenReturn(Optional.of(payment));

        TenantProfileDto profile = tenantCabinetService.getProfile(tenant.getUser().getId());

        assertThat(profile.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(profile.getStatus()).isEqualTo("Оплачено");
    }

    @Test
    void getPaymentHistoryReturnsTenantPayments() {
        Tenant tenant = tenant();
        RentContract contract = contract(tenant, BigDecimal.ZERO);
        Payment payment = approvedPayment(contract, LocalDate.of(2026, 6, 15));
        PaymentMethod method = new PaymentMethod();
        method.setName("Cash");
        payment.setPaymentMethod(method);

        when(tenantRepository.findByUserIdAndDeletedFalse(tenant.getUser().getId()))
                .thenReturn(Optional.of(tenant));
        when(paymentRepository.findAllByTenantId(tenant.getId()))
                .thenReturn(List.of(payment));

        List<PaymentHistoryItemDto> history = tenantCabinetService.getPaymentHistory(tenant.getUser().getId());

        assertThat(history).singleElement()
                .satisfies(item -> {
                    assertThat(item.getAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
                    assertThat(item.getPaymentDate()).isEqualTo(LocalDate.of(2026, 6, 15));
                    assertThat(item.getStatus()).isEqualTo(PaymentStatus.APPROVED.name());
                    assertThat(item.getPaymentMethodName()).isEqualTo("Cash");
                });
    }

    private static Tenant tenant() {
        com.oshmarket.entity.User user = new com.oshmarket.entity.User();
        user.setId(7L);

        Tenant tenant = new Tenant();
        tenant.setId(177L);
        tenant.setUser(user);
        tenant.setInn("12501200550404");
        tenant.setFullName("Кенешова Айтенира Кенешовна");
        return tenant;
    }

    private static RentContract contract(Tenant tenant, BigDecimal debt) {
        Place place = new Place();
        place.setId(6L);
        place.setPlaceNumber("A-12");
        place.setAisle("Проход 1");
        place.setDepartment("Овощи");
        place.setMonthlyRent(new BigDecimal("5000.00"));

        RentContract contract = new RentContract();
        contract.setId(18L);
        contract.setTenant(tenant);
        contract.setPlace(place);
        contract.setStartDate(LocalDate.of(2026, 6, 15));
        contract.setPlannedEndDate(LocalDate.of(2027, 6, 30));
        contract.setDebt(debt);
        contract.setPenaltyDebt(BigDecimal.ZERO);
        return contract;
    }

    private static Payment approvedPayment(RentContract contract, LocalDate paymentDate) {
        Payment payment = new Payment();
        payment.setId(23L);
        payment.setContract(contract);
        payment.setAmount(new BigDecimal("5000.00"));
        payment.setPaymentDate(paymentDate);
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setDescription("Manual payment");
        return payment;
    }
}
