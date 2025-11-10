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
    List<Token> findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(String branchId, TokenStatus status);
    List<Token> findByServiceIdAndStatusOrderByPriorityAscCreatedAtAsc(String serviceId, TokenStatus status);
    List<Token> findByCounterIdAndStatusOrderByPriorityAscCreatedAtAsc(String counterId, TokenStatus status);
    List<Token> findByStatus(TokenStatus status);

    @Query("SELECT t FROM Token t WHERE t.branchId = :branchId AND t.status IN ('CALLING') " +
            "ORDER BY t.calledAt DESC")
    List<Token> findLatestCalledTokens(@Param("branchId") String branchId);

    @Query("""
    SELECT t FROM Token t
    WHERE t.status = 'WAITING'
      AND t.serviceId IN (
          SELECT s.id FROM Counter c JOIN c.services s WHERE c.id = :counterId
      )
    ORDER BY t.priority ASC, t.createdAt ASC
""")
    List<Token> findUpcomingTokensForCounter(@Param("counterId") String counterId);


    Optional<Token> findFirstByServiceIdAndStatusOrderByPriorityAscCreatedAtAsc(String serviceId, TokenStatus status);

    @Query("SELECT MAX(CAST(SUBSTRING(t.token, LENGTH(:prefix) + 1) AS integer)) FROM Token t " +
            "WHERE t.branchId = :branchId AND t.serviceId = :serviceId " +
            "AND t.token LIKE CONCAT(:prefix, '%')")
    Optional<Integer> findLastTokenNumber(@Param("branchId") String branchId,
                                          @Param("serviceId") String serviceId,
                                          @Param("prefix") String prefix);

    long countByServiceIdAndStatus(String serviceId, TokenStatus status);
}