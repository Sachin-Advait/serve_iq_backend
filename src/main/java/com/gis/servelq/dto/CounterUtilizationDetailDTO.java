package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterUtilizationDetailDTO {
    private String counterId;
    private String counterCode;
    private String counterName;
    private String agentName;
    private long tokensServed;
    private double averageServiceTime;
    private double utilizationRate;
    private String status;
}
