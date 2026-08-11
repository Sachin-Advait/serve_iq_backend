package com.gis.servelq.repository;

import com.gis.servelq.models.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    Page<AuditLog> findByBranchIdOrderByCreatedAtDesc(String branchId, Pageable pageable);

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId, Pageable pageable);

    @Query(value = "SELECT * FROM audit_logs a WHERE " +
            "(:branchId IS NULL OR a.branch_id = CAST(:branchId AS VARCHAR)) AND " +
            "(:userId IS NULL OR a.user_id = CAST(:userId AS VARCHAR)) AND " +
            "(:action IS NULL OR a.action = CAST(:action AS VARCHAR)) AND " +
            "(:entityType IS NULL OR a.entity_type = CAST(:entityType AS VARCHAR)) AND " +
            "(CAST(:startDate AS TIMESTAMP) IS NULL OR a.created_at >= CAST(:startDate AS TIMESTAMP)) AND " +
            "(CAST(:endDate AS TIMESTAMP) IS NULL OR a.created_at <= CAST(:endDate AS TIMESTAMP)) " +
            "ORDER BY a.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM audit_logs a WHERE " +
                    "(:branchId IS NULL OR a.branch_id = CAST(:branchId AS VARCHAR)) AND " +
                    "(:userId IS NULL OR a.user_id = CAST(:userId AS VARCHAR)) AND " +
                    "(:action IS NULL OR a.action = CAST(:action AS VARCHAR)) AND " +
                    "(:entityType IS NULL OR a.entity_type = CAST(:entityType AS VARCHAR)) AND " +
                    "(CAST(:startDate AS TIMESTAMP) IS NULL OR a.created_at >= CAST(:startDate AS TIMESTAMP)) AND " +
                    "(CAST(:endDate AS TIMESTAMP) IS NULL OR a.created_at <= CAST(:endDate AS TIMESTAMP))",
            nativeQuery = true)
    Page<AuditLog> searchAuditLogs(
            @Param("branchId") String branchId,
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    @Query("SELECT a.action, COUNT(a) FROM AuditLog a " +
            "WHERE a.branchId = :branchId AND a.createdAt >= :since " +
            "GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> getActionStats(@Param("branchId") String branchId, @Param("since") Instant since);

    @Query("SELECT a.userId, a.userName, COUNT(a) as cnt FROM AuditLog a " +
            "WHERE a.branchId = :branchId AND a.createdAt >= :since " +
            "GROUP BY a.userId, a.userName ORDER BY cnt DESC")
    List<Object[]> getMostActiveUsers(@Param("branchId") String branchId, @Param("since") Instant since,
                                      Pageable pageable);

    void deleteByCreatedAtBefore(Instant before);
}