package com.gis.servelq.repository;


import com.gis.servelq.models.ResponseModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ResponseRepo
        extends JpaRepository<ResponseModel, UUID> {

    List<ResponseModel> findByQuizSurveyId(UUID quizSurveyId);

    List<ResponseModel> findByUserId(String userId);

    List<ResponseModel> findByQuizSurveyIdAndUserId(
            UUID quizSurveyId,
            String userId
    );

    // 🔥 Optimized participation check
    @Query("""
        SELECT COUNT(r) > 0
        FROM ResponseModel r
        WHERE r.quizSurveyId = :quizId
          AND r.userId = :userId
    """)
    boolean hasUserParticipated(
            @Param("quizId") UUID quizId,
            @Param("userId") String userId
    );

    /**
     * Scored responses since a cut-off, selecting only the columns the
     * low-scorer report needs.
     *
     * getLowScoringUsers used to call findAll() - every response row for every
     * quiz ever, each dragging its jsonb answers blob through Hibernate - and
     * then filter by date in Java. answers is an eager basic attribute so it was
     * deserialised for every row despite never being read here.
     */
    @Query("""
        SELECT r.userId, r.score, r.maxScore
        FROM ResponseModel r
        WHERE r.submittedAt > :fromDate
          AND r.score IS NOT NULL
          AND r.maxScore IS NOT NULL
    """)
    List<Object[]> findScoresSince(@Param("fromDate") Instant fromDate);
}
