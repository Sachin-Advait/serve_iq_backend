package com.gis.servelq.services;

import com.gis.servelq.models.Counter;
import com.gis.servelq.models.CounterStatus;
import com.gis.servelq.repository.CounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Frees counters whose visitor hasn't rated within app.feedback-timeout.seconds
 * (30 by default), the same way the feedback app's idle timer does, so a counter
 * never stays on COMPLETE when the feedback screen is offline. Set the seconds
 * to 0 to turn it off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackTimeoutScheduler {

    private final CounterRepository counterRepository;
    private final FeedbackService feedbackService;

    @Value("${app.feedback-timeout.seconds:30}")
    private long timeoutSeconds;

    @Scheduled(fixedDelayString = "${app.feedback-timeout.check-ms:5000}")
    public void expireStaleReviews() {
        if (timeoutSeconds <= 0) return;
        Duration timeout = Duration.ofSeconds(timeoutSeconds);

        for (Counter counter : counterRepository.findByStatus(CounterStatus.COMPLETE)) {
            try {
                if (feedbackService.expireStaleReview(counter.getId(), timeout)) {
                    log.info("No feedback within {}s at counter {}: token closed, counter freed",
                            timeoutSeconds, counter.getCode());
                }
            } catch (Exception e) {
                log.error("Feedback timeout failed for counter {}", counter.getCode(), e);
            }
        }
    }
}
