package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiDashboardDTO {
    private String systemCode;
    private String requestedFrequency;
    private Instant serverTime;
    private List<AnalyticsRecordDTO> records;
}