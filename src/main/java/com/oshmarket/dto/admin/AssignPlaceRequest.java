package com.oshmarket.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AssignPlaceRequest {

    @NotNull(message = "ID места обязателен")
    private Long placeId;

    @NotNull(message = "Дата начала аренды обязательна")
    private LocalDate startDate;
}
