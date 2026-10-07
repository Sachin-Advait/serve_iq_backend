package com.gis.servelq.repository;

import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {

    boolean existsByStatusInAndAssignedCounterId(List<TokenStatus> statuses, String assignedCounterId);
    
    List<Token> findByBranchId(String branchId);

    long countByBranchId(String branchId);

    // The live queue (agent app, TV board, waiting counts) only covers the
    // current business day. Tokens left WAITING/HOLD/CALLING from an earlier
    // day stay in the table for reports but must not show up in the queue, so
    // every queue read below is scoped to tokenDate. The no-date defaults pass
    // LocalDate.now(), the same clock TokenIssuer stamps tokenDate with.

    List<Token> findByBranchIdAndStatusAndTokenDateOrderByPriorityAscCreatedAtAsc(
            String branchId, TokenStatus status, LocalDate tokenDate, Pageable pageable);

    default List<Token> findTodayByBranchIdAndStatus(String branchId, TokenStatus status, Pageable pageable) {
        return findByBranchIdAndStatusAndTokenDateOrderByPriorityAscCreatedAtAsc(
                branchId, status, LocalDate.now(), pageable);
    }

    @Query("SELECT t.status, COUNT(t) FROM Token t WHERE t.branchId = :branchId AND t.tokenDate = :tokenDate GROUP BY t.status")
    List<Object[]> countByStatusForBranch(@Param("branchId") String branchId, @Param("tokenDate") LocalDate tokenDate);

    default List<Object[]> countByStatusForBranch(String branchId) {
        return countByStatusForBranch(branchId, LocalDate.now());
    }

    List<Token> findTop20ByStatusAndAssignedCounterIdOrderByEndAtDesc(TokenStatus status, String assignedCounterId);

    List<Token> findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(
            List<TokenStatus> statuses, String assignedCounterId);

    /**
     * Bulk version of findByStatusInAndAssignedCounterId — one query for the
     * whole display board instead of one query per counter. There should be
     * at most one SERVING/CALLING/REVIEW token per counter, so the caller can
     * safely key the result by assignedCounterId.
     */
    List<Token> findByStatusInAndAssignedCounterIdIn(List<TokenStatus> statuses, List<String> assignedCounterIds);

    @Query(value = """
            SELECT *
            FROM tokens t
            WHERE t.status = 'WAITING'
              AND t.token_date = :tokenDate
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
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Token> findNextToken(@Param("counterId") String counterId, @Param("tokenDate") LocalDate tokenDate);

    default Optional<Token> findNextToken(String counterId) {
        return findNextToken(counterId, LocalDate.now());
    }

    Optional<Token> findFirstByAssignedCounterIdAndStatus(String assignedCounterId, TokenStatus status);

    @Query("""
            SELECT t FROM Token t
            WHERE t.branchId = :branchId
              AND t.status = com.gis.servelq.models.TokenStatus.CALLING
              AND t.tokenDate = :tokenDate
            ORDER BY t.startAt DESC
            """)
    List<Token> findLatestCalledTokens(@Param("branchId") String branchId,
                                       @Param("tokenDate") LocalDate tokenDate,
                                       Pageable pageable);

    default List<Token> findLatestCalledTokens(String branchId, Pageable pageable) {
        return findLatestCalledTokens(branchId, LocalDate.now(), pageable);
    }

    @Query("""
            SELECT t FROM Token t
            WHERE (t.status = com.gis.servelq.models.TokenStatus.WAITING
                   OR t.status = com.gis.servelq.models.TokenStatus.HOLD)
              AND t.tokenDate = :tokenDate
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
    List<Token> findUpcomingTokensForCounter(@Param("counterId") String counterId,
                                             @Param("tokenDate") LocalDate tokenDate);

    default List<Token> findUpcomingTokensForCounter(String counterId) {
        return findUpcomingTokensForCounter(counterId, LocalDate.now());
    }

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
              AND t.tokenDate = :tokenDate
            """)
    Double getAvgServiceTimeSecondsByCounter(@Param("counterId") String counterId,
                                             @Param("tokenDate") LocalDate tokenDate);

    // Today only: an all-time average was dragged up by old tokens left open
    // for hours, so agents saw values like 400 minutes.
    default Double getAvgServiceTimeSecondsByCounter(String counterId) {
        return getAvgServiceTimeSecondsByCounter(counterId, LocalDate.now());
    }

    long countByBranchIdAndCreatedAtBetween(String branchId, LocalDateTime start, LocalDateTime end);

    List<Token> findByBranchIdAndCreatedAtBetween(String branchId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(t) FROM Token t WHERE t.serviceId = :serviceId AND t.status = :status AND t.tokenDate = :tokenDate")
    long countByServiceIdAndStatus(@Param("serviceId") String serviceId,
                                   @Param("status") TokenStatus status,
                                   @Param("tokenDate") LocalDate tokenDate);

    default long countByServiceIdAndStatus(String serviceId, TokenStatus status) {
        return countByServiceIdAndStatus(serviceId, status, LocalDate.now());
    }

    @Query("SELECT COUNT(t) FROM Token t WHERE t.assignedCounterId = :counterId AND t.status = :status")
    long countByAssignedCounterIdAndStatus(@Param("counterId") String counterId, @Param("status") TokenStatus status);
}