package com.gis.servelq.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ServiceRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    private String arabicName;

    private String parentId;
    private Integer slaSec = 900;
    private Boolean enabled = true;

    @NotBlank
    private String branchId;
}
