package com.gis.servelq.dto;

import lombok.Data;
import java.util.List;

@Data
public class TemplateRequestDTO {
    private String templateName;
    private String description;

    // Common fields
    private String backgroundImage;
    private String appLogo;
    private String backgroundImageUrl;
    private String appLogoUrl;
    private String appLanguage;
    private Boolean defaultEnabled;
    private String primaryColor;
    private String secondaryColor;

    // TV Display fields
    private Boolean tickerEnabled;
    private Integer tickerSpeed;
    private Boolean videosEnabled;
    private Integer flickerTime;
    private List<String> componentOrder;

    // Feedback fields
    private Boolean imageCarouselEnabled;
    private Boolean feedbackVideoEnabled;

    // ==================== KIOSK PRINT FIELDS ====================

    // General
    private Boolean kioskPrintEnabled;
    private String kioskPrintLanguage;

    // Header
    private String kioskHeaderTitle;
    private String kioskHeaderSubtitle;

    // Token
    private String kioskTokenTitle;

    // Service
    private String kioskServiceLabel;

    // Date & Time
    private String kioskDateLabel;
    private String kioskTimeLabel;

    // Waiting message
    private String kioskWaitingMessage;

    // Footer
    private String kioskThankYouMessage;
}