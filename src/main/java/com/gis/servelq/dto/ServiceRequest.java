package com.gis.servelq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class ServiceRequest {
    // Required for a sub-service; a main service's code is filled automatically
    private String code;
    private String arabicCode;

    @NotBlank
    private String name;

    @NotBlank
    private String arabicName;
    private String description;
    private String arabicDescription;
    private String parentId;
    private List<String> counterIds;
    private Boolean enabled = true;
    @Positive
    private Integer slaSeconds;
    private Integer servingSlaSeconds;
    @NotBlank
    private String branchId;
}
