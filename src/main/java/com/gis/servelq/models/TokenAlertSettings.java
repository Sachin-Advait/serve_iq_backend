package com.gis.servelq.models;

import com.gis.servelq.utils.StringListConverter;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Waiting-token alert mail settings from the admin panel's System page; one
 * row, id 1. When a branch's waiting tokens reach waitingThreshold, active
 * users with one of the roles are e-mailed.
 */
@Entity
@Table(name = "token_alert_settings")
@Data
public class TokenAlertSettings {
    public static final int SINGLETON_ID = 1;
    public static final int DEFAULT_THRESHOLD = 20;

    @Id
    private Integer id = SINGLETON_ID;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "waiting_threshold", nullable = false)
    private Integer waitingThreshold = DEFAULT_THRESHOLD;

    @Convert(converter = StringListConverter.class)
    @Column(name = "roles")
    private List<String> roles = new ArrayList<>(List.of(UserRole.ADMIN.name()));

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
