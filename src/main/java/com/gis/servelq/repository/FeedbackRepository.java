package com.gis.servelq.repository;

import com.gis.servelq.models.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    /**
     * One grouped count instead of loading every feedback row and walking it
     * three times in Java. Returns [rating, count] pairs; ratings with no rows
     * are simply absent, so the caller defaults them to zero.
     */
    @Query("SELECT f.rating, COUNT(f) FROM Feedback f GROUP BY f.rating")
    List<Object[]> countByRating();

    /** Same as {@link #countByRating()} for feedback created in [start, end). */
    @Query("SELECT f.rating, COUNT(f) FROM Feedback f " +
            "WHERE f.createdAt >= :start AND f.createdAt < :end GROUP BY f.rating")
    List<Object[]> countByRatingBetween(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    Optional<Feedback> findFirstByTokenIdOrderByCreatedAtDesc(String tokenId);

    /**
     * The feedback list with every filter optional (null = not filtered):
     * agent, [start, end), rating, and a search pattern (already lower-cased
     * and wrapped in %) matched against the comment, agent name, counter code,
     * and the ticket's number and service. Order comes from the Pageable.
     */
    @Query("SELECT f FROM Feedback f WHERE "
            + "(:agentId IS NULL OR f.agentId = :agentId) "
            + "AND (CAST(:start AS LocalDateTime) IS NULL OR f.createdAt >= :start) "
            + "AND (CAST(:end AS LocalDateTime) IS NULL OR f.createdAt < :end) "
            + "AND (:rating IS NULL OR f.rating = :rating) "
            + "AND (:search IS NULL "
            + "  OR LOWER(f.review) LIKE :search "
            + "  OR LOWER(f.agentName) LIKE :search "
            + "  OR LOWER(f.counterCode) LIKE :search "
            + "  OR EXISTS (SELECT 1 FROM Token t WHERE t.id = f.tokenId "
            + "      AND (LOWER(t.serviceName) LIKE :search OR LOWER(t.token) LIKE :search)))")
    Page<Feedback> search(@Param("agentId") String agentId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("rating") Feedback.MoodRating rating,
                          @Param("search") String search,
                          Pageable pageable);
}