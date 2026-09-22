package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {
    private Long id;
    private String actorEmail;
    private String actorRoles;
    private String action;
    private String entityType;
    private Long entityId;
    private String entityName;
    private String details;
    private LocalDateTime createdAt;
}
