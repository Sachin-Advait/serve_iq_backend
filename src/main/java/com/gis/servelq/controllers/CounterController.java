package com.gis.servelq.controllers;

import com.gis.servelq.dto.CounterRequest;
import com.gis.servelq.dto.CounterResponseDTO;
import com.gis.servelq.dto.CounterUpdateRequest;
import com.gis.servelq.services.CounterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/serveiq/api/counters")
@RequiredArgsConstructor
public class CounterController {
    private final CounterService counterService;

    @PostMapping
    public ResponseEntity<?> createCounter(@Valid @RequestBody CounterRequest request) {
        try {
            CounterResponseDTO counter = counterService.createCounter(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<CounterResponseDTO>> getAllCounters() {
        List<CounterResponseDTO> counters = counterService.getAllCounters();
        return ResponseEntity.ok(counters);
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<CounterResponseDTO>> getCountersByBranch(@PathVariable String branchId) {
        List<CounterResponseDTO> counters = counterService.getCountersByBranch(branchId);
        return ResponseEntity.ok(counters);
    }

    @GetMapping("/{counterId}")
    public ResponseEntity<CounterResponseDTO> getCounterById(@PathVariable String counterId) {
        CounterResponseDTO counter = counterService.getCounterById(counterId);
        return ResponseEntity.ok(counter);
    }

    @PutMapping("/{counterId}")
    public ResponseEntity<?> updateCounter(
            @PathVariable String counterId,
            @RequestBody CounterUpdateRequest request) {
        try {
            CounterResponseDTO counter = counterService.updateCounter(counterId, request);
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

    @PatchMapping("/enabled/{counterId}/{value}")
    public ResponseEntity<?> toggleEnabled(
            @PathVariable String counterId,
            @PathVariable boolean value) {
        try {
            CounterResponseDTO counter = counterService.toggleCounter(counterId, value);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/paused/{counterId}/{value}")
    public ResponseEntity<?> togglePaused(
            @PathVariable String counterId,
            @PathVariable boolean value) {
        try {
            CounterResponseDTO counter = counterService.togglePauseCounter(counterId, value);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Get full counter status with token info
    @GetMapping("/status/details/{counterId}")
    public ResponseEntity<?> getCounterStatusDetails(@PathVariable String counterId) {
        try {
            return ResponseEntity.ok(counterService.getCounterStatusDetails(counterId));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}