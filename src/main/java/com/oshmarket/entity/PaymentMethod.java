package com.oshmarket.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Способ оплаты, который видит арендатор. К каждому способу привязан QR-код,
 * хранящийся в объектном хранилище (MinIO); в БД лежит только ключ объекта.
 */
@Entity
@Table(name = "payment_methods")
@Getter
@Setter
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Реквизиты / инструкция для оплаты (что показать рядом с QR). */
    @Column(length = 1000)
    private String requisites;

    /** Ключ QR-объекта в MinIO. Null, пока админ не загрузил QR. */
    @Column(name = "qr_object_key", length = 500)
    private String qrObjectKey;

    @Column(name = "qr_content_type", length = 100)
    private String qrContentType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
