package com.oshmarket.repository;

import com.oshmarket.entity.BankLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankLinkRepository extends JpaRepository<BankLink, Long> {

    List<BankLink> findAllByActiveTrueOrderByBankName();
}
