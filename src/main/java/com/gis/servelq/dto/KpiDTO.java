package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiDTO {
    private String icon;
    private String label;
    private String value;
    private String change;
    private Boolean positive;
    private Boolean neutral;
}