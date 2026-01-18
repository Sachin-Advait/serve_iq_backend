package com.gis.servelq.services;

import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.FeedbackRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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


    @Transactional
    public Feedback createFeedback(Feedback feedback) {
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
        return saved;
    }

    public List<Feedback> getAll() {
        return feedbackRepository.findAll();
    }

    public void delete(String id) {
        feedbackRepository.deleteById(id);
    }

    public Map<String, Long> getFeedbackSummary() {
        List<Feedback> all = feedbackRepository.findAll();

        long happy = all.stream()
                .filter(f -> f.getRating() == Feedback.MoodRating.HAPPY)
                .count();

        long neutral = all.stream()
                .filter(f -> f.getRating() == Feedback.MoodRating.NEUTRAL)
                .count();

        long sad = all.stream()
                .filter(f -> f.getRating() == Feedback.MoodRating.SAD)
                .count();

        Map<String, Long> summary = new HashMap<>();
        summary.put("totalHappy", happy);
        summary.put("totalNeutral", neutral);
        summary.put("totalSad", sad);

        return summary;
    }
}
