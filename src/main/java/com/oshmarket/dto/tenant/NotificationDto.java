package com.oshmarket.dto.tenant;

import com.oshmarket.entity.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationDto {
    private Long id;
    private NotificationType type;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
}
