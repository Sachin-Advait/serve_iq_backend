package com.gis.servelq.services;

import com.gis.servelq.models.Feedback;
import com.gis.servelq.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository repo;

    public Feedback createFeedback(Feedback f) {
        return repo.save(f);
    }

    public List<Feedback> getAll() {
        return repo.findAll();
    }

    public void delete(String id) {
        repo.deleteById(id);
    }

    public Map<String, Long> getFeedbackSummary() {
        List<Feedback> all = repo.findAll();

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
