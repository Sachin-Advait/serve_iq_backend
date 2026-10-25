package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * SMS text the admin edits, in English and Arabic. The backend picks a
 * template by its code (see SmsTemplateCode) and fills in placeholders such
 * as {token} when it sends.
 */
@Entity
@Table(name = "sms_templates")
@Data
public class SmsTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, updatable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "body_en", nullable = false, columnDefinition = "TEXT")
    private String bodyEn;

    @Column(name = "body_ar", nullable = false, columnDefinition = "TEXT")
    private String bodyAr;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
