package com.oshmarket.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "rent_contracts")
@Getter
@Setter
public class RentContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal debt = BigDecimal.ZERO;

    @Column(name = "penalty_debt", nullable = false, precision = 12, scale = 2)
    private BigDecimal penaltyDebt = BigDecimal.ZERO;

    @Column(name = "last_penalty_due_date")
    private LocalDate lastPenaltyDueDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
