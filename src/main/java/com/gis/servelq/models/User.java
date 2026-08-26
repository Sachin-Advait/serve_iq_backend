package com.gis.servelq.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(unique = true)
    private String email;
    private String name;
    @NotBlank
    private String password;

    @Enumerated(EnumType.STRING)
    @NotNull
    private UserRole role = UserRole.USER;

    @Column(name = "branch_id")
    private String branchId;

    @Column(name = "counter_id")
    private String counterId;

    @Column(name = "fcm_token")
    private String fcmToken = null;

    @Column(name = "is_active")
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

