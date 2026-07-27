package com.gis.servelq.controllers;

import com.gis.servelq.dto.FeedbackRequestDto;
import com.gis.servelq.models.Feedback;
import com.gis.servelq.services.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/serveiq/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService service;

    @PostMapping
    public Feedback submitFeedback(@RequestBody FeedbackRequestDto dto) {

        Feedback f = new Feedback();
        f.setTokenId(dto.getTokenId());
        f.setCounterCode(dto.getCounterCode());
        f.setAgentName(dto.getAgentName());
        f.setRating(dto.getRating());
        f.setReview(dto.getReview());

        return service.createFeedback(f);
    }

    @GetMapping
    public Page<Feedback> getAllFeedback(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.getAll(pageable);
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
