package com.gis.servelq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRequest {
    @NotBlank
    private String serviceId;

    @NotBlank
    private String branchId;
    private String civilId;
    private String counterId;
    private Integer priority = 100;
}
