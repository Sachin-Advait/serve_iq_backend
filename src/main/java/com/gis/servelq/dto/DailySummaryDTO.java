package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailySummaryDTO {
    private LocalDate date;
    private String branchName;
    private long totalTokensGenerated;
    private long tokensServed;
    private long tokensWaiting;
    private long tokensNoShow;
    private long tokensTransferred;
    private double averageWaitTimeMinutes;
    private double averageServiceTimeMinutes;
    private double slaComplianceRate;
    private String peakHour;
    private long peakHourTokens;
    private Map<String, Long> tokensByService;
    private Map<String, Long> tokensByStatus;
}