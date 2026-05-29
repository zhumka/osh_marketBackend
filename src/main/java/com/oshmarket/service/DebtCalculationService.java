package com.oshmarket.service;

import com.oshmarket.entity.RentContract;
import com.oshmarket.repository.RentContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtCalculationService {

    private final RentContractRepository rentContractRepository;

    // Runs at midnight on the 1st of every month
    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void chargeMonthlyRent() {
        LocalDate today = LocalDate.now();
        List<RentContract> activeContracts = rentContractRepository.findAllByActiveTrueOrderByCreatedAtDesc();

        for (RentContract contract : activeContracts) {
            if (!contract.getStartDate().isAfter(today)) {
                contract.setDebt(contract.getDebt().add(contract.getPlace().getMonthlyRent()));
                rentContractRepository.save(contract);
            }
        }
        log.info("Monthly rent charged for {} active contracts", activeContracts.size());
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
}
