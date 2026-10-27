package com.gis.servelq.services;

import org.springframework.util.StringUtils;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.repository.FeedbackRepository;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final CounterRepository counterRepository;
    private final UserRepository userRepository;
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

        boolean wasWaiting = token.getStatus() == TokenStatus.REVIEW;
        token.setStatus(TokenStatus.DONE);
        tokenRepository.save(token);

        // Only free the counter if it is still waiting on this visitor's feedback. A late
        // rating can arrive after the agent already called the next token; flipping that
        // counter to IDLE hid the live token and let the agent try to log out mid-call.
        // It can also arrive after the timeout closed this token and the agent finished
        // the next visitor, so the counter is on COMPLETE again for someone else: then
        // this token is no longer in REVIEW and the counter stays for the new rating.
        if (wasWaiting
                && counter.getStatus() == CounterStatus.COMPLETE
                && counter.getId().equals(token.getAssignedCounterId())) {
            counter.setStatus(CounterStatus.IDLE);
            counterRepository.save(counter);
        }

        // Credit the rating to whoever is logged in at this counter right now. The feedback
        // app has no idea who the agent is (agents change seats day to day), so whatever
        // name it sends is ignored.
        feedback.setAgentId(counter.getUserId());
        feedback.setAgentName(counter.getUserId() == null ? null
                : userRepository.findById(counter.getUserId()).map(User::getName).orElse(null));

        // One rating per visitor: a second submit for the same token (a retry, or a
        // rating that arrives after the screen already timed out) replaces the first.
        Feedback saved = feedbackRepository.findFirstByTokenIdOrderByCreatedAtDesc(feedback.getTokenId())
                .map(existing -> {
                    existing.setCounterCode(feedback.getCounterCode());
                    existing.setRating(feedback.getRating());
                    existing.setReview(feedback.getReview());
                    existing.setAgentId(feedback.getAgentId());
                    existing.setAgentName(feedback.getAgentName());
                    return feedbackRepository.save(existing);
                })
                .orElseGet(() -> feedbackRepository.save(feedback));

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

    /**
     * Feedback screen timed out with no rating: close the token and free the counter
     * the same way a submitted rating does, without storing a feedback row.
     * No-op if the counter has already moved on (agent called the next token, etc.).
     */
    @Transactional
    public void expireFeedback(String tokenId, String counterCode) {
        Counter counter = counterRepository.findByCode(counterCode)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (counter.getStatus() != CounterStatus.COMPLETE
                || token.getStatus() != TokenStatus.REVIEW
                || !counter.getId().equals(token.getAssignedCounterId())) {
            return;
        }

        token.setStatus(TokenStatus.DONE);
        tokenRepository.save(token);

        counter.setStatus(CounterStatus.IDLE);
        counterRepository.save(counter);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.FEEDBACK_SUBMITTED,
                token.getBranchId(),
                token.getId(),
                token.getToken(),
                counter.getId(),
                Instant.now()
        ));
    }

    /**
     * Server-side twin of the feedback app's idle timeout: a counter left on COMPLETE
     * longer than {@code timeout} (the app is offline, closed, or never got the token)
     * is freed as if the screen had timed out, so the agent can call the next visitor.
     * A rating that arrives later is still stored by {@link #createFeedback}.
     *
     * @return true if the counter was freed
     */
    @Transactional
    public boolean expireStaleReview(String counterId, Duration timeout) {
        Counter counter = counterRepository.findById(counterId).orElse(null);
        if (counter == null || counter.getStatus() != CounterStatus.COMPLETE) {
            return false;
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(timeout);

        List<Token> reviews = tokenRepository.findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(
                List.of(TokenStatus.REVIEW), counterId);
        if (reviews.isEmpty()) {
            // COMPLETE with nothing to rate (bad data, or the token was closed elsewhere)
            if (counter.getUpdatedAt() != null && counter.getUpdatedAt().isAfter(cutoff)) {
                return false;
            }
        } else {
            // Wait for the most recently completed token. The list is ordered by
            // creation time, and a token issued earlier can be completed later
            // (transfer, hold, no-show call back), so reviews.get(0) may be an
            // older visit and would free the counter early.
            boolean recent = reviews.stream()
                    .map(t -> t.getEndAt() != null ? t.getEndAt() : counter.getUpdatedAt())
                    .anyMatch(endAt -> endAt != null && endAt.isAfter(cutoff));
            if (recent) {
                return false;
            }
            reviews.forEach(t -> t.setStatus(TokenStatus.DONE));
            tokenRepository.saveAll(reviews);
        }

        counter.setStatus(CounterStatus.IDLE);
        counterRepository.save(counter);

        Token latest = reviews.stream()
                .filter(t -> t.getEndAt() != null)
                .max(java.util.Comparator.comparing(Token::getEndAt))
                .orElse(reviews.isEmpty() ? null : reviews.get(0));
        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.FEEDBACK_SUBMITTED,
                counter.getBranchId(),
                latest == null ? null : latest.getId(),
                latest == null ? null : latest.getToken(),
                counter.getId(),
                Instant.now()
        ));
        return true;
    }

    /**
     * Paged feedback, newest first.
     * - If start & end are both null → returns everything.
     * - If both are provided       → returns only rows whose createdAt is in [start, end).
     */
    public Page<Feedback> getAll(LocalDateTime start, LocalDateTime end, String agentId, Pageable pageable) {
        if (StringUtils.hasText(agentId)) {
            if (start != null && end != null) {
                return feedbackRepository.findByAgentIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        agentId, start, end, pageable);
            }
            return feedbackRepository.findByAgentIdOrderByCreatedAtDesc(agentId, pageable);
        }
        if (start != null && end != null) {
            return feedbackRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable);
        }
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
     * All-time feedback summary counts (HAPPY / NEUTRAL / SAD).
     * Always computed across the whole table — no date filtering.
     */
    public Map<String, Long> getFeedbackSummary() {
        Map<Feedback.MoodRating, Long> counts = new EnumMap<>(Feedback.MoodRating.class);
        for (Object[] row : feedbackRepository.countByRating()) {
            counts.put((Feedback.MoodRating) row[0], (Long) row[1]);
        }

        Map<String, Long> summary = new HashMap<>();
        summary.put("totalHappy",   counts.getOrDefault(Feedback.MoodRating.HAPPY,   0L));
        summary.put("totalNeutral", counts.getOrDefault(Feedback.MoodRating.NEUTRAL, 0L));
        summary.put("totalSad",     counts.getOrDefault(Feedback.MoodRating.SAD,     0L));
        return summary;
    }
}