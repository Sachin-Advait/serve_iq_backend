package com.gis.servelq.controllers;

import com.gis.servelq.dto.*;
import com.gis.servelq.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/serveiq/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ==================== TOKEN REPORTS (Existing) ====================

    /**
     * Get all completed (serving) tokens with optional filters
     * Example: /api/reports/served-tokens?branchId=BR001&counterId=1&serviceId=S001&date=2025-12-08
     */
    @GetMapping("/served-tokens")
    public ResponseEntity<ApiResponseDTO<List<TokenResponseDTO>>> getServingTokens(
            @RequestParam String branchId,
            @RequestParam(required = false) String counterId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<TokenResponseDTO> tokens = reportService.getServingReport(branchId, counterId, serviceId, date);
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Served tokens retrieved successfully", tokens));
    }

    /**
     * Get all waiting tokens with optional filters
     * Example: /api/reports/waited-tokens?branchId=BR001&counterId=1&serviceId=S001&date=2025-12-08
     */
    @GetMapping("/waited-tokens")
    public ResponseEntity<ApiResponseDTO<List<TokenResponseDTO>>> getWaitingTokens(
            @RequestParam String branchId,
            @RequestParam(required = false) String counterId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<TokenResponseDTO> tokens = reportService.getWaitingReport(branchId, counterId, serviceId, date);
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Waiting tokens retrieved successfully", tokens));
    }

    /**
     * Get all transferred tokens with optional filters
     * Example: /api/reports/transferred?branchId=BR001&counterId=1&serviceId=S001&date=2025-12-08
     */
    @GetMapping("/transferred")
    public ResponseEntity<ApiResponseDTO<List<TokenResponseDTO>>> getTransferredTokens(
            @RequestParam String branchId,
            @RequestParam(required = false) String counterId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<TokenResponseDTO> tokens = reportService.getTransferredTokensReport(branchId, counterId, serviceId, date);
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Transferred tokens retrieved successfully", tokens));
    }

    // ==================== DASHBOARD ANALYTICS REPORTS (New) ====================

    /**
     * Get available reports overview
     */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponseDTO<List<ReportOverviewDTO>>> getReportsOverview(
            @RequestParam String branchId) {
        return ResponseEntity.ok(new ApiResponseDTO<>(true,
                "Reports overview retrieved",
                reportService.getReportsOverview(branchId)));
    }

    /**
     * Generate Daily Summary Report
     * Example: /api/reports/daily-summary?branchId=BR001&date=2026-08-06
     */
    @GetMapping("/daily-summary")
    public ResponseEntity<ApiResponseDTO<DailySummaryDTO>> getDailySummary(
            @RequestParam String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        DailySummaryDTO summary = reportService.getDailySummary(branchId, date);
        return ResponseEntity.ok(new ApiResponseDTO<>(true,
                "Daily summary generated successfully", summary));
    }

    /**
     * Generate Service Analytics Report
     * Example: /api/reports/service-analytics?branchId=BR001&date=2026-08-06
     */
    @GetMapping("/service-analytics")
    public ResponseEntity<ApiResponseDTO<ServiceAnalyticsDTO>> getServiceAnalytics(
            @RequestParam String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        ServiceAnalyticsDTO analytics = reportService.getServiceAnalytics(branchId, date);
        return ResponseEntity.ok(new ApiResponseDTO<>(true,
                "Service analytics generated successfully", analytics));
    }

    /**
     * Generate SLA Compliance Report
     * Example: /api/reports/sla-compliance?branchId=BR001&date=2026-08-06
     */
    @GetMapping("/sla-compliance")
    public ResponseEntity<ApiResponseDTO<SlaComplianceDTO>> getSlaCompliance(
            @RequestParam String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        SlaComplianceDTO compliance = reportService.getSlaCompliance(branchId, date);
        return ResponseEntity.ok(new ApiResponseDTO<>(true,
                "SLA compliance report generated successfully", compliance));
    }

    /**
     * Generate Counter Utilization Report
     * Example: /api/reports/counter-utilization?branchId=BR001&date=2026-08-06
     */
    @GetMapping("/counter-utilization")
    public ResponseEntity<ApiResponseDTO<CounterUtilizationDTO>> getCounterUtilization(
            @RequestParam String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        CounterUtilizationDTO utilization = reportService.getCounterUtilization(branchId, date);
        return ResponseEntity.ok(new ApiResponseDTO<>(true,
                "Counter utilization report generated successfully", utilization));
    }
}