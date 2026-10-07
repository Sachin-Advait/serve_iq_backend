package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import lombok.Data;

import java.time.LocalDateTime;

@Data

public class UserResponseDTO {
    private String id;
    private String name;
    private String email;
    private UserRole role;
    private String branchId;
    private String counterId;
    // Device push token. Written from the login/patch payloads but not sent back
    // out - GET /users returned it for every user, which is enough to push
    // notifications to someone else's device.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String fcmToken;
    @JsonProperty("active")
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UserResponseDTO(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.branchId = user.getBranchId();
        if (user.getCounterId() != null) this.counterId = user.getCounterId();
        this.fcmToken = user.getFcmToken();
        this.active = user.getActive();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }

    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(user);
    }
}
