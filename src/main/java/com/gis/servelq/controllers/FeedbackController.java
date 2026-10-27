package com.gis.servelq.controllers;

import com.gis.servelq.dto.FeedbackRequestDto;
import com.gis.servelq.models.Feedback;
import com.gis.servelq.security.AuthenticatedUser;
import com.gis.servelq.services.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/serveiq/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService service;

    // ==================== SUBMIT ====================

    @PostMapping
    public Feedback submitFeedback(@RequestBody FeedbackRequestDto dto,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        Feedback f = new Feedback();
        f.setTokenId(dto.getTokenId());
        f.setCounterCode(dto.getCounterCode());
        f.setRating(dto.getRating());
        f.setReview(dto.getReview());

        return service.createFeedback(f, user);
    }

    // ==================== TIMEOUT (no rating given) ====================

    @PostMapping("/timeout")
    public void feedbackTimeout(@RequestBody FeedbackRequestDto dto) {
        service.expireFeedback(dto.getTokenId(), dto.getCounterCode());
    }

    // ==================== LIST (with optional date filter) ====================

    /**
     * GET /serveiq/api/feedback
     * GET /serveiq/api/feedback?from=2026-09-01&to=2026-09-27
     *
     * GET /serveiq/api/feedback?agentId=...
     *
     * `from` / `to` are inclusive dates (YYYY-MM-DD).
     * Omit them → no date filtering (return everything).
     * `agentId` → only feedback for tickets that agent served.
     * `rating`  → HAPPY, NEUTRAL or SAD only.
     * `search`  → text in the comment, agent name, counter, ticket number or service.
     */
    @GetMapping
    public Page<Feedback> getAllFeedback(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            @RequestParam(required = false) String agentId,

            @RequestParam(required = false) Feedback.MoodRating rating,

            @RequestParam(required = false) String search,

            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        LocalDateTime start = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime end   = (to   != null) ? to.plusDays(1).atStartOfDay() : null;

        return service.getAll(start, end, agentId, rating, search, pageable);
    }

    // ==================== DELETE ====================

    @DeleteMapping("/{id}")
    public void deleteFeedback(@PathVariable String id,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(id, user);
    }

    // ==================== SUMMARY (all-time or a date range) ====================

    /**
     * GET /serveiq/api/feedback/summary                              → all-time totals
     * GET /serveiq/api/feedback/summary?from=2026-10-01&to=2026-10-31 → totals in that range
     *
     * `from` / `to` are inclusive dates (YYYY-MM-DD).
     */
    @GetMapping("/summary")
    public Map<String, Long> getSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDateTime start = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime end   = (to   != null) ? to.plusDays(1).atStartOfDay() : null;

        return service.getFeedbackSummary(start, end);
    }
}