package com.gis.servelq.models;

import com.gis.servelq.utils.StringListConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

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

    @NotNull
    @Column(name = "branch_id")
    private String branchId;

    @Column(name = "user_id")
    private String userId;

    // First of serviceIds, kept for clients that only know one service per counter.
    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "service_ids")
    @Convert(converter = StringListConverter.class)
    private List<String> serviceIds;

    @Enumerated(EnumType.STRING)
    private CounterStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}