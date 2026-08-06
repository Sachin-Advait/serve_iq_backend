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
public class CounterUtilizationDTO {
    private String branchName;
    private int totalCounters;
    private int activeCounters;
    private int idleCounters;
    private int pausedCounters;
    private double overallUtilizationRate;
    private List<CounterUtilizationDetailDTO> counters;
}

