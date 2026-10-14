package com.gis.servelq.dto;

import com.gis.servelq.models.LogoutType;
import com.gis.servelq.models.UserRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** One check-in / check-out row for the admin panel's login history. */
@Getter
@Builder
public class UserSessionDTO {
    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private UserRole role;
    private String branchId;
    private String counterId;
    private String counterName;
    private LocalDateTime loginAt;
    private LocalDateTime logoutAt;
    /** Null while the session is still open. */
    private LogoutType logoutType;
    /** Minutes between check-in and check-out; null while open. */
    private Long durationMinutes;
}
