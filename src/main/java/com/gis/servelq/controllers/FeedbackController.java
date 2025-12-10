package com.gis.servelq.controllers;

import com.gis.servelq.models.Feedback;
import com.gis.servelq.services.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/serveiq/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService service;

    @PostMapping
    public Feedback submitFeedback(@RequestBody Feedback feedback) {
        return service.createFeedback(feedback);
    }

    @GetMapping
    public List<Feedback> getAllFeedback() {
        return service.getAll();
    }

    @DeleteMapping("/{id}")
    public void deleteFeedback(@PathVariable String id) {
        service.delete(id);
    }
    @GetMapping("/summary")
    public Map<String, Long> getSummary() {
        return service.getFeedbackSummary();
    }
}
