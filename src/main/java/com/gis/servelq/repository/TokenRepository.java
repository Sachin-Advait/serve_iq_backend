package com.gis.servelq.repository;

import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {
    List<Token> findByBranchId(String branchId);

    long countByBranchId(String branchId);

    List<Token> findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(String branchId, TokenStatus status);

    List<Token> findTop20ByStatusAndAssignedCounterIdOrderByEndAtDesc(TokenStatus status, String assignedCounterId);

    Optional<Token> findByStatusInAndAssignedCounterId(List<TokenStatus> statuses, String assignedCounterId);

    long countByBranchIdAndStatus(String branchId, TokenStatus status);

    @Query(value = """
            SELECT *
            FROM tokens t
            WHERE t.status = 'WAITING'
              AND (
                    t.counter_ids IS NULL
                    OR t.counter_ids = ''
                    OR t.counter_ids = :counterId
                    OR t.counter_ids LIKE CONCAT(:counterId, ',%')
                    OR t.counter_ids LIKE CONCAT('%,', :counterId)
                    OR t.counter_ids LIKE CONCAT('%,', :counterId, ',%')
                )
            ORDER BY
                t.priority DESC,
                t.is_transfer DESC,
                t.created_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Token> findNextToken(@Param("counterId") String counterId);

    Optional<Token> findFirstByAssignedCounterIdAndStatus(String assignedCounterId, TokenStatus status);

    @Query("""
            SELECT t FROM Token t
            WHERE t.branchId = :branchId
              AND t.status = com.gis.servelq.models.TokenStatus.CALLING
            ORDER BY t.startAt DESC
            """)
    List<Token> findLatestCalledTokens(@Param("branchId") String branchId);

    @Query("""
            SELECT t FROM Token t
            WHERE (t.status = com.gis.servelq.models.TokenStatus.WAITING
                   OR t.status = com.gis.servelq.models.TokenStatus.HOLD)
              AND (
                    t.counterIds IS NULL
                    OR t.counterIds = ''
                    OR t.counterIds LIKE CONCAT(:counterId)
                    OR t.counterIds LIKE CONCAT(:counterId, ',%')
                    OR t.counterIds LIKE CONCAT('%,', :counterId)
                    OR t.counterIds LIKE CONCAT('%,', :counterId, ',%')
                  )
            ORDER BY
                t.priority DESC,
                CASE WHEN t.isTransfer = true THEN 0 ELSE 1 END ASC,
                t.createdAt ASC
            """)
    List<Token> findUpcomingTokensForCounter(@Param("counterId") String counterId);

    @Query("""
            SELECT MAX(t.tokenSeq)
            FROM Token t
            WHERE t.branchId = :branchId
              AND t.priority = :priority
              AND t.createdAt >= :startOfDay
              AND t.createdAt < :startOfNextDay
            """)
    Optional<Integer> findLastSeqForPriorityToday(
            @Param("branchId") String branchId,
            @Param("priority") Integer priority,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("startOfNextDay") LocalDateTime startOfNextDay
    );

    @Query("""
            SELECT AVG(TIMESTAMPDIFF(SECOND, t.startAt, t.endAt))
            FROM Token t
            WHERE t.assignedCounterId = :counterId
              AND t.startAt IS NOT NULL
              AND t.endAt IS NOT NULL
              AND t.status = 'DONE'
            """)
    Double getAvgServiceTimeSecondsByCounter(@Param("counterId") String counterId);

    long countByBranchIdAndCreatedAtBetween(String branchId, LocalDateTime start, LocalDateTime end);

    List<Token> findByBranchIdAndCreatedAtBetween(String branchId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(t) FROM Token t WHERE t.serviceId = :serviceId AND t.status = :status")
    long countByServiceIdAndStatus(@Param("serviceId") String serviceId, @Param("status") TokenStatus status);

    @Query("SELECT COUNT(t) FROM Token t WHERE t.assignedCounterId = :counterId AND t.status = :status")
    long countByAssignedCounterIdAndStatus(@Param("counterId") String counterId, @Param("status") TokenStatus status);

    @Query("SELECT t FROM Token t WHERE t.branchId = :branchId " +
            "AND t.status = 'DONE' AND t.createdAt BETWEEN :start AND :end")
    List<Token> findCompletedTokensByDate(@Param("branchId") String branchId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);
}