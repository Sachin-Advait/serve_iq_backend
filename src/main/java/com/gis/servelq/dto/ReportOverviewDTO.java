package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportOverviewDTO {
    private String reportType;
    private String title;
    private String description;
    private String icon;
    private String color;
    private boolean hasData;
}