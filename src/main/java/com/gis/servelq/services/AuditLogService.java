package com.gis.servelq.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gis.servelq.dto.AuditLogDTO;
import com.gis.servelq.dto.PageResponseDTO;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.AuditLog;
import com.gis.servelq.repository.AuditLogRepository;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    /**
     * Logs an audit event. Resolves IP/User-Agent from the request SYNCHRONOUSLY
     * (on the calling thread, where the request context is still live), then
     * hands off the actual DB write to AuditLogWriter, which runs it async.
     */
    public void log(AuditAction action, String entityType, String entityId,
                    String entityName, String description, AuthenticatedUser user,
                    String branchId, HttpServletRequest request) {
        String ipAddress = request != null ? getClientIp(request) : null;
        String userAgent = request != null ? request.getHeader("User-Agent") : null;

        AuditLog auditLog = AuditLog.builder()
                .userId(user != null ? user.id() : "SYSTEM")
                .userName(user != null ? user.email() : "System")
                .userRole(user != null ? user.role() : "SYSTEM")
                .action(action.name())
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .description(description)
                .branchId(branchId != null ? branchId :
                        (user != null ? user.branchId() : null))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogWriter.write(auditLog);
    }

    public void logWithChanges(AuditAction action, String entityType, String entityId,
                               String entityName, String description, Object oldValue,
                               Object newValue, AuthenticatedUser user, String branchId,
                               HttpServletRequest request) {
        String ipAddress = request != null ? getClientIp(request) : null;
        String userAgent = request != null ? request.getHeader("User-Agent") : null;

        String oldJson = null;
        String newJson = null;
        try {
            oldJson = oldValue != null ? objectMapper.writeValueAsString(oldValue) : null;
            newJson = newValue != null ? objectMapper.writeValueAsString(newValue) : null;
        } catch (Exception e) {
            log.error("Failed to serialize audit log changes: {}", e.getMessage(), e);
        }

        AuditLog auditLog = AuditLog.builder()
                .userId(user != null ? user.id() : "SYSTEM")
                .userName(user != null ? user.email() : "System")
                .userRole(user != null ? user.role() : "SYSTEM")
                .action(action.name())
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .description(description)
                .oldValue(oldJson)
                .newValue(newJson)
                .branchId(branchId != null ? branchId :
                        (user != null ? user.branchId() : null))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogWriter.write(auditLog);
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<AuditLogDTO> searchAuditLogs(
            String branchId, String userId, AuditAction action,
            String entityType, Instant startDate, Instant endDate,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "created_at"));

        // Convert AuditAction enum to String to avoid PostgreSQL type inference issue
        String actionStr = action != null ? action.name() : null;

        Page<AuditLog> auditPage = auditLogRepository.searchAuditLogs(
                branchId, userId, actionStr, entityType, startDate, endDate, pageable);

        Page<AuditLogDTO> dtoPage = auditPage.map(this::convertToDTO);
        return new PageResponseDTO<>(dtoPage);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAuditStats(String branchId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        Map<String, Object> stats = new HashMap<>();

        List<Object[]> actionStats = auditLogRepository.getActionStats(branchId, since);
        Map<String, Long> actionCounts = new HashMap<>();
        actionStats.forEach(row -> actionCounts.put(row[0].toString(), (Long) row[1]));
        stats.put("actionsByType", actionCounts);

        List<Object[]> activeUsers = auditLogRepository.getMostActiveUsers(
                branchId, since, PageRequest.of(0, 10));
        List<Map<String, Object>> topUsers = activeUsers.stream()
                .map(row -> Map.of(
                        "userId", row[0],
                        "userName", row[1],
                        "count", row[2]
                )).toList();
        stats.put("mostActiveUsers", topUsers);

        return stats;
    }

    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM daily
    @Transactional
    public void cleanupOldLogs() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        auditLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Cleaned up audit logs older than 90 days");
    }

    private AuditLogDTO convertToDTO(AuditLog auditLog) {
        return AuditLogDTO.builder()
                .id(auditLog.getId())
                .userId(auditLog.getUserId())
                .userName(auditLog.getUserName())
                .userRole(auditLog.getUserRole())
                .action(AuditAction.valueOf(auditLog.getAction()))
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .entityName(auditLog.getEntityName())
                .description(auditLog.getDescription())
                .branchId(auditLog.getBranchId())
                .ipAddress(auditLog.getIpAddress())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}