package com.oshmarket.repository;

import com.oshmarket.entity.RentContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RentContractRepository extends JpaRepository<RentContract, Long> {

    Optional<RentContract> findByTenantIdAndActiveTrue(Long tenantId);

    boolean existsByPlaceIdAndActiveTrue(Long placeId);

    List<RentContract> findAllByActiveTrueOrderByCreatedAtDesc();

    @Query("SELECT rc FROM RentContract rc " +
           "WHERE rc.active = true AND (rc.debt > 0 OR rc.penaltyDebt > 0) " +
           "ORDER BY (rc.debt + rc.penaltyDebt) DESC")
    List<RentContract> findAllActiveWithDebtOrderByDebtDesc();

    @Query("SELECT COALESCE(SUM(" +
           "(CASE WHEN rc.debt > 0 THEN rc.debt ELSE 0 END) + " +
           "(CASE WHEN rc.penaltyDebt > 0 THEN rc.penaltyDebt ELSE 0 END)), 0) " +
           "FROM RentContract rc WHERE rc.active = true AND (rc.debt > 0 OR rc.penaltyDebt > 0)")
    BigDecimal sumTotalDebt();

    @Query("SELECT COALESCE(SUM(rc.penaltyDebt), 0) FROM RentContract rc " +
           "WHERE rc.active = true AND rc.penaltyDebt > 0")
    BigDecimal sumTotalPenaltyDebt();

    @Query("SELECT COUNT(rc) FROM RentContract rc " +
           "WHERE rc.active = true AND (rc.debt > 0 OR rc.penaltyDebt > 0)")
    long countDebtors();

}
