package com.gis.servelq.services;

import com.gis.servelq.models.AuditLog;
import com.gis.servelq.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists AuditLog entities asynchronously.
 * Kept as a separate bean (rather than a method on AuditLogService) so that
 * @Async actually applies — Spring's async proxy only intercepts calls made
 * from OUTSIDE the bean; a self-invoked call (this.someAsyncMethod()) bypasses
 * the proxy and runs synchronously, silently ignoring @Async.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional
    public void write(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }
}