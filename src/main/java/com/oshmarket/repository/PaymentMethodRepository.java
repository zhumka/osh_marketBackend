package com.oshmarket.repository;

import com.oshmarket.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findAllByActiveTrueOrderBySortOrderAscNameAsc();

    List<PaymentMethod> findAllByOrderBySortOrderAscNameAsc();
}
