package com.gis.servelq.repository;

import com.gis.servelq.models.Counter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CounterRepository extends JpaRepository<Counter, String> {
    Optional<Counter> findByCodeAndBranchId(String code, String branchId);

    List<Counter> findByUserId(String userId);
    
    Optional<Counter> findByCode(String code);

    List<Counter> findByBranchIdOrderByCreatedAtAsc(String branchId);

    List<Counter> findByBranchId(String branchId);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Counter c SET c.userId = :userId
            WHERE c.id = :counterId
              AND (c.userId IS NULL OR c.userId = '' OR c.userId = :userId)
            """)
    int claimIfFree(@Param("counterId") String counterId, @Param("userId") String userId);
}