package com.gis.servelq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ServiceRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    private String arabicName;
    private String parentId;
    private List<String> counterIds;
    private Boolean enabled = true;
    @NotBlank
    private String branchId;
}
