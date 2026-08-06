package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalServiceStatsDTO {
    private long totalTokens;
    private long totalCompleted;
    private double overallAverageServiceTime;
    private double overallCompletionRate;
}
