package com.gis.servelq.controllers;

import com.gis.servelq.dto.*;
import com.gis.servelq.services.AiForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/serveiq/api/ai/forecasting")
@RequiredArgsConstructor
public class AiForecastController {

    private final AiForecastService service;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponseDTO<AiDashboardDTO>> dashboard(
            @RequestParam(required = false) String frequency) {
        return ok("Dashboard fetched", service.dashboard(frequency));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponseDTO<List<AnalyticsRecordDTO>>> current(
            @RequestParam(required = false) String frequency) {
        return ok("Current forecasts fetched", service.current(frequency));
    }

    @GetMapping("/current/{useCaseCode}")
    public ResponseEntity<ApiResponseDTO<AnalyticsRecordDTO>> currentOne(
            @PathVariable String useCaseCode,
            @RequestParam(required = false) String frequency) {
        return ok("Current forecast fetched", service.currentOne(useCaseCode, frequency));
    }

    @GetMapping("/history/{useCaseCode}")
    public ResponseEntity<ApiResponseDTO<List<AnalyticsRecordDTO>>> history(
            @PathVariable String useCaseCode,
            @RequestParam(required = false) String frequency,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit) {
        return ok("History fetched",
                service.history(useCaseCode, frequency, from, to, limit));
    }

    @GetMapping("/data-health")
    public ResponseEntity<ApiResponseDTO<DataHealthDTO>> dataHealth() {
        return ok("Data health fetched", service.health());
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> ok(String message, T data) {
        return ResponseEntity.ok(new ApiResponseDTO<>(true, message, data));
    }
}