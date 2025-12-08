package com.gis.servelq.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BranchResponseDTO {
    private String id;
    private String code;
    private String name;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
