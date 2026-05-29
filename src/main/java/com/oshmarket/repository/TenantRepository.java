package com.oshmarket.repository;

import com.oshmarket.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByUserIdAndDeletedFalse(Long userId);

    Optional<Tenant> findByIdAndDeletedFalse(Long id);

    List<Tenant> findAllByDeletedFalseOrderByCreatedAtDesc();

    @Query("SELECT t FROM Tenant t WHERE t.deleted = false AND " +
           "(LOWER(t.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(t.inn) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Tenant> searchByNameOrInn(@Param("search") String search, Pageable pageable);

    boolean existsByInnAndDeletedFalse(String inn);

    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.deleted = false")
    long countActive();
}
