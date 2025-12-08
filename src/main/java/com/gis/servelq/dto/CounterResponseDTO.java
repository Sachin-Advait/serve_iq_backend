package com.gis.servelq.dto;

import com.gis.servelq.models.CounterStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CounterResponseDTO {
    private String id;
    private String code;
    private String name;
    private BranchResponseDTO branch;
    private Boolean enabled;
    private Boolean paused;
    private CounterStatus status;
    private String userId;
    private String username;
    private ServiceResponseDTO service;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
