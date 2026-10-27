package com.gis.servelq.controllers;

import com.gis.servelq.dto.ApiResponseDTO;
import com.gis.servelq.dto.AuditLogDTO;
import com.gis.servelq.dto.PageResponseDTO;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/serveiq/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Search audit logs with filters
     * Example: /api/admin/audit-logs?branchId=BR001&action=TOKEN_GENERATED&startDate=2026-08-01
     */
    @GetMapping
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<AuditLogDTO>>> getAuditLogs(
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Convert String action to AuditAction enum
        AuditAction auditAction = null;
        if (action != null && !action.isEmpty()) {
            try {
                auditAction = AuditAction.valueOf(action);
            } catch (IllegalArgumentException e) {
                // Invalid action, return empty or ignore
            }
        }

        PageResponseDTO<AuditLogDTO> logs = auditLogService.searchAuditLogs(
                branchId, userId, auditAction, entityType, startDate, endDate, page, size);

        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Audit logs retrieved successfully", logs));
    }
    /**
     * Get audit statistics
     * Example: /api/admin/audit-logs/stats?branchId=BR001&days=30
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> getAuditStats(
            @RequestParam String branchId,
            @RequestParam(defaultValue = "30") int days) {

        Map<String, Object> stats = auditLogService.getAuditStats(branchId, days);
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Audit stats retrieved successfully", stats));
    }
}