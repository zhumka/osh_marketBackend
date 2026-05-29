package com.oshmarket.repository;

import com.oshmarket.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    long countByTenantIdAndReadFalse(Long tenantId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.tenant.id = :tenantId AND n.read = false")
    void markAllAsReadByTenantId(@Param("tenantId") Long tenantId);
}
