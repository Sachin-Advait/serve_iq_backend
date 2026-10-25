package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One pause of a counter (an agent's break). endedAt stays null until the
 * counter is resumed or released.
 */
@NoArgsConstructor
@Entity
@Table(name = "counter_breaks")
@Data
public class CounterBreak {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "counter_id", nullable = false)
    private String counterId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "branch_id")
    private String branchId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
