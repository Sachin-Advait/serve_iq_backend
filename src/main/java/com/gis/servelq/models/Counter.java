package com.gis.servelq.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "counters")
@Data
public class Counter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    private Boolean enabled = true;

    @NotNull
    private Boolean paused = false;

    @Column(name = "branch_id")
    private String branchId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "service_id")
    private String serviceId;

    private CounterStatus status = CounterStatus.IDLE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
