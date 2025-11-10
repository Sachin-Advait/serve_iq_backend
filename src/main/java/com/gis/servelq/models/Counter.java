package com.gis.servelq.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @NotBlank
    @Column(name = "branch_id")
    private String branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    private Branch branch;

    @NotNull
    private Boolean enabled = true;

    @NotNull
    private Boolean paused = false;

    private String status = "IDLE";

    @ManyToMany
    @JoinTable(
            name = "counter_services",
            joinColumns = @JoinColumn(name = "counter_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id")
    )
    private List<ServiceModel> services = new ArrayList<>(); // Changed back to 'services'

    @OneToMany(mappedBy = "counter", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Token> tokens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}