package com.gis.servelq.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRequest {
    @NotBlank
    private String serviceId;

    @NotBlank
    private String branchId;

    private String visitorName;
    private String phone;
    private String civilId;
    private String purpose;
    private Integer priority = 100;
}
