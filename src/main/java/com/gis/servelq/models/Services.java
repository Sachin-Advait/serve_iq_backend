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
@Table(name = "services")
@Data
public class Services {
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

    @NotNull
    private Boolean enabled = true;

    @NotNull
    @Column(name = "counter_ids")
    @Convert(converter = StringListConverter.class)
    private List<String> counterIds;

    @Column(name = "children_ids")
    @Convert(converter = StringListConverter.class)
    private List<String> children;

    @NotBlank
    @Column(name = "branch_id")
    private String branchId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
