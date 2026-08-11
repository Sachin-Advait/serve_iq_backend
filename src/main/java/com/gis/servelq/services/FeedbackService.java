package com.gis.servelq.services;

import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.FeedbackRepository;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final CounterRepository counterRepository;
    private final TokenRepository tokenRepository;
    private final TokenEventPublisher tokenEventPublisher;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;


    @Transactional
    public Feedback createFeedback(Feedback feedback, AuthenticatedUser user) {
        Counter counter = counterRepository.findByCode(feedback.getCounterCode())
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));

        Token token = tokenRepository.findById(feedback.getTokenId())
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        token.setStatus(TokenStatus.DONE);
        tokenRepository.save(token);

        counter.setStatus(CounterStatus.IDLE);
        counterRepository.save(counter);

        Feedback saved = feedbackRepository.save(feedback);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.FEEDBACK_SUBMITTED,
                token.getBranchId(),
                token.getId(),
                token.getToken(),
                counter.getId(),
                Instant.now()
        ));

        // Log feedback submitted
        auditLogService.log(
                AuditAction.FEEDBACK_SUBMITTED,
                "Feedback",
                saved.getId(),
                saved.getTokenId(),
                "Feedback submitted: Rating " + saved.getRating() + " for token " + saved.getTokenId(),
                user, // May be null for public endpoint
                counter.getBranchId(),
                request
        );

        return saved;
    }

    /** Paged - this returned the entire table, newest first is what the UI wants. */
    public Page<Feedback> getAll(Pageable pageable) {
        return feedbackRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public void delete(String id, AuthenticatedUser user) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));

        // Log before deletion
        auditLogService.log(
                AuditAction.DELETE,
                "Feedback",
                feedback.getId(),
                feedback.getTokenId(),
                "Feedback deleted: " + feedback.getId(),
                user,
                null,
                request
        );

        feedbackRepository.deleteById(id);
    }

    /**
     * Was findAll() into memory followed by three separate stream passes to
     * count three values, so the cost grew with every piece of feedback ever
     * left. One GROUP BY does the same work in the database.
     */
    public Map<String, Long> getFeedbackSummary() {
        Map<Feedback.MoodRating, Long> counts = new EnumMap<>(Feedback.MoodRating.class);
        for (Object[] row : feedbackRepository.countByRating()) {
            counts.put((Feedback.MoodRating) row[0], (Long) row[1]);
        }

        Map<String, Long> summary = new HashMap<>();
        summary.put("totalHappy", counts.getOrDefault(Feedback.MoodRating.HAPPY, 0L));
        summary.put("totalNeutral", counts.getOrDefault(Feedback.MoodRating.NEUTRAL, 0L));
        summary.put("totalSad", counts.getOrDefault(Feedback.MoodRating.SAD, 0L));
        return summary;
    }
}