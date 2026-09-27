package com.gis.servelq.repository;

import com.gis.servelq.models.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    /**
     * One grouped count instead of loading every feedback row and walking it
     * three times in Java. Returns [rating, count] pairs; ratings with no rows
     * are simply absent, so the caller defaults them to zero.
     */
    @Query("SELECT f.rating, COUNT(f) FROM Feedback f GROUP BY f.rating")
    List<Object[]> countByRating();

    Page<Feedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ==================== NEW: DATE FILTERING (list only) ====================

    /**
     * Paged feedback between two timestamps, newest first.
     */
    Page<Feedback> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );
}