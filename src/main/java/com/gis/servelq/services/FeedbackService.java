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

        long positive = all.stream().filter(f -> f.getRating() >= 4).count();
        long neutral = all.stream().filter(f -> f.getRating() == 3).count();
        long negative = all.stream().filter(f -> f.getRating() <= 2).count();

        Map<String, Long> summary = new HashMap<>();
        summary.put("totalPositive", positive);
        summary.put("totalNeutral", neutral);
        summary.put("totalNegative", negative);

        return summary;
    }
}
