package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Check-in / check-out record: one row per login of an agent, admin or receptionist.
 * logoutAt stays null while the session is open.
 */
@NoArgsConstructor
@Entity
@Table(name = "user_sessions")
@Data
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "branch_id")
    private String branchId;

    @Column(name = "counter_id")
    private String counterId;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Column(name = "logout_at")
    private LocalDateTime logoutAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "logout_type")
    private LogoutType logoutType;
}
