package com.gis.servelq.controllers;

import com.gis.servelq.dto.ApiResponseDTO;
import com.gis.servelq.dto.DashboardOverviewDTO;
import com.gis.servelq.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/serveiq/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Get complete dashboard overview data
     * Example: /api/dashboard/overview?branchId=BR001&date=2026-08-06
     */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponseDTO<DashboardOverviewDTO>> getDashboardOverview(
            @RequestParam String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        DashboardOverviewDTO overview = dashboardService.getDashboardOverview(branchId, date);
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Dashboard data retrieved successfully", overview));
    }

    /**
     * Get KPI data only
     */
    @GetMapping("/kpis")
    public ResponseEntity<ApiResponseDTO<?>> getKpis(
            @RequestParam String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(new ApiResponseDTO<>(true, "KPIs retrieved successfully",
                dashboardService.getKpis(branchId, date)));
    }

    /**
     * Get visitor flow data
     */
    @GetMapping("/visitor-flow")
    public ResponseEntity<ApiResponseDTO<?>> getVisitorFlow(
            @RequestParam String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Visitor flow data retrieved",
                dashboardService.getVisitorFlow(branchId, date)));
    }

    /**
     * Get service time trends
     */
    @GetMapping("/service-trends")
    public ResponseEntity<ApiResponseDTO<?>> getServiceTrends(
            @RequestParam String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Service trends retrieved",
                dashboardService.getServiceTrends(branchId, date)));
    }

    /**
     * Get AI recommendations
     */
    @GetMapping("/ai-recommendations")
    public ResponseEntity<ApiResponseDTO<?>> getAiRecommendations(
            @RequestParam String branchId) {

        return ResponseEntity.ok(new ApiResponseDTO<>(true, "AI recommendations retrieved",
                dashboardService.getAiRecommendations(branchId)));
    }
}