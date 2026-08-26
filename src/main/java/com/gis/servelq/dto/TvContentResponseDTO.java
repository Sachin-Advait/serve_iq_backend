package com.gis.servelq.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TvContentResponseDTO {
    private String id;
    private String branchId;
    private String name;
    private String url;          // Full URL
    private String type;
    private Boolean active;
    private String size;
    private Boolean archived;
    private String hlsUrl;       // Full HLS URL
    private Boolean hlsProcessed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}