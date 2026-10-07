package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "app_config_templates")
@Data
public class AppConfigTemplate {
    @Id
    private String id;

    @Column(nullable = false)
    private String appType;

    private String templateName;
    private String description;
    private boolean isActive;
    private boolean isDefault;

    // ==================== COMMON FIELDS ====================
    private String backgroundImage;
    private String appLogo;
    private String backgroundImageUrl;
    private String appLogoUrl;
    private String appLanguage;
    private Boolean defaultEnabled;
    private String primaryColor;
    private String secondaryColor;

    // ==================== TV DISPLAY FIELDS ====================
    private Boolean tickerEnabled;
    private Integer tickerSpeed;
    private Boolean videosEnabled;
    private Integer flickerTime;
    private String foregroundFontColor;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_config_template_component_order", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "component")
    @OrderColumn(name = "position")
    private List<String> componentOrder;

    // ==================== FEEDBACK FIELDS ====================
    private Boolean imageCarouselEnabled;
    private Boolean feedbackVideoEnabled;
    private Integer feedbackIdleTimeout;

    // ==================== KIOSK PRINT FIELDS ====================

    // General
    private Boolean kioskPrintEnabled = true;

    // Header
    private String kioskHeaderTitleEn;
    private String kioskHeaderTitleAr;
    private String kioskHeaderSubtitleEn;
    private String kioskHeaderSubtitleAr;

    // Token
    private String kioskTokenTitleEn;
    private String kioskTokenTitleAr;

    // Service
    private String kioskServiceLabelEn;
    private String kioskServiceLabelAr;

    // Date & Time
    private String kioskDateLabelEn;
    private String kioskDateLabelAr;
    private String kioskTimeLabelEn;
    private String kioskTimeLabelAr;

    // Waiting message
    private String kioskWaitingMessageEn;
    private String kioskWaitingMessageAr;

    // Footer
    private String kioskThankYouMessageEn;
    private String kioskThankYouMessageAr;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}