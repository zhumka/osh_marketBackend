package com.oshmarket.entity;

public enum PaymentStatus {
    /** Платёж отправлен арендатором и ожидает проверки администратором. */
    PENDING,
    /** Платёж подтверждён администратором — долг списан. */
    APPROVED,
    /** Платёж отклонён администратором. */
    REJECTED
}
