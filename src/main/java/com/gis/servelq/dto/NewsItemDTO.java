package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItemDTO {
    private String title;
    private String description;
    private String link;
    private String source;
    private String category;
    private String publishedDate;
}