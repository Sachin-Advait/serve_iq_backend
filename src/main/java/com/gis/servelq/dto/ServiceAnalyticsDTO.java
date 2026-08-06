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
public class ServiceAnalyticsDTO {
    private String branchName;
    private List<ServiceMetricDTO> services;
    private TotalServiceStatsDTO totals;
}

