package com.gis.servelq.controllers;

import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * Get all completed (serving) tokens with optional filters
     * Example: /api/reports/serving?branchId=BR001&counterId=1&serviceId=S001&date=2025-12-08
     */
    @GetMapping("/served-tokens")
    public List<TokenResponseDTO> getServingTokens(
            @RequestParam String branchId,
            @RequestParam(required = false) String counterId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reportService.getServingReport(branchId, counterId, serviceId, date);
    }

    /**
     * Get all waiting tokens with optional filters
     * Example: /api/reports/waiting?branchId=BR001&counterId=1&serviceId=S001&date=2025-12-08
     */
    @GetMapping("/waited-tokens")
    public List<TokenResponseDTO> getWaitingTokens(
            @RequestParam String branchId,
            @RequestParam(required = false) String counterId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reportService.getWaitingReport(branchId, counterId, serviceId, date);
    }

    /**
     * Get all transferred tokens with optional filters
     * Example: /api/reports/transferred?branchId=BR001&counterId=1&serviceId=S001&date=2025-12-08
     */
    @GetMapping("/transferred")
    public List<TokenResponseDTO> getTransferredTokens(
            @RequestParam String branchId,
            @RequestParam(required = false) String counterId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reportService.getTransferredTokensReport(branchId, counterId, serviceId, date);
    }

}
