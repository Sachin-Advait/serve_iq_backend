package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaComplianceDTO {
    private String branchName;
    private double overallSlaRate;
    private long totalTokens;
    private long slaCompliantTokens;
    private long slaBreachedTokens;
    private int slaTargetMinutes;
    private List<HourlySlaDTO> hourlyBreakdown;
}

