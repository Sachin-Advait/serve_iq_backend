package com.gis.servelq.dto;

import com.gis.servelq.models.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private String id;
    private String userId;
    private String userName;
    private String userRole;
    private AuditAction action;
    private String entityType;
    private String entityId;
    private String entityName;
    private String description;
    private String oldValue;
    private String newValue;
    private String branchId;
    private String ipAddress;
    private Instant createdAt;
}