package com.gis.servelq.repository;

import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {
    List<Token> findByBranchId(String branchId);

    List<Token> findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(String branchId, TokenStatus status);

    /**
     * Same ordering but with the row limit applied by the database.
     *
     * The TV display asked for every WAITING/HOLD token for the branch and then
     * did .stream().limit(10) in Java, so Postgres shipped and Hibernate
     * hydrated the entire queue to show ten of them.
     */
    List<Token> findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(
            String branchId, TokenStatus status, Pageable pageable);

    /**
     * Counts for every status the board shows, in one round trip instead of the
     * four separate COUNT queries it used to run. Statuses with no rows are
     * absent from the result, so the caller defaults them to zero.
     */
    @Query("SELECT t.status, COUNT(t) FROM Token t WHERE t.branchId = :branchId GROUP BY t.status")
    List<Object[]> countByStatusForBranch(@Param("branchId") String branchId);

    List<Token> findTop20ByStatusAndAssignedCounterIdOrderByEndAtDesc(TokenStatus status, String assignedCounterId);

    Optional<Token> findByStatusInAndAssignedCounterId(List<TokenStatus> statuses, String assignedCounterId);

    long countByBranchIdAndStatus(String branchId, TokenStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "3000"
    ))
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
    List<Token> findLatestCalledTokens(@Param("branchId") String branchId, Pageable pageable);


    @Query(value = """
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
}