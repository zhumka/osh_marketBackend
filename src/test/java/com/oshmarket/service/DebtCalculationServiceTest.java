package com.oshmarket.service;

import com.oshmarket.entity.Place;
import com.oshmarket.entity.PaymentStatus;
import com.oshmarket.entity.RentContract;
import com.oshmarket.entity.Tenant;
import com.oshmarket.repository.PaymentRepository;
import com.oshmarket.repository.RentContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtCalculationServiceTest {

    @Mock
    private RentContractRepository rentContractRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DebtCalculationService debtCalculationService;

    @Test
    void chargeLatePaymentPenaltiesAddsFivePercentOnceAfterThreeBusinessDays() {
        LocalDate dueDate = LocalDate.of(2026, 6, 1);
        RentContract contract = contract(dueDate, BigDecimal.ZERO);

        when(rentContractRepository.findAllByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(contract));
        when(paymentRepository.existsByContractIdAndStatusAndPaymentDateAfter(
                contract.getId(), PaymentStatus.APPROVED, LocalDate.of(2026, 5, 1)))
                .thenReturn(false);

        debtCalculationService.chargeLatePaymentPenalties(LocalDate.of(2026, 6, 5));

        assertThat(contract.getDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(contract.getPenaltyDebt()).isEqualByComparingTo(new BigDecimal("190.00"));
        assertThat(contract.getLastPenaltyDueDate()).isEqualTo(dueDate);
        verify(rentContractRepository).save(contract);
        verify(notificationService).createPaymentPenalty(
                contract.getTenant(), new BigDecimal("190.00"), dueDate);
    }

    @Test
    void chargeLatePaymentPenaltiesDoesNotDuplicateForSameDueDate() {
        LocalDate dueDate = LocalDate.of(2026, 6, 1);
        RentContract contract = contract(dueDate, new BigDecimal("3800.00"));
        contract.setLastPenaltyDueDate(dueDate);

        when(rentContractRepository.findAllByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(contract));

        debtCalculationService.chargeLatePaymentPenalties(LocalDate.of(2026, 6, 5));

        assertThat(contract.getDebt()).isEqualByComparingTo(new BigDecimal("3800.00"));
        assertThat(contract.getPenaltyDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(rentContractRepository, never()).save(any(RentContract.class));
        verify(notificationService, never()).createPaymentPenalty(any(), any(), any());
    }

    @Test
    void chargeLatePaymentPenaltiesCatchesUpOverdueRentPeriods() {
        RentContract contract = contract(LocalDate.of(2026, 4, 1), new BigDecimal("7600.00"));

        when(rentContractRepository.findAllByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(contract));

        debtCalculationService.chargeLatePaymentPenalties(LocalDate.of(2026, 6, 5));

        assertThat(contract.getDebt()).isEqualByComparingTo(new BigDecimal("7600.00"));
        assertThat(contract.getPenaltyDebt()).isEqualByComparingTo(new BigDecimal("380.00"));
        assertThat(contract.getLastPenaltyDueDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        verify(notificationService).createPaymentPenalty(
                contract.getTenant(), new BigDecimal("190.00"), LocalDate.of(2026, 5, 1));
        verify(notificationService).createPaymentPenalty(
                contract.getTenant(), new BigDecimal("190.00"), LocalDate.of(2026, 6, 1));
    }

    private static RentContract contract(LocalDate startDate, BigDecimal debt) {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setInn("12345678901234");
        tenant.setFullName("Тестовый Арендатор");

        Place place = new Place();
        place.setId(2L);
        place.setPlaceNumber("A-1");
        place.setMonthlyRent(new BigDecimal("3800.00"));

        RentContract contract = new RentContract();
        contract.setId(3L);
        contract.setTenant(tenant);
        contract.setPlace(place);
        contract.setStartDate(startDate);
        contract.setDebt(debt);
        return contract;
    }
}
