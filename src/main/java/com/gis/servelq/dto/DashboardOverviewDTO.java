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
public class DashboardOverviewDTO {
    private List<KpiDTO> kpis;
    private List<VisitorFlowDTO> visitorFlow;
    private List<ServiceTrendDTO> serviceTrends;
    private List<AiRecommendationDTO> aiRecommendations;
}