package com.gis.servelq.controllers;

import com.gis.servelq.dto.CounterRequest;
import com.gis.servelq.dto.CounterResponse;
import com.gis.servelq.models.CounterStatus;
import com.gis.servelq.services.CounterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/counters")
@RequiredArgsConstructor
public class CounterController {
    private final CounterService counterService;

    // Create a new counter
    @PostMapping
    public ResponseEntity<?> createCounter(@Valid @RequestBody CounterRequest request) {
        try {
            CounterResponse counter = counterService.createCounter(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get all counters
    @GetMapping
    public ResponseEntity<List<CounterResponse>> getAllCounters() {
        List<CounterResponse> counters = counterService.getAllCounters();
        return ResponseEntity.ok(counters);
    }

    // Get counters by branch
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<CounterResponse>> getCountersByBranch(@PathVariable String branchId) {
        List<CounterResponse> counters = counterService.getCountersByBranch(branchId);
        return ResponseEntity.ok(counters);
    }

    // Get counter by ID
    @GetMapping("/{counterId}")
    public ResponseEntity<CounterResponse> getCounterById(@PathVariable String counterId) {
        CounterResponse counter = counterService.getCounterById(counterId);
        return ResponseEntity.ok(counter);
    }

    // Update counter
    @PutMapping("/{counterId}")
    public ResponseEntity<?> updateCounter(
            @PathVariable String counterId,
            @Valid @RequestBody CounterRequest request) {
        try {
            CounterResponse counter = counterService.updateCounter(counterId, request);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Delete counter
    @DeleteMapping("/{counterId}")
    public ResponseEntity<?> deleteCounter(@PathVariable String counterId) {
        try {
            counterService.deleteCounter(counterId);
            return ResponseEntity.ok().body("Counter deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Enable counter
    @PatchMapping("/{counterId}/enable")
    public ResponseEntity<?> enableCounter(@PathVariable String counterId) {
        try {
            CounterResponse counter = counterService.toggleCounter(counterId, true);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Disable counter
    @PatchMapping("/{counterId}/disable")
    public ResponseEntity<?> disableCounter(@PathVariable String counterId) {
        try {
            CounterResponse counter = counterService.toggleCounter(counterId, false);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Pause counter
    @PatchMapping("/{counterId}/pause")
    public ResponseEntity<?> pauseCounter(@PathVariable String counterId) {
        try {
            CounterResponse counter = counterService.togglePauseCounter(counterId, true);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Resume counter
    @PatchMapping("/{counterId}/resume")
    public ResponseEntity<?> resumeCounter(@PathVariable String counterId) {
        try {
            CounterResponse counter = counterService.togglePauseCounter(counterId, false);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Update counter status
    @PatchMapping("/{counterId}/status")
    public ResponseEntity<?> updateCounterStatus(
            @PathVariable String counterId,
            @RequestParam CounterStatus status) {
        try {
            CounterResponse counter = counterService.updateCounterStatus(counterId, status);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}