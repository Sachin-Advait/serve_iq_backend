package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_source_config")
@Data
public class NewsSourceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "source_name", unique = true)
    private String sourceName;

    @Column(name = "rss_url")
    private String rssUrl;

    private String category;
    private Boolean active = true;

    @Column(name = "max_items")
    private Integer maxItems = 5;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}