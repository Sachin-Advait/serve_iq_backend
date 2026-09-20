package com.gis.servelq.controllers;

import com.gis.servelq.dto.CounterDisplayDTO;
import com.gis.servelq.dto.CounterRequest;
import com.gis.servelq.dto.CounterResponseDTO;
import com.gis.servelq.dto.CounterUpdateRequest;
import com.gis.servelq.security.AuthenticatedUser;
import com.gis.servelq.services.CounterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/serveiq/api/counters")
@RequiredArgsConstructor
public class CounterController {
    private final CounterService counterService;

    @PostMapping
    public ResponseEntity<?> createCounter(@Valid @RequestBody CounterRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            CounterResponseDTO counter = counterService.createCounter(request, user);
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
    public ResponseEntity<?> updateCounter(@PathVariable String counterId,
                                           @RequestBody CounterUpdateRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            CounterResponseDTO counter = counterService.updateCounter(counterId, request, user);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{counterId}")
    public ResponseEntity<?> deleteCounter(@PathVariable String counterId,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            counterService.deleteCounter(counterId, user);
            return ResponseEntity.ok().body("Counter deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/enabled/{counterId}/{value}")
    public ResponseEntity<?> toggleEnabled(@PathVariable String counterId,
                                           @PathVariable boolean value,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            CounterResponseDTO counter = counterService.toggleCounter(counterId, value, user);
            return ResponseEntity.ok(counter);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/paused/{counterId}/{value}")
    public ResponseEntity<?> togglePaused(@PathVariable String counterId,
                                          @PathVariable boolean value,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            CounterResponseDTO counter = counterService.togglePauseCounter(counterId, value, user);
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

    // TV/display-board feed: token, counter, service, status for every counter in a branch
    @GetMapping("/display-board/branch/{branchId}")
    public ResponseEntity<List<CounterDisplayDTO>> getCounterDisplayBoard(@PathVariable String branchId) {
        return ResponseEntity.ok(counterService.getCounterDisplayBoard(branchId));
    }

    @PostMapping("/{counterId}/release")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<?> release(@PathVariable String counterId,
                                     @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(counterService.forceReleaseCounter(counterId, admin));
    }
}