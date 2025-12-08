package com.gis.servelq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class TokenRequest {
    @NotBlank
    private String serviceId;

    @NotBlank
    private String branchId;
    private String mobileNumber;
    private List<String> counterIds;
    private Integer priority;
}
