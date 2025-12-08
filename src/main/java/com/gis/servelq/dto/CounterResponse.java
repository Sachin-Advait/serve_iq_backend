package com.gis.servelq.dto;

import com.gis.servelq.models.CounterStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CounterResponse {
    private String id;
    private String code;
    private String name;
    private BranchResponse branch;
    private Boolean enabled;
    private Boolean paused;
    private CounterStatus status;
    private String userId;
    private String username;
    private List<ServiceResponseDTO> services;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
