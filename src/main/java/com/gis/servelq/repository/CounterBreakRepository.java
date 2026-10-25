package com.gis.servelq.repository;

import com.gis.servelq.models.CounterBreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CounterBreakRepository extends JpaRepository<CounterBreak, String> {

    Optional<CounterBreak> findFirstByCounterIdAndEndedAtIsNull(String counterId);

    // Breaks that overlap [from, to)
    @Query("SELECT b FROM CounterBreak b WHERE b.branchId = :branchId AND b.startedAt < :to " +
            "AND (b.endedAt IS NULL OR b.endedAt > :from)")
    List<CounterBreak> findOverlapping(@Param("branchId") String branchId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
