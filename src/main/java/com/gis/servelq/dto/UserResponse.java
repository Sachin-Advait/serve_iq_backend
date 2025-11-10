package com.gis.servelq.dto;

import com.gis.servelq.models.UserRole;
import lombok.Data;

@Data
public class UserResponse {
    private String id;
    private String username;
    private UserRole role;
    private String branchId;
}
