package com.gis.servelq.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
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
@Table(name = "services")
@Data
public class ServiceModel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    private String arabicName;

    @Column(name = "parent_id")
    private String parentId;

    // Prevent recursive serialization of parent <-> child
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    @JsonBackReference
    private ServiceModel parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ServiceModel> children = new ArrayList<>();

    @Column(name = "sla_sec")
    private Integer slaSec = 900;

    @NotNull
    private Boolean enabled = true;

    @NotBlank
    @Column(name = "branch_id")
    private String branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    @JsonIgnore
    private Branch branch;

    @ManyToMany(mappedBy = "services", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Counter> counters = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Token> tokens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
