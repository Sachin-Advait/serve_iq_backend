package com.gis.servelq.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModelInfoDTO {
    private String name;
    private String version;
    private JsonNode metrics;
}