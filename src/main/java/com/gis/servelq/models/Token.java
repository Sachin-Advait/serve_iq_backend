package com.gis.servelq.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokens")
@Data
public class Token {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    private String token;

    @NotBlank
    @Column(name = "branch_id")
    private String branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    private Branch branch;

    @NotBlank
    @Column(name = "service_id")
    private String serviceId;

    // Change this from serviceModel to service
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", insertable = false, updatable = false)
    private ServiceModel service;

    @NotNull
    private Integer priority = 100;

    @Enumerated(EnumType.STRING)
    @NotNull
    private TokenStatus status = TokenStatus.WAITING;

    @Column(name = "civil_id")
    private String civilId;

    @Column(name = "counter_id")
    private String counterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counter_id", insertable = false, updatable = false)
    private Counter counter;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "appt_time")
    private LocalDateTime apptTime;

    @Column(name = "no_show_count")
    private Integer noShowCount = 0;
}