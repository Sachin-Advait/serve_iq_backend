package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationDTO {
    private String counter;
    private String action;
    private String reason;
    private String impact;
    private int confidence;
}