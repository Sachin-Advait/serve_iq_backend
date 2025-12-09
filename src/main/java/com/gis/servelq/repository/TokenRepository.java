package com.gis.servelq.repository;

import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {
    List<Token> findByBranchId(String branchId);

    List<Token> findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(String branchId, TokenStatus status);

    List<Token> findByStatusAndAssignedCounterId(TokenStatus status, String assignedCounterId);

    @Query("""
                SELECT t
                FROM Token t
                WHERE t.serviceId = :serviceId
                  AND t.status = :status
                  AND (
                        t.counterIds LIKE CONCAT(:counterId, ',%')
                        OR t.counterIds LIKE CONCAT('%,', :counterId, ',%')
                        OR t.counterIds LIKE CONCAT('%,', :counterId)
                        OR t.counterIds = :counterId
                  )
                ORDER BY
                    t.priority DESC,
                    t.isTransfer DESC,
                    t.createdAt ASC
            """)
    Optional<Token> findNextToken(
            String serviceId,
            TokenStatus status,
            String counterId
    );


    Optional<Token> findFirstByAssignedCounterIdAndStatus(String assignedCounterId, TokenStatus status);

    long countByServiceIdAndStatus(String serviceId, TokenStatus status);

    @Query("""
            SELECT t FROM Token t
            WHERE t.branchId = :branchId
              AND t.status = com.gis.servelq.models.TokenStatus.CALLING
            ORDER BY t.startAt DESC
            """)
    List<Token> findLatestCalledTokens(@Param("branchId") String branchId);


    @Query(value = """
                SELECT t FROM Token t
                WHERE t.status = com.gis.servelq.models.TokenStatus.WAITING
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
                SELECT MAX(CAST(t.token AS integer))
                FROM Token t
                WHERE t.branchId = :branchId
            """)
    Optional<Integer> findLastTokenNumber(@Param("branchId") String branchId);
}