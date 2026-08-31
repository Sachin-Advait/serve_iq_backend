package com.gis.servelq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TokenRequest {
    @NotBlank
    private String serviceId;
    @NotBlank
    private String branchId;
    @NotNull
    private String mobileNumber;
    @NotNull
    private Boolean greenToken;
    private Integer priority = 50;

    @NotNull
    private String language;
}
