package com.gis.servelq.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsRecordDTO {
    private String systemCode;
    private String useCaseCode;
    private String frequency;
    private Instant generatedAt;
    private Instant validFrom;
    private Instant validTo;
    private String status;
    private ModelInfoDTO model;
    private JsonNode summary;
    private JsonNode chart;
    private JsonNode alerts;
    private JsonNode details;
    private boolean stale;
}