package com.gis.servelq.models;

import com.gis.servelq.utils.StringListConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

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

    @NotBlank
    @Column(name = "service_id")
    private String serviceId;

    @NotBlank
    @Column(name = "service_name")
    private String serviceName;

    @NotNull
    private Integer priority = 50;

    @Enumerated(EnumType.STRING)
    @NotNull
    private TokenStatus status = TokenStatus.WAITING;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "assigned_counter_id")
    private String assignedCounterId;

    @Column(name = "assigned_counter_name")
    private String assignedCounterName;

    @Column(name = "counter_ids")
    @Convert(converter = StringListConverter.class)
    private List<String> counterId;

    private Boolean isTransfer = false;
    private String transferFrom;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;
}