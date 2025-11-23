package com.gis.servelq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CounterRequest {
    @NotBlank(message = "Counter code is required")
    private String code;

    @NotBlank(message = "Counter name is required")
    private String name;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    private Boolean enabled = true;
    private Boolean paused = false;
    private String status = "IDLE";

    private List<String> serviceIds = new ArrayList<>();
}