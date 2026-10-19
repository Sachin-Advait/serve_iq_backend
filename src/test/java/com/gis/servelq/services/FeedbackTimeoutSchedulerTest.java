package com.gis.servelq.services;

import com.gis.servelq.models.Counter;
import com.gis.servelq.models.CounterStatus;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackTimeoutSchedulerTest {

    @Mock private CounterRepository counterRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private FeedbackService feedbackService;

    private FeedbackTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new FeedbackTimeoutScheduler(counterRepository, tokenRepository, feedbackService);
        Counter counter = new Counter();
        counter.setId("c1");
        counter.setCode("C1");
        counter.setStatus(CounterStatus.COMPLETE);
        Token token = new Token();
        token.setId("t1");
        token.setStatus(TokenStatus.REVIEW);
        // A stored end time far in the past must not count.
        token.setEndAt(LocalDateTime.now().minusDays(3));
        when(counterRepository.findByStatus(CounterStatus.COMPLETE)).thenReturn(List.of(counter));
        when(tokenRepository.findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(
                List.of(TokenStatus.REVIEW), "c1")).thenReturn(List.of(token));
    }

    @Test
    void waitsTheFullTimeoutOnItsOwnClock() {
        ReflectionTestUtils.setField(scheduler, "timeoutSeconds", 30L);

        scheduler.expireStaleReviews();
        scheduler.expireStaleReviews();

        verify(feedbackService, never()).expireStaleReview(any(), any());
    }

    @Test
    void freesOnceTheTimeoutHasPassed() throws Exception {
        ReflectionTestUtils.setField(scheduler, "timeoutSeconds", 1L);

        scheduler.expireStaleReviews();
        Thread.sleep(1100);
        scheduler.expireStaleReviews();

        verify(feedbackService).expireStaleReview("c1", Duration.ZERO);
    }
}
