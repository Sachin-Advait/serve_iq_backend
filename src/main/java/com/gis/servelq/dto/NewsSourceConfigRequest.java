package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsSourceConfigRequest {
    private String sourceName;
    private String rssUrl;
    private String category;
    private Boolean active;
    private Integer maxItems;
}