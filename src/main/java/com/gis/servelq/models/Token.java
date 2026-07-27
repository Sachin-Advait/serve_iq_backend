package com.gis.servelq.models;

import com.gis.servelq.utils.StringListConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tokens",
        uniqueConstraints = {
                // The sequence is derived with MAX(token_seq)+1 and then
                // inserted, which is a read-modify-write. Two kiosks issuing at
                // the same instant both read the same max and both used to
                // succeed, handing two customers the same number. This is what
                // makes the loser fail so the retry can pick the next number.
                @UniqueConstraint(name = "uk_tokens_branch_priority_seq_date",
                        columnNames = {"branch_id", "priority", "token_seq", "token_date"})
        },
        indexes = {
                @Index(name = "ix_tokens_branch_status_priority_created",
                        columnList = "branch_id, status, priority, created_at"),
                @Index(name = "ix_tokens_counter_status", columnList = "assigned_counter_id, status"),
                @Index(name = "ix_tokens_branch_created", columnList = "branch_id, created_at")
        })
@Data
public class Token {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    private String token;

    @NotNull
    @Column(name = "token_seq")
    private Integer tokenSeq;

    /**
     * Business day the token belongs to. The sequence restarts each day, so the
     * uniqueness constraint needs a day to scope itself to. Derived from
     * createdAt but stored separately so the constraint can use it.
     */
    @NotNull
    @Column(name = "token_date")
    private LocalDate tokenDate;

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

    @NotBlank
    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "assigned_counter_id")
    private String assignedCounterId;

    @Column(name = "assigned_counter_name")
    private String assignedCounterName;

    @Column(name = "counter_ids")
    @Convert(converter = StringListConverter.class)
    private List<String> counterIds;

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