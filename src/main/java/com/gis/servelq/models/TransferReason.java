package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Why an agent moved a token to another counter (queue procedure 9.g). The
 * admin manages the list; tokens store the code, which never changes after
 * the reason is created so old transfers keep their label.
 */
@Entity
@Table(name = "transfer_reasons")
@Data
public class TransferReason {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, updatable = false, length = 50)
    private String code;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "title_ar", nullable = false)
    private String titleAr;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
