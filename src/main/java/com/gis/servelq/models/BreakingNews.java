package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "breaking_news")
@Data
public class BreakingNews {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String link;
    private String source;
    private String category;
    private String type; // MANUAL or RSS

    @Column(name = "published_date")
    private Instant publishedDate;

    private Boolean published = false;
    private Boolean active = true;
    private Boolean archived = false;

    @Column(name = "separator_image")
    private String separatorImage;

    @Column(name = "separator_image_url")
    private String separatorImageUrl;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}