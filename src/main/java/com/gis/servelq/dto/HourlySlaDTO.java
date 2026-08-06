package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourlySlaDTO {
    private String hour;
    private long totalTokens;
    private long compliantTokens;
    private double complianceRate;
}
