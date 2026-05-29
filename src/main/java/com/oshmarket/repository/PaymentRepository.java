package com.oshmarket.repository;

import com.oshmarket.entity.Payment;
import com.oshmarket.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByContractIdOrderByPaymentDateDesc(Long contractId);

    Optional<Payment> findFirstByContractIdOrderByPaymentDateDesc(Long contractId);

    /** Последний подтверждённый платёж — для отображения «дата последней оплаты». */
    Optional<Payment> findFirstByContractIdAndStatusOrderByPaymentDateDesc(Long contractId, PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.contract.tenant.id = :tenantId ORDER BY p.paymentDate DESC")
    List<Payment> findAllByTenantId(@Param("tenantId") Long tenantId);

    /** Платежи на модерации — для админ-панели (сначала самые старые). */
    List<Payment> findAllByStatusOrderBySubmittedAtAsc(PaymentStatus status);

    long countByStatus(PaymentStatus status);

    @Query("SELECT p FROM Payment p JOIN p.contract rc " +
           "WHERE rc.active = true AND p.status = com.oshmarket.entity.PaymentStatus.APPROVED " +
           "ORDER BY p.paymentDate DESC")
    List<Payment> findAllForReport();
}
