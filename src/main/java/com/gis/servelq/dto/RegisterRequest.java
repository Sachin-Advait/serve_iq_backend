package com.gis.servelq.dto;


import com.gis.servelq.models.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotNull
    private UserRole role = UserRole.USER;

    private String branchId;
}

