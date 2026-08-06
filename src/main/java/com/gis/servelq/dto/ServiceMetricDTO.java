package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceMetricDTO {
    private String serviceId;
    private String serviceName;
    private long totalTokens;
    private long completedTokens;
    private double averageServiceTime;
    private double averageWaitTime;
    private double completionRate;
}
