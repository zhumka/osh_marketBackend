package com.oshmarket.repository;

import com.oshmarket.entity.RentContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RentContractRepository extends JpaRepository<RentContract, Long> {

    Optional<RentContract> findByTenantIdAndActiveTrue(Long tenantId);

    boolean existsByPlaceIdAndActiveTrue(Long placeId);

    List<RentContract> findAllByActiveTrueOrderByCreatedAtDesc();

    @Query("SELECT rc FROM RentContract rc WHERE rc.active = true AND rc.debt > 0 ORDER BY rc.debt DESC")
    List<RentContract> findAllActiveWithDebtOrderByDebtDesc();

    @Query("SELECT COALESCE(SUM(rc.debt), 0) FROM RentContract rc WHERE rc.active = true AND rc.debt > 0")
    BigDecimal sumTotalDebt();

    @Query("SELECT COUNT(rc) FROM RentContract rc WHERE rc.active = true AND rc.debt > 0")
    long countDebtors();

    @Query("SELECT rc FROM RentContract rc JOIN FETCH rc.tenant t JOIN FETCH rc.place p " +
           "WHERE rc.active = true AND rc.debt > 0 ORDER BY rc.debt DESC")
    List<RentContract> findTopDebtors(@Param("limit") int limit);
}
