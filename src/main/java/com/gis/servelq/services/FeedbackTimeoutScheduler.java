package com.gis.servelq.services;

import com.gis.servelq.models.Counter;
import com.gis.servelq.models.CounterStatus;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Frees counters whose visitor hasn't rated within app.feedback-timeout.seconds
 * (30 by default), the same way the feedback app's idle timer does, so a counter
 * never stays on COMPLETE when the feedback screen is offline. Set the seconds
 * to 0 to turn it off.
 *
 * The 30 seconds are counted on this server's own clock from the moment this job
 * first sees the counter waiting on a given set of tokens, not from the tokens'
 * stored end times, so old unrated visits or a clock/time-zone difference with
 * the database can't free the counter early.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackTimeoutScheduler {

    private final CounterRepository counterRepository;
    private final TokenRepository tokenRepository;
    private final FeedbackService feedbackService;

    @Value("${app.feedback-timeout.seconds:30}")
    private long timeoutSeconds;

    /** counterId -> (tokens it is waiting on, when this job first saw that). */
    private final Map<String, Waiting> waiting = new ConcurrentHashMap<>();

    private record Waiting(String tokens, Instant since) {}

    @Scheduled(fixedDelayString = "${app.feedback-timeout.check-ms:5000}")
    public void expireStaleReviews() {
        if (timeoutSeconds <= 0) return;
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        Instant now = Instant.now();

        Set<String> seen = new HashSet<>();
        for (Counter counter : counterRepository.findByStatus(CounterStatus.COMPLETE)) {
            seen.add(counter.getId());
            String tokens = tokenRepository.findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(
                            List.of(TokenStatus.REVIEW), counter.getId())
                    .stream().map(Token::getId).sorted().collect(Collectors.joining(","));

            Waiting w = waiting.get(counter.getId());
            if (w == null || !w.tokens().equals(tokens)) {
                // Newly waiting, or another visitor finished: start the clock now.
                waiting.put(counter.getId(), new Waiting(tokens, now));
                continue;
            }
            if (Duration.between(w.since(), now).compareTo(timeout) < 0) continue;

            try {
                // Our own clock says the time is up, so don't re-check stored times.
                if (feedbackService.expireStaleReview(counter.getId(), Duration.ZERO)) {
                    log.info("No feedback within {}s at counter {}: token closed, counter freed",
                            timeoutSeconds, counter.getCode());
                }
                waiting.remove(counter.getId());
            } catch (Exception e) {
                log.error("Feedback timeout failed for counter {}", counter.getCode(), e);
            }
        }
        // Counters that left COMPLETE (rated, called next, ...) start fresh next time.
        waiting.keySet().retainAll(seen);
    }
}
