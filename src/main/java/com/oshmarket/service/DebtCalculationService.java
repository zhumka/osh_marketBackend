package com.oshmarket.service;

import com.oshmarket.entity.PaymentStatus;
import com.oshmarket.entity.RentContract;
import com.oshmarket.repository.PaymentRepository;
import com.oshmarket.repository.RentContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtCalculationService {

    private static final int PENALTY_GRACE_BUSINESS_DAYS = 3;
    private static final BigDecimal LATE_PAYMENT_PENALTY_RATE = new BigDecimal("0.05");

    private final RentContractRepository rentContractRepository;
    private final NotificationService notificationService;
    private final PaymentRepository paymentRepository;

    // Runs at midnight on the 1st of every month
    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void chargeMonthlyRent() {
        LocalDate today = LocalDate.now();
        List<RentContract> activeContracts = rentContractRepository.findAllByActiveTrueOrderByCreatedAtDesc();

        for (RentContract contract : activeContracts) {
            if (!contract.getStartDate().isAfter(today)) {
                contract.setDebt(safeAmount(contract.getDebt()).add(contract.getPlace().getMonthlyRent()));
                rentContractRepository.save(contract);
            }
        }
        log.info("Monthly rent charged for {} active contracts", activeContracts.size());
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void chargeLatePaymentPenalties() {
        chargeLatePaymentPenalties(LocalDate.now());
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void chargeLatePaymentPenaltiesOnStartup() {
        chargeLatePaymentPenalties(LocalDate.now());
    }

    void chargeLatePaymentPenalties(LocalDate today) {
        List<RentContract> activeContracts = rentContractRepository.findAllByActiveTrueOrderByCreatedAtDesc();
        int charged = 0;

        for (RentContract contract : activeContracts) {
            charged += applyLatePaymentPenalties(contract, today);
        }

        log.info("Late payment penalties charged for {} overdue periods", charged);
    }

    public LocalDate calculateNextDueDate(RentContract contract) {
        LocalDate startDate = contract.getStartDate();
        LocalDate today = LocalDate.now();

        LocalDate nextDue = startDate.withDayOfMonth(
                Math.min(startDate.getDayOfMonth(), today.lengthOfMonth()));
        nextDue = nextDue.withMonth(today.getMonthValue()).withYear(today.getYear());

        if (!nextDue.isAfter(today)) {
            LocalDate nextMonth = today.plusMonths(1);
            int dayOfMonth = Math.min(startDate.getDayOfMonth(), nextMonth.lengthOfMonth());
            nextDue = nextMonth.withDayOfMonth(dayOfMonth);
        }
        return nextDue;
    }

    private int applyLatePaymentPenalties(RentContract contract, LocalDate today) {
        BigDecimal debt = safeAmount(contract.getDebt());
        if (debt.compareTo(BigDecimal.ZERO) < 0) {
            return 0;
        }

        int maxPenaltiesToCharge = calculateMaxPenaltiesToCharge(contract, today, debt);
        if (maxPenaltiesToCharge <= 0) {
            return 0;
        }

        List<LocalDate> eligibleDueDates = findEligibleDueDates(contract, today);
        if (eligibleDueDates.isEmpty()) {
            return 0;
        }

        List<LocalDate> dueDatesToCharge = eligibleDueDates
                .stream()
                .skip(Math.max(0, eligibleDueDates.size() - maxPenaltiesToCharge))
                .toList();

        int charged = 0;
        for (LocalDate dueDate : dueDatesToCharge) {
            if (debt.compareTo(BigDecimal.ZERO) == 0 && hasApprovedPaymentForPeriod(contract, dueDate)) {
                continue;
            }

            BigDecimal penaltyAmount = calculatePenaltyAmount(contract);
            if (penaltyAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            contract.setPenaltyDebt(safeAmount(contract.getPenaltyDebt()).add(penaltyAmount));
            contract.setLastPenaltyDueDate(dueDate);
            rentContractRepository.save(contract);
            notificationService.createPaymentPenalty(contract.getTenant(), penaltyAmount, dueDate);
            charged++;
        }

        return charged;
    }

    private int calculateMaxPenaltiesToCharge(RentContract contract, LocalDate today, BigDecimal debt) {
        BigDecimal monthlyRent = contract.getPlace().getMonthlyRent();
        if (monthlyRent == null || monthlyRent.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        if (debt.compareTo(BigDecimal.ZERO) > 0) {
            return debt.divide(monthlyRent, 0, RoundingMode.CEILING).intValue();
        }

        LocalDate dueDate = calculateDueDateForMonth(contract, today);
        LocalDate graceEndsAt = addBusinessDays(dueDate, PENALTY_GRACE_BUSINESS_DAYS);
        return !dueDate.isBefore(contract.getStartDate()) && today.isAfter(graceEndsAt) ? 1 : 0;
    }

    private List<LocalDate> findEligibleDueDates(RentContract contract, LocalDate today) {
        LocalDate firstDueDate = contract.getStartDate();
        LocalDate lastDueDate = calculateDueDateForMonth(contract, today);
        if (lastDueDate.isAfter(today)) {
            lastDueDate = calculateDueDateForMonth(contract, today.minusMonths(1));
        }

        List<LocalDate> dueDates = new ArrayList<>();
        long monthsBetween = ChronoUnit.MONTHS.between(
                firstDueDate.withDayOfMonth(1),
                lastDueDate.withDayOfMonth(1));

        for (long i = 0; i <= monthsBetween; i++) {
            LocalDate month = firstDueDate.plusMonths(i);
            LocalDate dueDate = month.withDayOfMonth(
                    Math.min(firstDueDate.getDayOfMonth(), month.lengthOfMonth()));

            if (dueDate.isBefore(contract.getStartDate())) {
                continue;
            }
            if (contract.getLastPenaltyDueDate() != null
                    && !dueDate.isAfter(contract.getLastPenaltyDueDate())) {
                continue;
            }
            if (today.isAfter(addBusinessDays(dueDate, PENALTY_GRACE_BUSINESS_DAYS))) {
                dueDates.add(dueDate);
            }
        }

        return dueDates;
    }

    private LocalDate calculateDueDateForMonth(RentContract contract, LocalDate monthDate) {
        int dayOfMonth = Math.min(contract.getStartDate().getDayOfMonth(), monthDate.lengthOfMonth());
        return monthDate.withDayOfMonth(dayOfMonth);
    }

    private boolean hasApprovedPaymentForPeriod(RentContract contract, LocalDate dueDate) {
        LocalDate previousDueDate = calculatePreviousDueDate(contract, dueDate);
        return paymentRepository.existsByContractIdAndStatusAndPaymentDateAfter(
                contract.getId(), PaymentStatus.APPROVED, previousDueDate);
    }

    private LocalDate calculatePreviousDueDate(RentContract contract, LocalDate dueDate) {
        LocalDate previousMonth = dueDate.minusMonths(1);
        int dayOfMonth = Math.min(contract.getStartDate().getDayOfMonth(), previousMonth.lengthOfMonth());
        return previousMonth.withDayOfMonth(dayOfMonth);
    }

    private LocalDate addBusinessDays(LocalDate date, int businessDays) {
        LocalDate result = date;
        int added = 0;

        while (added < businessDays) {
            result = result.plusDays(1);
            if (result.getDayOfWeek().getValue() < 6) {
                added++;
            }
        }

        return result;
    }

    private BigDecimal calculatePenaltyAmount(RentContract contract) {
        return contract.getPlace().getMonthlyRent()
                .multiply(LATE_PAYMENT_PENALTY_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
