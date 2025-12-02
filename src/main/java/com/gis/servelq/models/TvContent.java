package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tv_content")
@Data
public class TvContent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String branchId;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String url;  // Video file URL or IPTV URL

    private String type; // "URL" or "VIDEO"

    private Boolean active;

    private String size; // For videos
}
