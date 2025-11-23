package com.gis.servelq.dto;

import com.gis.servelq.models.ServiceModel;
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
    private String status;
    private List<ServiceResponseDTO> services;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
