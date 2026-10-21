package com.neram.pos.dto;

import com.neram.pos.entity.SystemNotification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryDTO {
    private long unreadCount;
    private long pendingLeavesCount;
    private long lowStockAlertsCount;
    private List<SystemNotification> notifications;
}
