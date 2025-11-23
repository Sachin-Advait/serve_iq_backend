package com.gis.servelq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchRequest {
    @NotBlank(message = "Branch code is required")
    private String code;

    @NotBlank(message = "Branch name is required")
    private String name;

    private Boolean enabled = true;
}