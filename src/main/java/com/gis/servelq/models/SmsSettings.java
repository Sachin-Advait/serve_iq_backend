package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Non-secret SMS settings the admin changes from the admin panel; one row,
 * id 1. A blank sender, priority or appId uses the SMSOMAN_* environment
 * value. The SMSOMAN username, password and URL are never stored here.
 */
@Entity
@Table(name = "sms_settings")
@Data
public class SmsSettings {
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id = SINGLETON_ID;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 11)
    private String sender;

    private Integer priority;

    @Column(name = "app_id", length = 50)
    private String appId;

    /** "en" or "ar" for every SMS; null uses the visitor's kiosk language. */
    @Column(length = 2)
    private String language;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
