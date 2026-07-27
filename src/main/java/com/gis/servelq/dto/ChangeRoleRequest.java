package com.gis.servelq.dto;

import com.gis.servelq.models.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeRoleRequest {

    @NotNull(message = "role is required")
    private UserRole role;
}
