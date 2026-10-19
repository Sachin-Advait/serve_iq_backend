package com.gis.servelq.services;

import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.FeedbackRepository;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The backend frees a counter left on COMPLETE when no feedback arrives in time,
 * and a rating that comes in afterwards replaces any earlier one for the token.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackTimeoutTest {

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private CounterRepository counterRepository;
    @Mock private UserRepository userRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private TokenEventPublisher tokenEventPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock private HttpServletRequest request;

    private FeedbackService feedbackService;
    private Counter counter;
    private Token token;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(feedbackRepository, counterRepository, userRepository,
                tokenRepository, tokenEventPublisher, auditLogService, request);

        counter = new Counter();
        counter.setId("c1");
        counter.setCode("C1");
        counter.setStatus(CounterStatus.COMPLETE);

        token = new Token();
        token.setId("t1");
        token.setStatus(TokenStatus.REVIEW);
        token.setAssignedCounterId("c1");

        lenient().when(counterRepository.findById("c1")).thenReturn(Optional.of(counter));
        lenient().when(counterRepository.findByCode("C1")).thenReturn(Optional.of(counter));
        lenient().when(tokenRepository.findById("t1")).thenReturn(Optional.of(token));
        lenient().when(tokenRepository.findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(
                List.of(TokenStatus.REVIEW), "c1")).thenAnswer(i ->
                token.getStatus() == TokenStatus.REVIEW ? List.of(token) : List.of());
        lenient().when(feedbackRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void waitsUntilTheTimeoutHasPassed() {
        token.setEndAt(LocalDateTime.now().minusSeconds(10));

        assertThat(feedbackService.expireStaleReview("c1", Duration.ofSeconds(30))).isFalse();
        assertThat(counter.getStatus()).isEqualTo(CounterStatus.COMPLETE);
        assertThat(token.getStatus()).isEqualTo(TokenStatus.REVIEW);
    }

    @Test
    void freesTheCounterAfterTheTimeout() {
        token.setEndAt(LocalDateTime.now().minusSeconds(31));

        assertThat(feedbackService.expireStaleReview("c1", Duration.ofSeconds(30))).isTrue();
        assertThat(counter.getStatus()).isEqualTo(CounterStatus.IDLE);
        assertThat(token.getStatus()).isEqualTo(TokenStatus.DONE);
        verify(tokenEventPublisher).publish(any());
    }

    @Test
    void waitsForTheMostRecentlyCompletedToken() {
        // An older visit left in REVIEW sorts first (created later, completed
        // long ago); the visitor who just finished must still get 30 seconds.
        Token older = new Token();
        older.setId("t0");
        older.setStatus(TokenStatus.REVIEW);
        older.setAssignedCounterId("c1");
        older.setEndAt(LocalDateTime.now().minusMinutes(5));
        token.setEndAt(LocalDateTime.now().minusSeconds(10));
        when(tokenRepository.findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(
                List.of(TokenStatus.REVIEW), "c1")).thenReturn(List.of(older, token));

        assertThat(feedbackService.expireStaleReview("c1", Duration.ofSeconds(30))).isFalse();
        assertThat(counter.getStatus()).isEqualTo(CounterStatus.COMPLETE);
    }

    @Test
    void freesACounterStuckOnCompleteWithNoTokenToRate() {
        token.setStatus(TokenStatus.DONE);
        counter.setUpdatedAt(LocalDateTime.now().minusMinutes(5));

        assertThat(feedbackService.expireStaleReview("c1", Duration.ofSeconds(30))).isTrue();
        assertThat(counter.getStatus()).isEqualTo(CounterStatus.IDLE);
    }

    @Test
    void leavesACounterThatHasMovedOn() {
        counter.setStatus(CounterStatus.CALLING);
        token.setEndAt(LocalDateTime.now().minusMinutes(5));

        assertThat(feedbackService.expireStaleReview("c1", Duration.ofSeconds(30))).isFalse();
        assertThat(counter.getStatus()).isEqualTo(CounterStatus.CALLING);
    }

    @Test
    void aLateRatingReplacesTheEarlierOne() {
        Feedback earlier = new Feedback();
        earlier.setId("f1");
        earlier.setTokenId("t1");
        earlier.setRating(Feedback.MoodRating.NEUTRAL);
        when(feedbackRepository.findFirstByTokenIdOrderByCreatedAtDesc("t1")).thenReturn(Optional.of(earlier));
        counter.setStatus(CounterStatus.IDLE);
        token.setStatus(TokenStatus.DONE);

        Feedback late = new Feedback();
        late.setTokenId("t1");
        late.setCounterCode("C1");
        late.setRating(Feedback.MoodRating.SAD);
        late.setReview("Too slow");

        Feedback saved = feedbackService.createFeedback(late, null);

        assertThat(saved.getId()).isEqualTo("f1");
        assertThat(saved.getRating()).isEqualTo(Feedback.MoodRating.SAD);
        assertThat(saved.getReview()).isEqualTo("Too slow");
        verify(feedbackRepository, never()).save(late);
    }
}
