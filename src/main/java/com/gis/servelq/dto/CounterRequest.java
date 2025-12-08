package com.gis.servelq.dto;

import com.gis.servelq.models.CounterStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CounterRequest {
    @NotBlank(message = "Counter code is required")
    private String code;

    @NotBlank(message = "Counter name is required")
    private String name;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    private String userId;
    private Boolean enabled;
    private Boolean paused;
    private CounterStatus status;
    private String serviceId;
}