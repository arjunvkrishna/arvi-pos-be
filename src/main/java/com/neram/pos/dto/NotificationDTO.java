package com.neram.pos.dto;

import com.neram.pos.entity.SystemNotification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A notification as one particular manager sees it: {@code status} is theirs, not everyone's. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private SystemNotification.NotificationType type;
    private String title;
    private String message;
    private Long referenceId;
    private String createdBy;
    private SystemNotification.Status status;
    private LocalDateTime createdAt;

    public static NotificationDTO of(SystemNotification n, boolean read) {
        return NotificationDTO.builder()
                .id(n.getId()).type(n.getType()).title(n.getTitle()).message(n.getMessage())
                .referenceId(n.getReferenceId()).createdBy(n.getCreatedBy()).createdAt(n.getCreatedAt())
                .status(read ? SystemNotification.Status.READ : SystemNotification.Status.UNREAD)
                .build();
    }
}
